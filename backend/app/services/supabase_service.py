import logging
import os
from typing import Any, Dict, List, Optional
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
            with httpx.Client(timeout=self.timeout) as client:
                res = client.post(
                    f"{self.url}/rest/v1/banners",
                    json=banner,
                    headers=self._headers(),
                )
                if res.is_success:
                    return True
                logger.warning(f"[Supabase] insert_banner failed: {res.status_code} - {res.text}")
        except Exception as e:
            logger.warning(f"[Supabase] insert_banner exception: {e}")
        return False

    def update_banner(self, banner_id: str, updates: Dict[str, Any]) -> bool:
        if not self.is_configured():
            return False
        try:
            with httpx.Client(timeout=self.timeout) as client:
                res = client.patch(
                    f"{self.url}/rest/v1/banners?id=eq.{banner_id}",
                    json=updates,
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


# Global singleton instance
supabase_service = SupabaseService()
