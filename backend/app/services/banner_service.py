import datetime
import json
import logging
import os
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..config import PROJECT_ROOT
from .supabase_service import supabase_service

logger = logging.getLogger(__name__)

EGYPTIAN_GOVERNORATES = [
    "القاهرة",
    "الجيزة",
    "الإسكندرية",
    "القليوبية",
    "الدقهلية",
    "الشرقية",
    "المنوفية",
    "الغربية",
    "البحيرة",
    "كفر الشيخ",
    "دمياط",
    "بورسعيد",
    "الإسماعيلية",
    "السويس",
    "شمال سيناء",
    "جنوب سيناء",
    "البحر الأحمر",
    "مطروح",
    "الوادي الجديد",
    "الفيوم",
    "بني سويف",
    "المنيا",
    "أسيوط",
    "سوهاج",
    "قنا",
    "الأقصر",
    "أسوان",
]

DEFAULT_BANNERS_PATH = PROJECT_ROOT / "dataset" / "banners" / "banners.json"
TMP_BANNERS_PATH = Path("/tmp/banners.json")
DELETED_IDS_PATH = Path("/tmp/deleted_banner_ids.json")


class BannerService:
    def __init__(self):
        self._banners: List[Dict[str, Any]] = []
        self._deleted_ids: set = set()
        self._load()

    def _get_deleted_ids(self) -> set:
        if DELETED_IDS_PATH.exists():
            try:
                with open(DELETED_IDS_PATH, "r", encoding="utf-8") as f:
                    return set(json.load(f))
            except Exception:
                pass
        return self._deleted_ids

    def _record_deleted_id(self, banner_id: str):
        self._deleted_ids.add(banner_id)
        try:
            DELETED_IDS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(DELETED_IDS_PATH, "w", encoding="utf-8") as f:
                json.dump(list(self._deleted_ids), f)
        except Exception:
            pass

    def _reload_if_needed(self):
        """Always ensures memory reflects the latest state and filters deleted banners."""
        if supabase_service.is_configured():
            cloud_banners = supabase_service.fetch_all_banners()
            if cloud_banners is not None:
                self._banners = cloud_banners
                return

        if TMP_BANNERS_PATH.exists():
            try:
                with open(TMP_BANNERS_PATH, "r", encoding="utf-8") as f:
                    self._banners = json.load(f)
            except Exception:
                pass
        deleted = self._get_deleted_ids()
        if deleted:
            self._banners = [b for b in self._banners if b.get("id") not in deleted]

    def _load(self):
        """Loads banners from Supabase Cloud DB or disk or initializes defaults."""
        if supabase_service.is_configured():
            cloud_banners = supabase_service.fetch_all_banners()
            if cloud_banners is not None:
                self._banners = cloud_banners
                logger.info(f"Loaded {len(self._banners)} banners from Supabase Cloud DB")
                self._save()
                return

        source_path: Optional[Path] = None
        if TMP_BANNERS_PATH.exists():
            source_path = TMP_BANNERS_PATH
        elif DEFAULT_BANNERS_PATH.exists():
            source_path = DEFAULT_BANNERS_PATH

        if source_path:
            try:
                with open(source_path, "r", encoding="utf-8") as f:
                    self._banners = json.load(f)
                deleted = self._get_deleted_ids()
                if deleted:
                    self._banners = [b for b in self._banners if b.get("id") not in deleted]
                logger.info(f"Loaded {len(self._banners)} banners from {source_path}")
                return
            except Exception as e:
                logger.warning(f"Failed to read banners from {source_path}: {e}")

        # Fallback default seed
        self._banners = [
            {
                "id": "banner-elezaby-promo",
                "title": "خصم 15% على جميع أدويتك ومستلزماتك",
                "subtitle": "اطلب روشتتك الآن عبر واتساب صيدليات العزبي باستخدام كود GOR3A15",
                "image_url": "https://images.unsplash.com/photo-1586015555751-63c2c62c3e1e?auto=format&fit=crop&w=800&q=80",
                "target_type": "all",
                "target_governorates": [],
                "action_type": "whatsapp",
                "action_target": "https://wa.me/2011119011?text=مرحباً، أود طلب الأدوية بكود خصم جرعة GOR3A15",
                "is_active": True,
                "start_date": "2026-10-01",
                "end_date": "2026-12-31",
                "display_order": 1,
                "created_at": datetime.datetime.utcnow().isoformat() + "Z",
                "updated_at": datetime.datetime.utcnow().isoformat() + "Z",
            }
        ]

    def _save(self) -> bool:
        """Persists banners to disk (local project root or serverless /tmp)."""
        saved = False
        # Try persisting to project root first
        try:
            DEFAULT_BANNERS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(DEFAULT_BANNERS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._banners, f, ensure_ascii=False, indent=2)
            saved = True
        except Exception as exc:
            logger.debug(f"Could not write to {DEFAULT_BANNERS_PATH} (serverless read-only): {exc}")

        # In serverless environments (e.g. Vercel), always ensure /tmp copy is up to date
        try:
            TMP_BANNERS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(TMP_BANNERS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._banners, f, ensure_ascii=False, indent=2)
            saved = True
        except Exception as exc:
            logger.warning(f"Could not write to {TMP_BANNERS_PATH}: {exc}")

        return saved

    def _is_expired(self, banner: Dict[str, Any], today_str: Optional[str] = None) -> bool:
        if not today_str:
            today_str = datetime.date.today().isoformat()
        end_date = banner.get("end_date")
        if end_date and end_date.strip():
            return today_str > end_date.strip()
        return False

    def _is_not_started(self, banner: Dict[str, Any], today_str: Optional[str] = None) -> bool:
        if not today_str:
            today_str = datetime.date.today().isoformat()
        start_date = banner.get("start_date")
        if start_date and start_date.strip():
            return today_str < start_date.strip()
        return False

    def get_stats(self) -> Dict[str, int]:
        self._reload_if_needed()
        today_str = datetime.date.today().isoformat()
        total = len(self._banners)
        active = 0
        expired = 0
        inactive = 0

        for b in self._banners:
            if not b.get("is_active", True):
                inactive += 1
            elif self._is_expired(b, today_str):
                expired += 1
            else:
                active += 1

        return {
            "total": total,
            "active": active,
            "expired": expired,
            "inactive": inactive,
        }

    def get_all_admin_banners(self) -> List[Dict[str, Any]]:
        """Returns all banners with computed status flags for admin panel."""
        self._reload_if_needed()
        today_str = datetime.date.today().isoformat()
        results = []
        for b in sorted(self._banners, key=lambda x: (x.get("display_order", 999), x.get("id", ""))):
            item = dict(b)
            is_expired = self._is_expired(b, today_str)
            is_pending = self._is_not_started(b, today_str)
            item["is_expired"] = is_expired
            item["is_pending"] = is_pending
            if not item.get("is_active", True):
                item["status_label"] = "معطّل"
                item["status_color"] = "gray"
            elif is_expired:
                item["status_label"] = "منتهي الصلاحية"
                item["status_color"] = "red"
            elif is_pending:
                item["status_label"] = "مجدول مستقبلاً"
                item["status_color"] = "blue"
            else:
                item["status_label"] = "نشط الآن"
                item["status_color"] = "green"
            results.append(item)
        return results

    def get_active_banners(self, governorate: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Returns active, non-expired banners suitable for the mobile app / website slider.
        Filters by target governorate if specified.
        """
        self._reload_if_needed()
        today_str = datetime.date.today().isoformat()
        active_list = []

        gov_normalized = governorate.strip() if governorate else ""

        for b in self._banners:
            # Must be active
            if not b.get("is_active", True):
                continue
            # Must not be expired or future-scheduled
            if self._is_expired(b, today_str) or self._is_not_started(b, today_str):
                continue

            target_type = b.get("target_type", "all")
            target_govs = b.get("target_governorates", []) or []

            # Check governorate match
            if target_type == "all":
                active_list.append(b)
            elif target_type == "specific":
                if not gov_normalized:
                    # If client didn't supply governorate, still show specific ads or fallback
                    active_list.append(b)
                else:
                    # Match if governorate name is in target_govs (or partial substring match)
                    if any(gov_normalized in g or g in gov_normalized for g in target_govs):
                        active_list.append(b)

        return sorted(active_list, key=lambda x: (x.get("display_order", 999), x.get("id", "")))

    def get_banner(self, banner_id: str) -> Optional[Dict[str, Any]]:
        for b in self._banners:
            if b.get("id") == banner_id:
                return dict(b)
        return None

    def create_banner(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Creates and stores a new banner."""
        self._reload_if_needed()
        banner_id = f"banner-{uuid.uuid4().hex[:8]}"
        now_iso = datetime.datetime.utcnow().isoformat() + "Z"
        today_str = datetime.date.today().isoformat()

        # Compute next display order if not given or <= 0
        display_order = data.get("display_order")
        if not display_order or int(display_order) <= 0:
            existing_orders = [b.get("display_order", 0) for b in self._banners]
            display_order = (max(existing_orders) + 1) if existing_orders else 1
        else:
            display_order = int(display_order)

        target_type = data.get("target_type", "all")
        if target_type not in ["all", "specific"]:
            target_type = "all"

        target_govs = data.get("target_governorates", [])
        if not isinstance(target_govs, list):
            target_govs = []

        new_banner = {
            "id": banner_id,
            "title": data.get("title", "").strip() or "إعلان جديد",
            "subtitle": data.get("subtitle", "").strip(),
            "image_url": data.get("image_url", "").strip(),
            "target_type": target_type,
            "target_governorates": target_govs,
            "action_type": data.get("action_type", "none"),
            "action_target": data.get("action_target", "").strip(),
            "is_active": bool(data.get("is_active", True)),
            "start_date": data.get("start_date", "").strip() or today_str,
            "end_date": data.get("end_date") if data.get("end_date") and str(data.get("end_date")).strip() else None,
            "display_order": display_order,
            "created_at": now_iso,
            "updated_at": now_iso,
        }

        self._banners.append(new_banner)
        self._save()
        if supabase_service.is_configured():
            supabase_service.insert_banner(new_banner)
        return new_banner

    def update_banner(self, banner_id: str, data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Updates an existing banner."""
        self._reload_if_needed()
        for idx, b in enumerate(self._banners):
            if b.get("id") == banner_id:
                now_iso = datetime.datetime.utcnow().isoformat() + "Z"
                updated = dict(b)

                if "title" in data:
                    updated["title"] = str(data["title"]).strip()
                if "subtitle" in data:
                    updated["subtitle"] = str(data["subtitle"]).strip()
                if "image_url" in data:
                    updated["image_url"] = str(data["image_url"]).strip()
                if "target_type" in data:
                    t_type = str(data["target_type"]).strip()
                    updated["target_type"] = t_type if t_type in ["all", "specific"] else "all"
                if "target_governorates" in data:
                    govs = data["target_governorates"]
                    updated["target_governorates"] = govs if isinstance(govs, list) else []
                if "action_type" in data:
                    updated["action_type"] = str(data["action_type"]).strip()
                if "action_target" in data:
                    updated["action_target"] = str(data["action_target"]).strip()
                if "is_active" in data:
                    updated["is_active"] = bool(data["is_active"])
                if "start_date" in data:
                    updated["start_date"] = str(data["start_date"]).strip()
                if "end_date" in data:
                    ed = data["end_date"]
                    updated["end_date"] = str(ed).strip() if ed and str(ed).strip() else None
                if "display_order" in data and data["display_order"] is not None:
                    try:
                        updated["display_order"] = int(data["display_order"])
                    except (ValueError, TypeError):
                        pass

                updated["updated_at"] = now_iso
                self._banners[idx] = updated
                self._save()
                if supabase_service.is_configured():
                    supabase_service.update_banner(banner_id, updated)
                return updated
        return None

    def toggle_banner(self, banner_id: str) -> Optional[Dict[str, Any]]:
        """Toggles active status of a banner."""
        self._reload_if_needed()
        for idx, b in enumerate(self._banners):
            if b.get("id") == banner_id:
                b["is_active"] = not b.get("is_active", True)
                b["updated_at"] = datetime.datetime.utcnow().isoformat() + "Z"
                self._banners[idx] = b
                self._save()
                if supabase_service.is_configured():
                    supabase_service.update_banner(banner_id, {"is_active": b["is_active"], "updated_at": b["updated_at"]})
                return b
        return None

    def reorder_banners(self, order_map: List[Dict[str, Any]]) -> bool:
        """
        Accepts a list of dicts: [{'id': 'banner-id', 'display_order': 1}, ...]
        Updates all banners in memory and persists.
        """
        self._reload_if_needed()
        id_to_order = {item["id"]: int(item["display_order"]) for item in order_map if "id" in item and "display_order" in item}
        for b in self._banners:
            if b.get("id") in id_to_order:
                b["display_order"] = id_to_order[b["id"]]
                b["updated_at"] = datetime.datetime.utcnow().isoformat() + "Z"

        self._banners.sort(key=lambda x: (x.get("display_order", 999), x.get("id", "")))
        saved = self._save()
        if supabase_service.is_configured():
            for b in self._banners:
                supabase_service.update_banner(b["id"], {"display_order": b.get("display_order", 1), "updated_at": b.get("updated_at")})
        return saved

    def delete_banner(self, banner_id: str) -> bool:
        """Deletes a banner by ID and records its deletion."""
        self._reload_if_needed()
        self._record_deleted_id(banner_id)
        initial_count = len(self._banners)
        self._banners = [b for b in self._banners if b.get("id") != banner_id]
        self._save()
        if supabase_service.is_configured():
            supabase_service.delete_banner(banner_id)
        return True

    def sync_banners(self, new_banners: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Replaces the active banners with a synchronized list from admin."""
        self._banners = list(new_banners)
        # Any default banner not present in synchronized list is permanently recorded as deleted
        try:
            if DEFAULT_BANNERS_PATH.exists():
                with open(DEFAULT_BANNERS_PATH, "r", encoding="utf-8") as f:
                    default_items = json.load(f)
                new_ids = {b.get("id") for b in new_banners if b.get("id")}
                for item in default_items:
                    item_id = item.get("id")
                    if item_id and item_id not in new_ids:
                        self._record_deleted_id(item_id)
        except Exception:
            pass
        self._save()
        return self._banners


banner_service = BannerService()
