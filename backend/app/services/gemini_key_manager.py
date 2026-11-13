import datetime
import json
import logging
import os
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..config import PROJECT_ROOT, settings

logger = logging.getLogger(__name__)

DEFAULT_KEYS_PATH = PROJECT_ROOT / "dataset" / "users" / "gemini_keys.json"
TMP_KEYS_PATH = Path("/tmp/gemini_keys.json")


def get_egypt_today_date() -> str:
    """Returns today's date formatted as YYYY-MM-DD according to Egypt timezone (UTC+2 / UTC+3)."""
    try:
        import zoneinfo
        tz = zoneinfo.ZoneInfo("Africa/Cairo")
        return datetime.datetime.now(tz).strftime("%Y-%m-%d")
    except Exception:
        tz = datetime.timezone(datetime.timedelta(hours=2))
        return datetime.datetime.now(tz).strftime("%Y-%m-%d")


def mask_key(raw_key: Optional[str]) -> str:
    """Masks an API key for safe UI display (e.g. AIzaSy••••••••••••3xZ)."""
    if not raw_key:
        return "لم يتم التعيين"
    k = str(raw_key).strip()
    if len(k) <= 8:
        return "••••••••"
    if len(k) <= 14:
        return f"{k[:3]}••••{k[-3:]}"
    return f"{k[:6]}••••••••••••{k[-4:]}"


class GeminiKeyManager:
    """
    Manages a pool of Google Gemini API keys with:
    - Real-time token and request usage tracking per key
    - Daily quota calculations and automatic midnight reset
    - Automatic failover & smart rotation when 429 (Rate Limit / Quota Exceeded) occurs
    - Health checking, latency measurement, and admin controls
    """

    def __init__(self):
        self._keys: List[Dict[str, Any]] = []
        self._load()

    def _load(self):
        """Loads keys from persistent JSON or migrates from settings/env."""
        loaded = False
        path_to_try = None

        if DEFAULT_KEYS_PATH.exists():
            path_to_try = DEFAULT_KEYS_PATH
        elif TMP_KEYS_PATH.exists():
            path_to_try = TMP_KEYS_PATH

        if path_to_try:
            try:
                with open(path_to_try, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if isinstance(data, list):
                        self._keys = data
                        loaded = True
            except Exception as e:
                logger.warning(f"Failed to load gemini keys from {path_to_try}: {e}")

        # If not loaded or empty, auto-seed from settings / environment
        if not loaded or not self._keys:
            env_key = settings.gemini_api_key or os.environ.get("GEMINI_API_KEY")
            today = get_egypt_today_date()
            if env_key and len(env_key.strip()) >= 10:
                self._keys = [
                    {
                        "id": "key_primary",
                        "key": env_key.strip(),
                        "label": "المفتاح الأساسي 1",
                        "is_active": True,
                        "is_primary": True,
                        "status": "healthy",
                        "status_message": "جاهز ويعمل بشكل سليم ✓",
                        "daily_limit_requests": 1500,
                        "daily_limit_tokens": 1000000,
                        "requests_today": 0,
                        "tokens_today": 0,
                        "prompt_tokens_today": 0,
                        "completion_tokens_today": 0,
                        "total_requests": 0,
                        "total_tokens": 0,
                        "failed_requests": 0,
                        "consecutive_failures": 0,
                        "last_used_at": None,
                        "last_tested_at": None,
                        "last_error": None,
                        "cooldown_until": None,
                        "daily_date": today,
                        "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    }
                ]
                # Also check GEMINI_API_KEYS (comma separated)
                alt_keys = os.environ.get("GEMINI_API_KEYS", "")
                if alt_keys:
                    for idx, ak in enumerate(alt_keys.split(",")):
                        ak_clean = ak.strip()
                        if ak_clean and ak_clean != env_key.strip() and len(ak_clean) >= 10:
                            self._keys.append({
                                "id": f"key_env_{idx+2}",
                                "key": ak_clean,
                                "label": f"مفتاح احتياطي {idx+2}",
                                "is_active": True,
                                "is_primary": False,
                                "status": "healthy",
                                "status_message": "جاهز للاستخدام الاحتياطي ✓",
                                "daily_limit_requests": 1500,
                                "daily_limit_tokens": 1000000,
                                "requests_today": 0,
                                "tokens_today": 0,
                                "prompt_tokens_today": 0,
                                "completion_tokens_today": 0,
                                "total_requests": 0,
                                "total_tokens": 0,
                                "failed_requests": 0,
                                "consecutive_failures": 0,
                                "last_used_at": None,
                                "last_tested_at": None,
                                "last_error": None,
                                "cooldown_until": None,
                                "daily_date": today,
                                "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                            })
                self._save()

        # Always check daily reset on load
        self._check_daily_reset_and_cooldowns()

    def _save(self):
        """Persists the key list to disk."""
        try:
            TMP_KEYS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(TMP_KEYS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._keys, f, ensure_ascii=False, indent=2)

            if DEFAULT_KEYS_PATH.parent.exists():
                with open(DEFAULT_KEYS_PATH, "w", encoding="utf-8") as f:
                    json.dump(self._keys, f, ensure_ascii=False, indent=2)
        except Exception as e:
            logger.warning(f"Could not persist gemini_keys: {e}")

    def _check_daily_reset_and_cooldowns(self) -> bool:
        """Resets today's stats if date changed and clears expired cooldowns."""
        today = get_egypt_today_date()
        now_ts = time.time()
        modified = False

        for k in self._keys:
            # 1. Midnight date reset
            if k.get("daily_date") != today:
                k["requests_today"] = 0
                k["tokens_today"] = 0
                k["prompt_tokens_today"] = 0
                k["completion_tokens_today"] = 0
                k["daily_date"] = today
                if k.get("status") in ("exhausted", "rate_limited"):
                    k["status"] = "healthy"
                    k["status_message"] = "تم تصفير الحصة اليومية وبدء يوم جديد ✓"
                k["cooldown_until"] = None
                modified = True

            # 2. Cooldown recovery
            elif k.get("status") == "rate_limited" and k.get("cooldown_until"):
                if now_ts >= k["cooldown_until"]:
                    k["status"] = "healthy"
                    k["status_message"] = "انتهت فترة التبريد المؤقت وجاهز للعمل ✓"
                    k["cooldown_until"] = None
                    modified = True

        if modified:
            self._save()
        return modified

    def has_configured_keys(self) -> bool:
        """Returns True if there is at least one active, non-empty key."""
        return any(k.get("is_active", True) and bool(k.get("key")) for k in self._keys)

    def get_usable_keys(self) -> List[Dict[str, Any]]:
        """
        Returns keys available for AI inference, sorted in priority order:
        1. Primary healthy key
        2. Other healthy active keys (prioritizing lowest usage today)
        3. Rate-limited keys (if cooldown is expiring, as last resort)
        Disabled or permanently invalid keys are excluded.
        """
        self._check_daily_reset_and_cooldowns()

        active = [k for k in self._keys if k.get("is_active", True) and k.get("status") != "disabled" and k.get("status") != "invalid"]
        if not active:
            return []

        healthy = [k for k in active if k.get("status") == "healthy"]
        rate_limited = [k for k in active if k.get("status") == "rate_limited"]

        # Sort healthy: primary first, then by least requests today
        healthy.sort(key=lambda x: (not x.get("is_primary", False), x.get("requests_today", 0)))

        # Rate limited: sort by earliest cooldown expiration
        rate_limited.sort(key=lambda x: x.get("cooldown_until", 0) or 0)

        # Return healthy keys first; if all are rate-limited, return rate_limited as fallback
        return healthy if healthy else rate_limited

    def get_primary_key(self) -> Optional[str]:
        """Returns the raw key of the current primary active key, if any."""
        for k in self._keys:
            if k.get("is_primary") and k.get("is_active", True) and k.get("key"):
                return k["key"]
        for k in self._keys:
            if k.get("is_active", True) and k.get("key"):
                return k["key"]
        return None

    def record_success(self, key_id: str, prompt_tokens: int = 0, completion_tokens: int = 0):
        """Records a successful Gemini API request and token consumption."""
        self._check_daily_reset_and_cooldowns()
        tot_tokens = prompt_tokens + completion_tokens

        for k in self._keys:
            if k["id"] == key_id:
                k["requests_today"] = k.get("requests_today", 0) + 1
                k["tokens_today"] = k.get("tokens_today", 0) + tot_tokens
                k["prompt_tokens_today"] = k.get("prompt_tokens_today", 0) + prompt_tokens
                k["completion_tokens_today"] = k.get("completion_tokens_today", 0) + completion_tokens

                k["total_requests"] = k.get("total_requests", 0) + 1
                k["total_tokens"] = k.get("total_tokens", 0) + tot_tokens

                k["consecutive_failures"] = 0
                k["last_error"] = None
                k["last_used_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()

                if k.get("status") in ("rate_limited", "exhausted"):
                    k["status"] = "healthy"
                    k["status_message"] = "يعمل بكفاءة طبيعية ✓"

                # Check if reached daily limit
                daily_limit = k.get("daily_limit_requests", 1500)
                if daily_limit and k["requests_today"] >= daily_limit:
                    k["status"] = "exhausted"
                    k["status_message"] = f"وصل للحد اليومي المحدد ({daily_limit} طلب) - سيتم تصفيره غداً"

                self._save()
                break

    def record_failure(self, key_id: str, status_code: int, error_message: str):
        """Records a failed API request and handles rate limiting / quota exhaustion."""
        self._check_daily_reset_and_cooldowns()
        err_clean = (error_message or "").strip()

        for k in self._keys:
            if k["id"] == key_id:
                k["failed_requests"] = k.get("failed_requests", 0) + 1
                k["consecutive_failures"] = k.get("consecutive_failures", 0) + 1
                k["last_error"] = err_clean[:250]

                if status_code == 429:
                    msg_lower = err_clean.lower()
                    # Distinguish between daily quota exhaustion vs 1-minute RPM limit
                    if "queries per day" in msg_lower or "daily" in msg_lower or "check quota" in msg_lower:
                        k["status"] = "exhausted"
                        k["status_message"] = "وصل للحد الأقصى اليومي (429 Daily Quota Exceeded) - تم التبديل للمفتاح التالي"
                        k["cooldown_until"] = None
                    else:
                        k["status"] = "rate_limited"
                        k["status_message"] = "تجاوز حد الدقيقة (429 Rate Limit) - تبريد مؤقت والتبديل للمفتاح التالي"
                        k["cooldown_until"] = time.time() + 60.0
                elif status_code in (400, 403) and (
                    "api_key_invalid" in err_clean.lower()
                    or "api key not valid" in err_clean.lower()
                    or "permission_denied" in err_clean.lower()
                ):
                    k["status"] = "invalid"
                    k["status_message"] = "مفتاح غير صالح أو تم حذفه من Google (API Key Invalid)"
                else:
                    k["status_message"] = f"خطأ مؤقت (HTTP {status_code})"

                self._save()
                break

    def add_key(self, raw_key: str, label: Optional[str] = None, daily_limit: int = 1500) -> Dict[str, Any]:
        """Adds a new key to the pool."""
        clean_key = raw_key.strip()
        if not clean_key or len(clean_key) < 10:
            raise ValueError("مفتاح API غير صالح، يجب أن يحتوي على 10 خانات على الأقل.")

        # Check for duplicates
        for existing in self._keys:
            if existing["key"] == clean_key:
                raise ValueError("هذا المفتاح مضاف بالفعل في مجمع المفاتيح.")

        key_id = f"key_{uuid.uuid4().hex[:8]}"
        is_first = len(self._keys) == 0
        today = get_egypt_today_date()

        key_label = label.strip() if label and label.strip() else f"مفتاح #{len(self._keys) + 1}"

        new_record = {
            "id": key_id,
            "key": clean_key,
            "label": key_label,
            "is_active": True,
            "is_primary": is_first,
            "status": "healthy",
            "status_message": "تمت إضافته بنجاح وهو جاهز للاستخدام ✓",
            "daily_limit_requests": max(1, daily_limit),
            "daily_limit_tokens": 1000000,
            "requests_today": 0,
            "tokens_today": 0,
            "prompt_tokens_today": 0,
            "completion_tokens_today": 0,
            "total_requests": 0,
            "total_tokens": 0,
            "failed_requests": 0,
            "consecutive_failures": 0,
            "last_used_at": None,
            "last_tested_at": None,
            "last_error": None,
            "cooldown_until": None,
            "daily_date": today,
            "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        }

        self._keys.append(new_record)
        self._save()
        self._sync_env_and_settings()

        return self._format_key_for_client(new_record)

    def remove_key(self, key_id: str) -> bool:
        """Removes a key by ID."""
        target = None
        for k in self._keys:
            if k["id"] == key_id:
                target = k
                break
        if not target:
            return False

        was_primary = target.get("is_primary", False)
        self._keys = [k for k in self._keys if k["id"] != key_id]

        # If we removed the primary key, nominate a new one
        if was_primary and self._keys:
            self._keys[0]["is_primary"] = True

        self._save()
        self._sync_env_and_settings()
        return True

    def set_primary(self, key_id: str) -> bool:
        """Sets a key as the primary key."""
        found = False
        for k in self._keys:
            if k["id"] == key_id:
                k["is_primary"] = True
                found = True
            else:
                k["is_primary"] = False

        if found:
            self._save()
            self._sync_env_and_settings()
        return found

    def toggle_key(self, key_id: str, is_active: Optional[bool] = None) -> Optional[Dict[str, Any]]:
        """Enables or disables a key."""
        for k in self._keys:
            if k["id"] == key_id:
                if is_active is None:
                    k["is_active"] = not k.get("is_active", True)
                else:
                    k["is_active"] = bool(is_active)
                if not k["is_active"]:
                    k["status"] = "disabled"
                    k["status_message"] = "معطل يدوياً بواسطة الأدمن"
                else:
                    if k.get("status") == "disabled":
                        k["status"] = "healthy"
                        k["status_message"] = "تم التفعيل وجاهز للاستخدام ✓"
                self._save()
                return self._format_key_for_client(k)
        return None

    def reset_key_stats(self, key_id: str) -> Optional[Dict[str, Any]]:
        """Resets today's stats and error state for a key."""
        for k in self._keys:
            if k["id"] == key_id:
                k["requests_today"] = 0
                k["tokens_today"] = 0
                k["prompt_tokens_today"] = 0
                k["completion_tokens_today"] = 0
                k["consecutive_failures"] = 0
                k["last_error"] = None
                k["cooldown_until"] = None
                if k.get("status") in ("rate_limited", "exhausted"):
                    k["status"] = "healthy"
                    k["status_message"] = "تم تصفير العدادات وإعادة التفعيل بنجاح ✓"
                self._save()
                return self._format_key_for_client(k)
        return None

    async def test_key(self, key_id: str) -> Dict[str, Any]:
        """Tests a specific key live against the Google Gemini API."""
        import httpx

        target = None
        for k in self._keys:
            if k["id"] == key_id:
                target = k
                break
        if not target:
            return {"success": False, "error": "المفتاح غير موجود"}

        raw_key = target["key"]
        start_t = time.perf_counter()

        # Ping using a lightweight 1-token prompt to verify generation quota
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key={raw_key}"
        payload = {
            "contents": [{"parts": [{"text": "ping"}]}],
            "generationConfig": {"maxOutputTokens": 1},
        }

        try:
            async with httpx.AsyncClient(timeout=12.0) as client:
                resp = await client.post(url, json=payload)
                latency_ms = int((time.perf_counter() - start_t) * 1000)

                target["last_tested_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()

                if resp.status_code == 200:
                    target["status"] = "healthy"
                    target["status_message"] = f"يعمل بكفاءة ممتازة (سرعة استجابة {latency_ms}ms) ✓"
                    target["last_error"] = None
                    target["cooldown_until"] = None
                    self._save()
                    return {
                        "success": True,
                        "status": "healthy",
                        "latency_ms": latency_ms,
                        "message": f"المفتاح يعمل بكفاءة ممتازة وسرعة الاستجابة {latency_ms} مللي ثانية ✓",
                        "key": self._format_key_for_client(target),
                    }
                elif resp.status_code == 429:
                    target["status"] = "exhausted"
                    target["status_message"] = "وصل للحد الأقصى للطلبات (429 Rate Limit)"
                    target["last_error"] = resp.text[:200]
                    self._save()
                    return {
                        "success": False,
                        "status": "exhausted",
                        "latency_ms": latency_ms,
                        "error": "وصل هذا المفتاح للحد الأقصى للطلبات أو الحصة المسموحة من Google (429 Too Many Requests)",
                        "key": self._format_key_for_client(target),
                    }
                elif resp.status_code in (400, 403):
                    target["status"] = "invalid"
                    target["status_message"] = "مفتاح غير صالح أو محظور (API Key Invalid)"
                    target["last_error"] = resp.text[:200]
                    self._save()
                    return {
                        "success": False,
                        "status": "invalid",
                        "latency_ms": latency_ms,
                        "error": "المفتاح غير صالح أو تم إلغاؤه من Google (Invalid API Key)",
                        "key": self._format_key_for_client(target),
                    }
                else:
                    return {
                        "success": False,
                        "status": "error",
                        "latency_ms": latency_ms,
                        "error": f"استجابة غير متوقعة من Google (HTTP {resp.status_code}): {resp.text[:100]}",
                        "key": self._format_key_for_client(target),
                    }
        except Exception as exc:
            latency_ms = int((time.perf_counter() - start_t) * 1000)
            return {
                "success": False,
                "status": "network_error",
                "latency_ms": latency_ms,
                "error": f"تعذر الاتصال بـ Google API: {str(exc)}",
            }

    def _sync_env_and_settings(self):
        """Synchronizes the active primary key to settings.gemini_api_key and .env."""
        primary_key = self.get_primary_key()
        if primary_key:
            settings.gemini_api_key = primary_key
            os.environ["GEMINI_API_KEY"] = primary_key

            # Update .env on disk if writable
            env_file = PROJECT_ROOT / ".env"
            try:
                env_lines = []
                if env_file.exists():
                    with open(env_file, "r", encoding="utf-8") as f:
                        env_lines = f.readlines()
                found = False
                new_lines = []
                for line in env_lines:
                    if line.strip().startswith("GEMINI_API_KEY="):
                        new_lines.append(f"GEMINI_API_KEY={primary_key}\n")
                        found = True
                    else:
                        new_lines.append(line)
                if not found:
                    new_lines.append(f"GEMINI_API_KEY={primary_key}\n")
                with open(env_file, "w", encoding="utf-8") as f:
                    f.writelines(new_lines)
            except Exception as e:
                logger.warning(f"Could not update .env: {e}")

    def _format_key_for_client(self, k: Dict[str, Any]) -> Dict[str, Any]:
        """Formats a key record for public admin response, never exposing the raw key."""
        daily_limit = k.get("daily_limit_requests", 1500)
        requests_today = k.get("requests_today", 0)
        remaining = max(0, daily_limit - requests_today) if daily_limit else 0
        pct = min(100.0, round((requests_today / max(1, daily_limit)) * 100, 1))

        return {
            "id": k["id"],
            "masked_key": mask_key(k.get("key")),
            "label": k.get("label", "مفتاح Gemini"),
            "is_active": k.get("is_active", True),
            "is_primary": k.get("is_primary", False),
            "status": k.get("status", "healthy"),
            "status_message": k.get("status_message", "جاهز للعمل"),
            "daily_limit_requests": daily_limit,
            "requests_today": requests_today,
            "remaining_requests_today": remaining,
            "usage_percentage": pct,
            "tokens_today": k.get("tokens_today", 0),
            "prompt_tokens_today": k.get("prompt_tokens_today", 0),
            "completion_tokens_today": k.get("completion_tokens_today", 0),
            "total_requests": k.get("total_requests", 0),
            "total_tokens": k.get("total_tokens", 0),
            "failed_requests": k.get("failed_requests", 0),
            "last_used_at": k.get("last_used_at"),
            "last_tested_at": k.get("last_tested_at"),
            "last_error": k.get("last_error"),
            "created_at": k.get("created_at"),
        }

    def get_pool_summary(self) -> Dict[str, Any]:
        """Returns comprehensive status of all keys and global pool capacity."""
        self._check_daily_reset_and_cooldowns()

        formatted_keys = [self._format_key_for_client(k) for k in self._keys]
        total_keys = len(formatted_keys)
        active_keys = sum(1 for k in formatted_keys if k["is_active"])
        healthy_keys = sum(1 for k in formatted_keys if k["status"] == "healthy" and k["is_active"])
        exhausted_keys = sum(1 for k in formatted_keys if k["status"] in ("exhausted", "rate_limited"))

        total_requests_today = sum(k["requests_today"] for k in formatted_keys)
        total_tokens_today = sum(k["tokens_today"] for k in formatted_keys)
        total_capacity = sum(k["daily_limit_requests"] for k in formatted_keys if k["is_active"])
        remaining_capacity = sum(k["remaining_requests_today"] for k in formatted_keys if k["is_active"])
        pool_pct = min(100.0, round((total_requests_today / max(1, total_capacity)) * 100, 1)) if total_capacity else 0.0

        primary = next((k for k in formatted_keys if k["is_primary"]), formatted_keys[0] if formatted_keys else None)

        return {
            "keys": formatted_keys,
            "total_keys": total_keys,
            "active_keys": active_keys,
            "healthy_keys": healthy_keys,
            "exhausted_keys": exhausted_keys,
            "primary_key": primary,
            "total_requests_today": total_requests_today,
            "total_tokens_today": total_tokens_today,
            "total_capacity_today": total_capacity,
            "remaining_capacity_today": remaining_capacity,
            "pool_usage_percentage": pool_pct,
            "auto_failover_enabled": True,
        }


# Global singleton instance
gemini_key_manager = GeminiKeyManager()
