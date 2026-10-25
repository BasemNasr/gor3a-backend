import base64
import datetime
import hashlib
import hmac
import json
import logging
import os
import re
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from .supabase_service import supabase_service

logger = logging.getLogger(__name__)

USER_SECRET = os.environ.get("USER_JWT_SECRET", "gor3a-mobile-user-secret-key-2026")
DEFAULT_USERS_PATH = PROJECT_ROOT / "dataset" / "users" / "users.json"
TMP_USERS_PATH = Path("/tmp/users.json")

DEFAULT_PRESCRIPTIONS_PATH = PROJECT_ROOT / "dataset" / "users" / "user_prescriptions.json"
TMP_PRESCRIPTIONS_PATH = Path("/tmp/user_prescriptions.json")


def normalize_phone(phone: str) -> str:
    """
    Normalizes Egyptian mobile phone numbers to canonical 01xxxxxxxxx format.
    Handles +20, 0020, 20 prefixes and spacing.
    """
    clean = re.sub(r"[\s\-\(\)\+]", "", phone.strip())
    if clean.startswith("0020"):
        clean = "0" + clean[4:]
    elif clean.startswith("20") and len(clean) >= 12:
        clean = "0" + clean[2:]
    elif clean.startswith("0") is False and len(clean) == 10:
        clean = "0" + clean

    # Ensure starts with 01 and has 11 digits
    if re.match(r"^01[0125][0-9]{8}$", clean):
        return clean
    return clean


EGYPTIAN_PHONE_REGEX = re.compile(r"^01[0125][0-9]{8}$")


def is_valid_egyptian_phone(phone: str) -> bool:
    """Checks whether the phone is a valid Egyptian mobile number (Vodafone, Orange, Etisalat, WE)."""
    if not phone:
        return False
    clean = normalize_phone(phone)
    return bool(EGYPTIAN_PHONE_REGEX.match(clean))


def hash_password(password: str, salt: Optional[str] = None) -> Tuple[str, str]:
    """Hashes a password with SHA256 PBKDF2 and returns (hash_hex, salt)."""
    if not salt:
        salt = uuid.uuid4().hex[:16]
    derived = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt.encode("utf-8"),
        100000,
    ).hex()
    return derived, salt


def verify_password(password: str, stored_hash: str, salt: str) -> bool:
    """Verifies a password against the stored hash and salt."""
    if not stored_hash or not salt:
        return True
    derived, _ = hash_password(password, salt)
    return hmac.compare_digest(derived, stored_hash)


def sanitize_user(user: Dict[str, Any]) -> Dict[str, Any]:
    """Removes sensitive password hash/salt from user object before returning to client."""
    safe = dict(user)
    safe.pop("password_hash", None)
    safe.pop("password_salt", None)
    return safe


def create_user_token(user_id: str, phone: str) -> str:
    """Creates a secure HMAC token valid for 365 days."""
    timestamp = int(time.time())
    data = f"{user_id}:{phone}:{timestamp}"
    sig = hmac.new(USER_SECRET.encode(), data.encode(), hashlib.sha256).hexdigest()
    raw = f"{data}:{sig}"
    return base64.urlsafe_b64encode(raw.encode()).decode()


def verify_user_token(token: str) -> Optional[Tuple[str, str]]:
    """
    Verifies user token and returns (user_id, phone) or None if invalid/expired.
    """
    try:
        raw = base64.urlsafe_b64decode(token.encode()).decode()
        parts = raw.split(":")
        if len(parts) != 4:
            return None
        user_id, phone, ts_str, sig = parts
        timestamp = int(ts_str)
        # 365 days validity
        if time.time() - timestamp > 365 * 86400:
            return None
        expected_sig = hmac.new(
            USER_SECRET.encode(),
            f"{user_id}:{phone}:{timestamp}".encode(),
            hashlib.sha256,
        ).hexdigest()
        if hmac.compare_digest(sig, expected_sig):
            return user_id, phone
    except Exception:
        pass
    return None


class UserService:
    def __init__(self):
        self._users: List[Dict[str, Any]] = []
        self._prescriptions: List[Dict[str, Any]] = []
        self._load()

    def _reload_if_needed(self):
        """Ensures memory reflects latest state in serverless environments."""
        if supabase_service.is_configured():
            cloud_users = supabase_service.fetch_all_users()
            if cloud_users is not None:
                self._users = cloud_users
            cloud_rxs = supabase_service.fetch_all_prescriptions()
            if cloud_rxs is not None:
                self._prescriptions = cloud_rxs
            return

        if TMP_USERS_PATH.exists():
            try:
                with open(TMP_USERS_PATH, "r", encoding="utf-8") as f:
                    self._users = json.load(f)
            except Exception:
                pass

        if TMP_PRESCRIPTIONS_PATH.exists():
            try:
                with open(TMP_PRESCRIPTIONS_PATH, "r", encoding="utf-8") as f:
                    self._prescriptions = json.load(f)
            except Exception:
                pass

    def _load_users_from_disk(self):
        user_path = None
        if TMP_USERS_PATH.exists():
            user_path = TMP_USERS_PATH
        elif DEFAULT_USERS_PATH.exists():
            user_path = DEFAULT_USERS_PATH

        if user_path:
            try:
                with open(user_path, "r", encoding="utf-8") as f:
                    self._users = json.load(f)
                logger.info(f"Loaded {len(self._users)} users from {user_path}")
            except Exception as e:
                logger.warning(f"Failed to load users from {user_path}: {e}")
                self._users = []
        else:
            self._users = []

    def _load_prescriptions_from_disk(self):
        rx_path = None
        if TMP_PRESCRIPTIONS_PATH.exists():
            rx_path = TMP_PRESCRIPTIONS_PATH
        elif DEFAULT_PRESCRIPTIONS_PATH.exists():
            rx_path = DEFAULT_PRESCRIPTIONS_PATH

        if rx_path:
            try:
                with open(rx_path, "r", encoding="utf-8") as f:
                    self._prescriptions = json.load(f)
                logger.info(f"Loaded {len(self._prescriptions)} cloud prescriptions from {rx_path}")
            except Exception as e:
                logger.warning(f"Failed to load cloud prescriptions: {e}")
                self._prescriptions = []
        else:
            self._prescriptions = []

    def _load(self):
        """Loads users and cloud prescriptions from Supabase or persistent files."""
        # 1. Users
        if supabase_service.is_configured():
            cloud_users = supabase_service.fetch_all_users()
            if cloud_users is not None:
                self._users = cloud_users
                logger.info(f"Loaded {len(self._users)} users from Supabase Cloud DB")
                self._save_users()
            else:
                self._load_users_from_disk()
        else:
            self._load_users_from_disk()

        # 2. Cloud Prescriptions
        if supabase_service.is_configured():
            cloud_rxs = supabase_service.fetch_all_prescriptions()
            if cloud_rxs is not None:
                self._prescriptions = cloud_rxs
                logger.info(f"Loaded {len(self._prescriptions)} prescriptions from Supabase Cloud DB")
                self._save_prescriptions()
            else:
                self._load_prescriptions_from_disk()
        else:
            self._load_prescriptions_from_disk()

    def _save_users(self) -> bool:
        """Saves users to disk and /tmp."""
        try:
            DEFAULT_USERS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(DEFAULT_USERS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._users, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

        try:
            TMP_USERS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(TMP_USERS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._users, f, ensure_ascii=False, indent=2)
            return True
        except Exception as exc:
            logger.warning(f"Failed to save users to {TMP_USERS_PATH}: {exc}")
            return False

    def _save_prescriptions(self) -> bool:
        """Saves user prescriptions to disk and /tmp."""
        try:
            DEFAULT_PRESCRIPTIONS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(DEFAULT_PRESCRIPTIONS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._prescriptions, f, ensure_ascii=False, indent=2)
        except Exception:
            pass

        try:
            TMP_PRESCRIPTIONS_PATH.parent.mkdir(parents=True, exist_ok=True)
            with open(TMP_PRESCRIPTIONS_PATH, "w", encoding="utf-8") as f:
                json.dump(self._prescriptions, f, ensure_ascii=False, indent=2)
            return True
        except Exception as exc:
            logger.warning(f"Failed to save prescriptions to {TMP_PRESCRIPTIONS_PATH}: {exc}")
            return False

    def register_user(
        self,
        phone: str,
        password: str,
        name: str,
        governorate: str = "القاهرة",
        gender: str = "male",
        age: Optional[int] = None,
    ) -> Tuple[Dict[str, Any], str]:
        """
        Registers a new user with strict Egyptian phone validation, password hashing, and gender.
        """
        self._reload_if_needed()
        if not is_valid_egyptian_phone(phone):
            raise ValueError("يرجى إدخال رقم هاتف محمول مصري صحيح (مثال: 01012345678 أو 011 أو 012 أو 015)")

        if not password or len(password.strip()) < 6:
            raise ValueError("يجب أن تتكون كلمة المرور من 6 أحرف أو أرقام على الأقل")

        if not name or len(name.strip()) < 2:
            raise ValueError("يرجى كتابة الاسم بالكامل")

        norm_phone = normalize_phone(phone)
        existing = next((u for u in self._users if u.get("phone") == norm_phone), None)
        if existing:
            raise ValueError("رقم الهاتف مسجل مسبقاً بالفعل، يرجى تسجيل الدخول")

        now_iso = datetime.datetime.utcnow().isoformat() + "Z"
        new_id = f"usr-{uuid.uuid4().hex[:10]}"
        pwd_hash, pwd_salt = hash_password(password.strip())

        clean_gender = gender.strip().lower() if gender else "male"
        if clean_gender not in ["male", "female"]:
            clean_gender = "male"

        new_user = {
            "id": new_id,
            "phone": norm_phone,
            "name": name.strip(),
            "governorate": governorate.strip() if governorate and governorate.strip() else "القاهرة",
            "gender": clean_gender,
            "age": age,
            "password_hash": pwd_hash,
            "password_salt": pwd_salt,
            "created_at": now_iso,
            "updated_at": now_iso,
            "last_login_at": now_iso,
            "prescriptions_count": 0,
        }

        self._users.append(new_user)
        self._save_users()
        if supabase_service.is_configured():
            supabase_service.insert_user(new_user)
        token = create_user_token(new_id, norm_phone)
        return sanitize_user(new_user), token

    def login_user(
        self,
        phone: str,
        password: str,
    ) -> Tuple[Dict[str, Any], str]:
        """
        Logs in user verifying phone and password.
        """
        self._reload_if_needed()
        if not is_valid_egyptian_phone(phone):
            raise ValueError("رقم الهاتف المحمول غير صحيح، يرجى إدخال رقم مصري يبدأ بـ 010 أو 011 أو 012 أو 015")

        if not password:
            raise ValueError("يرجى إدخال كلمة المرور")

        norm_phone = normalize_phone(phone)
        user = next((u for u in self._users if u.get("phone") == norm_phone), None)
        if not user:
            raise ValueError("رقم الهاتف غير مسجل مسبقاً، يرجى إنشاء حساب جديد")

        stored_hash = user.get("password_hash")
        stored_salt = user.get("password_salt")
        if stored_hash and stored_salt:
            if not verify_password(password.strip(), stored_hash, stored_salt):
                raise ValueError("كلمة المرور غير صحيحة، يرجى التأكد والمحاولة ثانية")
        else:
            # Set password for legacy accounts
            pwd_hash, pwd_salt = hash_password(password.strip())
            user["password_hash"] = pwd_hash
            user["password_salt"] = pwd_salt

        now_iso = datetime.datetime.utcnow().isoformat() + "Z"
        user["last_login_at"] = now_iso
        user["prescriptions_count"] = self.count_user_prescriptions(user["id"])
        self._save_users()
        if supabase_service.is_configured():
            supabase_service.update_user(user["id"], {
                "last_login_at": now_iso,
                "prescriptions_count": user["prescriptions_count"],
                "password_hash": user.get("password_hash"),
                "password_salt": user.get("password_salt"),
            })

        token = create_user_token(user["id"], user["phone"])
        return sanitize_user(user), token

    def register_or_login(
        self,
        phone: str,
        name: Optional[str] = None,
        governorate: Optional[str] = None,
        age: Optional[int] = None,
        gender: Optional[str] = None,
    ) -> Tuple[Dict[str, Any], str, bool]:
        """
        Registers a new user or logs in an existing one by phone number.
        Returns (user_dict, token, is_new_user).
        """
        self._reload_if_needed()
        norm_phone = normalize_phone(phone)
        now_iso = datetime.datetime.utcnow().isoformat() + "Z"

        existing = next((u for u in self._users if u.get("phone") == norm_phone), None)
        if existing:
            # Update last login and any newly provided fields
            existing["last_login_at"] = now_iso
            if name and name.strip():
                existing["name"] = name.strip()
            if governorate and governorate.strip():
                existing["governorate"] = governorate.strip()
            if age is not None:
                existing["age"] = age
            if gender and gender.strip():
                existing["gender"] = gender.strip()

            existing["prescriptions_count"] = self.count_user_prescriptions(existing["id"])
            self._save_users()
            if supabase_service.is_configured():
                supabase_service.update_user(existing["id"], existing)
            token = create_user_token(existing["id"], existing["phone"])
            return existing, token, False

        # Create new user
        new_id = f"usr-{uuid.uuid4().hex[:10]}"
        new_user = {
            "id": new_id,
            "phone": norm_phone,
            "name": name.strip() if name and name.strip() else f"مستخدم {norm_phone[-4:]}",
            "governorate": governorate.strip() if governorate and governorate.strip() else "القاهرة",
            "age": age,
            "gender": gender.strip() if gender and gender.strip() else None,
            "created_at": now_iso,
            "updated_at": now_iso,
            "last_login_at": now_iso,
            "prescriptions_count": 0,
        }
        self._users.append(new_user)
        self._save_users()
        if supabase_service.is_configured():
            supabase_service.insert_user(new_user)
        token = create_user_token(new_id, norm_phone)
        return new_user, token, True

    def get_user_by_id(self, user_id: str) -> Optional[Dict[str, Any]]:
        self._reload_if_needed()
        user = next((u for u in self._users if u.get("id") == user_id), None)
        if user:
            user_copy = sanitize_user(user)
            user_copy["prescriptions_count"] = self.count_user_prescriptions(user_id)
            return user_copy
        return None

    def get_user_by_phone(self, phone: str) -> Optional[Dict[str, Any]]:
        self._reload_if_needed()
        norm_phone = normalize_phone(phone)
        user = next((u for u in self._users if u.get("phone") == norm_phone), None)
        if user:
            user_copy = sanitize_user(user)
            user_copy["prescriptions_count"] = self.count_user_prescriptions(user["id"])
            return user_copy
        return None

    def update_user_profile(
        self,
        user_id: str,
        name: Optional[str] = None,
        governorate: Optional[str] = None,
        age: Optional[int] = None,
        gender: Optional[str] = None,
    ) -> Optional[Dict[str, Any]]:
        self._reload_if_needed()
        user = next((u for u in self._users if u.get("id") == user_id), None)
        if not user:
            return None

        if name is not None:
            user["name"] = name.strip()
        if governorate is not None:
            user["governorate"] = governorate.strip()
        if age is not None:
            user["age"] = age
        if gender is not None:
            user["gender"] = gender.strip()

        user["updated_at"] = datetime.datetime.utcnow().isoformat() + "Z"
        self._save_users()
        if supabase_service.is_configured():
            supabase_service.update_user(user_id, {
                "name": user.get("name"),
                "governorate": user.get("governorate"),
                "age": user.get("age"),
                "gender": user.get("gender"),
                "updated_at": user["updated_at"],
            })
        user_copy = dict(user)
        user_copy["prescriptions_count"] = self.count_user_prescriptions(user_id)
        return user_copy

    # --------------------------------------------------------------------------
    # Cloud Prescriptions Management
    # --------------------------------------------------------------------------
    def count_user_prescriptions(self, user_id: str) -> int:
        return sum(1 for p in self._prescriptions if p.get("user_id") == user_id)

    def get_user_prescriptions(self, user_id: str) -> List[Dict[str, Any]]:
        self._reload_if_needed()
        user_rxs = [p for p in self._prescriptions if p.get("user_id") == user_id]
        return sorted(user_rxs, key=lambda x: x.get("created_at", ""), reverse=True)

    def sync_user_prescriptions(
        self, user_id: str, incoming_prescriptions: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Upserts prescriptions for a specific user into cloud storage.
        Preserves existing items while adding/updating new ones.
        """
        self._reload_if_needed()
        now_iso = datetime.datetime.utcnow().isoformat() + "Z"

        existing_map = {
            p.get("id"): p
            for p in self._prescriptions
            if p.get("user_id") == user_id and p.get("id")
        }

        for item in incoming_prescriptions:
            rx_id = item.get("id") or f"rx-{uuid.uuid4().hex[:8]}"
            entry = {
                "id": rx_id,
                "user_id": user_id,
                "title": item.get("title", "روشتة جديدة"),
                "date": item.get("date", datetime.date.today().isoformat()),
                "diagnosis": item.get("diagnosis", ""),
                "patient_age": item.get("patient_age"),
                "patient_weight": item.get("patient_weight"),
                "medications_count": item.get("medications_count", len(item.get("medications", []))),
                "medications": item.get("medications", []),
                "created_at": item.get("created_at") or existing_map.get(rx_id, {}).get("created_at") or now_iso,
                "updated_at": now_iso,
            }
            existing_map[rx_id] = entry

        # Rebuild full list: others + this user's updated items
        other_users_rxs = [p for p in self._prescriptions if p.get("user_id") != user_id]
        this_user_rxs = list(existing_map.values())
        self._prescriptions = other_users_rxs + this_user_rxs
        self._save_prescriptions()

        if supabase_service.is_configured():
            for item in this_user_rxs:
                supabase_service.upsert_prescription(item)

        # Update prescription count on user record
        user = next((u for u in self._users if u.get("id") == user_id), None)
        if user:
            user["prescriptions_count"] = len(this_user_rxs)
            self._save_users()
            if supabase_service.is_configured():
                supabase_service.update_user(user_id, {"prescriptions_count": len(this_user_rxs)})

        return sorted(this_user_rxs, key=lambda x: x.get("created_at", ""), reverse=True)

    def delete_user_prescription(self, user_id: str, prescription_id: str) -> bool:
        self._reload_if_needed()
        before_count = len(self._prescriptions)
        self._prescriptions = [
            p
            for p in self._prescriptions
            if not (p.get("user_id") == user_id and p.get("id") == prescription_id)
        ]
        if len(self._prescriptions) < before_count:
            self._save_prescriptions()
            if supabase_service.is_configured():
                supabase_service.delete_prescription(prescription_id, user_id)
            user = next((u for u in self._users if u.get("id") == user_id), None)
            if user:
                user["prescriptions_count"] = self.count_user_prescriptions(user_id)
                self._save_users()
                if supabase_service.is_configured():
                    supabase_service.update_user(user_id, {"prescriptions_count": user["prescriptions_count"]})
            return True
        return False

    # --------------------------------------------------------------------------
    # Admin Panel Methods
    # --------------------------------------------------------------------------
    def get_all_users_for_admin(self) -> List[Dict[str, Any]]:
        """Returns all registered mobile app users with calculated stats for Admin."""
        self._reload_if_needed()
        results = []
        for u in sorted(self._users, key=lambda x: x.get("created_at", ""), reverse=True):
            user_data = sanitize_user(u)
            user_data["prescriptions_count"] = self.count_user_prescriptions(u["id"])
            results.append(user_data)
        return results

    def get_stats(self) -> Dict[str, Any]:
        self._reload_if_needed()
        total_users = len(self._users)
        total_rxs = len(self._prescriptions)

        gov_counts: Dict[str, int] = {}
        for u in self._users:
            gov = u.get("governorate") or "غير محدد"
            gov_counts[gov] = gov_counts.get(gov, 0) + 1

        top_gov = "القاهرة"
        if gov_counts:
            top_gov = max(gov_counts.items(), key=lambda x: x[1])[0]

        return {
            "total_users": total_users,
            "total_prescriptions": total_rxs,
            "top_governorate": top_gov,
            "governorates_distribution": gov_counts,
        }


user_service = UserService()
