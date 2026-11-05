import datetime
import json
import logging
import os
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from .supabase_service import supabase_service

logger = logging.getLogger(__name__)

DAILY_SCAN_LIMIT = 5
WHATSAPP_NUMBER = "+201064290486"
WHATSAPP_LINK = (
    "https://wa.me/201064290486?text="
    "%D9%85%D8%B1%D8%AD%D8%A8%D8%A7%D9%8B%D8%8C%20%D8%A3%D8%B1%D9%8A%D8%AF%20%D8%A7%D9%84%D8%A7%D8%B4%D8%AA%D8%B1%D8%A7%D9%83%20%D9%81%D9%8A%20%D8%A7%D9%84%D9%86%D8%B3%D8%AE%D8%A9%20%D8%A7%D9%84%D9%83%D8%A7%D9%85%D9%84%D8%A9%20%D8%BA%D9%8A%D8%B1%20%D8%A7%D9%84%D9%85%D8%AD%D8%AF%D9%88%D8%AF%D8%A9%20%D9%84%D8%AA%D8%B7%D8%A8%D9%8A%D9%82%20%D8%AC%D8%B1%D8%B9%D8%A9"
)

DEFAULT_SCANS_PATH = PROJECT_ROOT / "dataset" / "users" / "daily_scans.json"
TMP_SCANS_PATH = Path("/tmp/daily_scans.json")


def get_egypt_today_date() -> str:
    """Returns today's date formatted as YYYY-MM-DD according to Egypt timezone (UTC+2 / UTC+3)."""
    try:
        import zoneinfo
        tz = zoneinfo.ZoneInfo("Africa/Cairo")
        return datetime.datetime.now(tz).strftime("%Y-%m-%d")
    except Exception:
        # Fallback UTC+2
        tz = datetime.timezone(datetime.timedelta(hours=2))
        return datetime.datetime.now(tz).strftime("%Y-%m-%d")


class QuotaService:
    """
    Manages daily prescription scan quotas with dual-layer anti-abuse tracking:
    1. By registered User ID
    2. By persistent Device UUID (preventing logout -> create new account bypass)
    """

    def __init__(self):
        self._local_scans: List[Dict[str, Any]] = []
        self._load_local_scans()

    def _load_local_scans(self):
        path = None
        if TMP_SCANS_PATH.exists():
            path = TMP_SCANS_PATH
        elif DEFAULT_SCANS_PATH.exists():
            path = DEFAULT_SCANS_PATH

        if path:
            try:
                with open(path, "r", encoding="utf-8") as f:
                    self._local_scans = json.load(f)
            except Exception as e:
                logger.warning(f"Failed to load daily scans from {path}: {e}")
                self._local_scans = []
        else:
            self._local_scans = []

    def _save_local_scans(self):
        try:
            # Keep only the last 30 days to avoid unlimited growth
            now_str = get_egypt_today_date()
            # Write to tmp
            with open(TMP_SCANS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._local_scans[-5000:], f, ensure_ascii=False, indent=2)

            # Also persist to repo dataset if writable
            if DEFAULT_SCANS_PATH.parent.exists():
                with open(DEFAULT_SCANS_PATH, "w", encoding="utf-8") as f:
                    json.dump(self._local_scans[-5000:], f, ensure_ascii=False, indent=2)
        except Exception as e:
            logger.warning(f"Failed to persist daily scans locally: {e}")

    def get_user_daily_limit(self, user_id: Optional[str]) -> int:
        """Returns the user-specific daily scan limit if set, otherwise the default (5)."""
        if not user_id:
            return DAILY_SCAN_LIMIT
        try:
            from .user_service import user_service
            user = user_service.get_user_by_id(user_id)
            if user and user.get("daily_limit") is not None:
                return max(1, int(user["daily_limit"]))
        except Exception:
            pass
        return DAILY_SCAN_LIMIT

    def check_quota(
        self,
        user_id: Optional[str],
        device_id: str,
    ) -> Dict[str, Any]:
        """
        Validates whether the user and device are permitted to perform another prescription scan.
        If either user_id OR device_id has reached the allowed daily scans, quota is exceeded.
        """
        today = get_egypt_today_date()
        clean_device = (device_id or "").strip()

        user_count = 0
        device_count = 0

        # 1. Check Supabase Cloud if configured
        if supabase_service.is_configured():
            u_cnt, d_cnt = supabase_service.count_daily_scans(
                user_id=user_id,
                device_id=clean_device,
                scan_date=today,
            )
            user_count = max(user_count, u_cnt)
            device_count = max(device_count, d_cnt)

        # 2. Check local memory/JSON storage
        self._load_local_scans()
        local_u_cnt = 0
        local_d_cnt = 0
        for s in self._local_scans:
            if s.get("scan_date") == today:
                if clean_device and s.get("device_id") == clean_device:
                    local_d_cnt += 1
                if user_id and s.get("user_id") == user_id:
                    local_u_cnt += 1

        user_count = max(user_count, local_u_cnt)
        device_count = max(device_count, local_d_cnt)

        # Look up custom limit for this user (default 5)
        max_limit = self.get_user_daily_limit(user_id)

        # The effective used scans is the higher of user or device (anti-abuse)
        effective_used = max(user_count, device_count)
        is_exceeded = effective_used >= max_limit
        scans_remaining = max(0, max_limit - effective_used)

        return {
            "allowed": not is_exceeded,
            "scans_used": effective_used,
            "max_scans": max_limit,
            "scans_remaining": scans_remaining,
            "scan_date": today,
            "user_id": user_id,
            "device_id": clean_device,
            "whatsapp_number": WHATSAPP_NUMBER,
            "whatsapp_link": WHATSAPP_LINK,
            "message": (
                f"لقد استنفدت الحد اليومي المسموح به ({max_limit} فحوصات في اليوم). "
                "إذا كنت ترغب في فحص عدد غير محدود من الروشتات وتفعيل النسخة الكاملة، "
                f"يرجى التواصل معنا عبر واتساب على الرقم {WHATSAPP_NUMBER}."
            )
            if is_exceeded
            else "الكوتا اليومية متاحة.",
        }

    def get_user_scan_stats(self, user_id: str) -> Dict[str, int]:
        """Returns today and current month scan counts for a specific user."""
        self._load_local_scans()
        today = get_egypt_today_date()
        month_prefix = today[:7]

        today_count = 0
        month_count = 0
        for s in self._local_scans:
            if s.get("user_id") == user_id:
                s_date = str(s.get("scan_date") or "")
                if s_date == today:
                    today_count += 1
                if s_date.startswith(month_prefix):
                    month_count += 1

        return {
            "today_scans": today_count,
            "month_scans": month_count,
        }

    def get_global_scan_stats(self) -> Dict[str, int]:
        """Returns overall scans used today and this month across all users."""
        self._load_local_scans()
        today = get_egypt_today_date()
        month_prefix = today[:7]

        today_count = 0
        month_count = 0
        for s in self._local_scans:
            s_date = str(s.get("scan_date") or "")
            if s_date == today:
                today_count += 1
            if s_date.startswith(month_prefix):
                month_count += 1

        return {
            "today_scans": today_count,
            "month_scans": month_count,
        }

    def record_scan(self, user_id: Optional[str], device_id: str) -> None:
        """
        Records a completed prescription scan against both user and device.
        """
        today = get_egypt_today_date()
        clean_device = (device_id or "").strip()
        record_id = "scan_" + uuid.uuid4().hex[:16]
        now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()

        scan_record = {
            "id": record_id,
            "user_id": user_id,
            "device_id": clean_device,
            "scan_date": today,
            "created_at": now_iso,
        }

        # 1. Record in memory and save locally
        self._local_scans.append(scan_record)
        self._save_local_scans()

        # 2. Record in Supabase Cloud DB
        if supabase_service.is_configured():
            supabase_service.insert_daily_scan(scan_record)

        logger.info(
            f"[QuotaService] Recorded scan for user={user_id}, device={clean_device}, date={today}"
        )


# Global singleton
quota_service = QuotaService()
