import json
import logging
import os
from typing import Any, Dict, List, Optional, Tuple
import httpx

logger = logging.getLogger(__name__)


class SupabaseService:
    """
    Lightweight, production-grade Supabase PostgREST client using standard HTTPX.
    Works seamlessly on Vercel Serverless with zero connection pooling issues.
    """

    def __init__(self):
        self.url = os.environ.get("SUPABASE_URL", "").rstrip("/")
        self.key = (
            os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
            or os.environ.get("SUPABASE_KEY")
            or os.environ.get("SUPABASE_ANON_KEY")
            or ""
        )
        self.timeout = 8.0

    def is_configured(self) -> bool:
        """Returns True if Supabase credentials are provided in environment."""
        return bool(self.url and self.key and self.url.startswith("http"))

    def _headers(self) -> Dict[str, str]:
        return {
            "apikey": self.key,
            "Authorization": f"Bearer {self.key}",
            "Content-Type": "application/json",
            "Prefer": "return=representation",
        }

    # --------------------------------------------------------------------------
    # Users Management
    # --------------------------------------------------------------------------
    def fetch_all_users(self) -> Optional[List[Dict[str, Any]]]:
        if not self.is_configured():
            return None
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.get(
                    f"{self.url}/rest/v1/users?select=*",
                    headers=self._headers(),
                )
                if res.is_success:
                    return res.json()
                logger.warning(f"[Supabase] fetch_all_users failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] fetch_all_users exception: {e}")
        return None

    def insert_user(self, user: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/users",
                    json=user,
                    headers=self._headers(),
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] insert_user failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] insert_user exception: {e}")
        return False

    def update_user(self, user_id: str, updates: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.patch(
                    f"{self.url}/rest/v1/users?id=eq.{user_id}",
                    json=updates,
                    headers=self._headers(),
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] update_user failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] update_user exception: {e}")
        return False

    def delete_user(self, user_id: str) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.delete(
                    f"{self.url}/rest/v1/users?id=eq.{user_id}",
                    headers=self._headers(),
                )
                return res.is_success
        except Exception as e:
            logger.warning(f"[Supabase] delete_user exception: {e}")
            return False

    # --------------------------------------------------------------------------
    # User Prescriptions Management
    # --------------------------------------------------------------------------
    def fetch_all_prescriptions(self) -> Optional[List[Dict[str, Any]]]:
        if not self.is_configured():
            return None
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.get(
                    f"{self.url}/rest/v1/user_prescriptions?select=*",
                    headers=self._headers(),
                )
                if res.is_success:
                    return res.json()
                logger.warning(f"[Supabase] fetch_all_prescriptions failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] fetch_all_prescriptions exception: {e}")
        return None

    def upsert_prescription(self, rx: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            headers = self._headers()
            headers["Prefer"] = "resolution=merge-duplicates,return=representation"
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/user_prescriptions",
                    json=rx,
                    headers=headers,
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] upsert_prescription failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] upsert_prescription exception: {e}")
        return False

    def delete_prescription(self, rx_id: str, user_id: str) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.delete(
                    f"{self.url}/rest/v1/user_prescriptions?id=eq.{rx_id}&user_id=eq.{user_id}",
                    headers=self._headers(),
                )
                return res.is_success
        except Exception as e:
            logger.warning(f"[Supabase] delete_prescription exception: {e}")
            return False

    # --------------------------------------------------------------------------
    # Promotional Banners Management
    # --------------------------------------------------------------------------
    _BANNER_COLUMNS = {
        "id",
        "title",
        "subtitle",
        "image_url",
        "target_type",
        "target_governorates",
        "action_type",
        "action_target",
        "is_active",
        "start_date",
        "end_date",
        "display_order",
        "created_at",
        "updated_at",
    }

    def _clean_banner(self, banner: Dict[str, Any]) -> Dict[str, Any]:
        return {k: v for k, v in banner.items() if k in self._BANNER_COLUMNS}

    def fetch_all_banners(self) -> Optional[List[Dict[str, Any]]]:
        if not self.is_configured():
            return None
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.get(
                    f"{self.url}/rest/v1/banners?select=*&order=display_order.asc",
                    headers=self._headers(),
                )
                if res.is_success:
                    return res.json()
                logger.warning(f"[Supabase] fetch_all_banners failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] fetch_all_banners exception: {e}")
        return None

    def insert_banner(self, banner: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            payload = self._clean_banner(banner)
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/banners",
                    json=payload,
                    headers=self._headers(),
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] insert_banner failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] insert_banner exception: {e}")
        return False

    def upsert_banner(self, banner: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            payload = self._clean_banner(banner)
            headers = self._headers()
            headers["Prefer"] = "resolution=merge-duplicates,return=representation"
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/banners",
                    json=payload,
                    headers=headers,
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] upsert_banner failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] upsert_banner exception: {e}")
        return False

    def update_banner(self, banner_id: str, updates: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            payload = self._clean_banner(updates)
            with httpx.Client(timeout=self.timeout) as client:
                res = client.patch(
                    f"{self.url}/rest/v1/banners?id=eq.{banner_id}",
                    json=payload,
                    headers=self._headers(),
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] update_banner failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] update_banner exception: {e}")
        return False

    def delete_banner(self, banner_id: str) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.delete(
                    f"{self.url}/rest/v1/banners?id=eq.{banner_id}",
                    headers=self._headers(),
                )
                return res.is_success
        except Exception as e:
            logger.warning(f"[Supabase] delete_banner exception: {e}")
            return False

    # --------------------------------------------------------------------------
    # Daily Scans Quota Tracking
    # --------------------------------------------------------------------------
    def count_daily_scans(self, user_id: Optional[str], device_id: str, scan_date: str) -> Tuple[int, int]:
        """
        Returns (user_scan_count, device_scan_count) for the given date.
        """
        if not self.is_configured():
            return 0, 0
        user_count = 0
        device_count = 0
        try:
            with httpx.Client(timeout=self.timeout) as client:
                # 1. Device count
                res_dev = client.get(
                    f"{self.url}/rest/v1/daily_scans?device_id=eq.{device_id}&scan_date=eq.{scan_date}&select=id",
                    headers=self._headers(),
                )
                if res_dev.is_success:
                    device_count = len(res_dev.json())

                # 2. User count (if user_id provided)
                if user_id:
                    res_user = client.get(
                        f"{self.url}/rest/v1/daily_scans?user_id=eq.{user_id}&scan_date=eq.{scan_date}&select=id",
                        headers=self._headers(),
                    )
                    if res_user.is_success:
                        user_count = len(res_user.json())
        except Exception as e:
            logger.warning(f"[Supabase] count_daily_scans exception: {e}")
        return user_count, device_count

    def insert_daily_scan(self, scan_record: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/daily_scans",
                    json=scan_record,
                    headers=self._headers(),
                )
                return res.is_success
        except Exception as e:
            logger.warning(f"[Supabase] insert_daily_scan exception: {e}")
            return False

    # --------------------------------------------------------------------------
    # Cloud Config & User Limits Persistence
    # --------------------------------------------------------------------------
    def fetch_user_limits_config(self) -> Dict[str, int]:
        """Fetches custom user daily limits from Supabase config row."""
        if not self.is_configured():
            return {}
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.get(
                    f"{self.url}/rest/v1/banners?id=eq.config_user_daily_limits&select=action_target",
                    headers=self._headers(),
                )
                if res.is_success:
                    rows = res.json()
                    if rows and rows[0].get("action_target"):
                        loaded = json.loads(rows[0]["action_target"])
                        if isinstance(loaded, dict):
                            return {str(k): int(v) for k, v in loaded.items()}
        except Exception as e:
            logger.warning(f"[Supabase] fetch_user_limits_config exception: {e}")
        return {}

    def save_user_limits_config(self, limits_map: Dict[str, int]) -> bool:
        """Upserts custom user daily limits to Supabase config row."""
        if not self.is_configured():
            return False
        try:
            payload = {
                "id": "config_user_daily_limits",
                "title": "Config User Daily Limits",
                "subtitle": "System configuration for user daily scan limits",
                "image_url": "https://gor3a.app/config",
                "action_type": "none",
                "action_target": json.dumps(limits_map),
                "is_active": False,
                "target_type": "all",
                "display_order": 9999,
            }
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/banners",
                    headers={**self._headers(), "Prefer": "resolution=merge-duplicates"},
                    json=payload,
                )
                return res.is_success
        except Exception as e:
            logger.warning(f"[Supabase] save_user_limits_config exception: {e}")
            return False


# Global singleton instance
supabase_service = SupabaseService()
