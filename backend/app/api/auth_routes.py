import logging
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from pydantic import BaseModel, Field

from ..services.banner_service import EGYPTIAN_GOVERNORATES
from ..services.user_service import normalize_phone, user_service, verify_user_token

logger = logging.getLogger(__name__)

router = APIRouter(prefix="", tags=["auth"])


# ==============================================================================
# Pydantic Schemas
# ==============================================================================
class RegisterRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15, description="Egyptian Mobile (01xxxxxxxxx)")
    password: str = Field(..., min_length=6, max_length=100, description="User Password (min 6 characters)")
    name: str = Field(..., min_length=2, max_length=100, description="Full Name")
    governorate: str = Field(default="القاهرة", description="Egyptian Governorate")
    gender: str = Field(default="male", pattern="^(male|female)$", description="Gender: male or female")
    age: Optional[int] = Field(default=None, ge=1, le=120)


class LoginRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15, description="Egyptian Mobile (01xxxxxxxxx)")
    password: str = Field(..., min_length=1, max_length=100, description="User Password")


class ProfileUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=100)
    governorate: Optional[str] = Field(default=None)
    age: Optional[int] = Field(default=None, ge=1, le=120)
    gender: Optional[str] = Field(default=None)


class PrescriptionSyncItem(BaseModel):
    id: Optional[str] = None
    title: str = Field(default="روشتة طبية")
    date: str = Field(default="")
    diagnosis: Optional[str] = ""
    patient_age: Optional[str] = None
    patient_weight: Optional[str] = None
    medications_count: Optional[int] = 0
    medications: List[Dict[str, Any]] = Field(default_factory=list)
    image_url: Optional[str] = None
    image_base64: Optional[str] = None
    created_at: Optional[str] = None


class PrescriptionsSyncPayload(BaseModel):
    prescriptions: List[PrescriptionSyncItem] = Field(default_factory=list)


# ==============================================================================
# Auth Dependency
# ==============================================================================
async def get_current_user(
    authorization: Optional[str] = Header(None),
    token: Optional[str] = Query(None),
) -> Dict[str, Any]:
    raw_token = None
    if authorization:
        raw_token = authorization.replace("Bearer ", "").strip()
    elif token:
        raw_token = token.strip()

    if not raw_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="رمز تسجيل الدخول مفقود (Authorization token missing)",
        )

    verified = verify_user_token(raw_token)
    if not verified:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="جلسة الدخول منتهية أو غير صحيحة، يرجى تسجيل الدخول مجدداً",
        )

    user_id, _ = verified
    user = user_service.get_user_by_id(user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="المستخدم غير موجود بالنظام",
        )
    return user


# ==============================================================================
# Endpoints
# ==============================================================================
@router.post("/auth/register")
async def register_user(payload: RegisterRequest):
    """
    Registers a new mobile app user with strict Egyptian phone validation, password hashing, and gender.
    Returns secure long-lived token and user profile.
    """
    try:
        user, token = user_service.register_user(
            phone=payload.phone,
            password=payload.password,
            name=payload.name,
            governorate=payload.governorate,
            gender=payload.gender,
            age=payload.age,
        )
        return {
            "success": True,
            "message": "تم إنشاء حسابك بنجاح وحفظ بياناتك سحابياً ☁️✓",
            "token": token,
            "user": user,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/auth/login")
async def login_user(payload: LoginRequest):
    """
    Logs in user with phone number and password.
    """
    try:
        user, token = user_service.login_user(
            phone=payload.phone,
            password=payload.password,
        )
        return {
            "success": True,
            "message": f"مرحباً بك {user.get('name', '')} في جرعة ✓",
            "token": token,
            "user": user,
        }
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/auth/me")
async def get_me(current_user: Dict[str, Any] = Depends(get_current_user)):
    """Returns current user's profile and cloud prescription stats."""
    return {
        "success": True,
        "user": current_user,
    }


@router.put("/auth/profile")
async def update_profile(
    payload: ProfileUpdateRequest,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """Updates user's name, governorate, age, or gender."""
    updated = user_service.update_user_profile(
        user_id=current_user["id"],
        name=payload.name,
        governorate=payload.governorate,
        age=payload.age,
        gender=payload.gender,
    )
    return {
        "success": True,
        "message": "تم تحديث بيانات الملف الشخصي بنجاح ✓",
        "user": updated,
    }


@router.get("/auth/governorates")
async def get_auth_governorates():
    """Returns list of Egyptian governorates for registration dropdown."""
    return {
        "success": True,
        "governorates": EGYPTIAN_GOVERNORATES,
    }


# ==============================================================================
# Cloud Prescriptions Sync Endpoints
# ==============================================================================
@router.post("/prescriptions/sync")
async def sync_prescriptions(
    payload: PrescriptionsSyncPayload,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """
    Syncs local prescriptions from mobile app to user's secure cloud storage.
    Ensures prescription history is never lost even if app is uninstalled.
    """
    rxs_data = [item.model_dump() for item in payload.prescriptions]
    synced = user_service.sync_user_prescriptions(current_user["id"], rxs_data)
    return {
        "success": True,
        "message": f"تمت مزامنة {len(synced)} روشتة سحابياً بنجاح ☁️✓",
        "count": len(synced),
        "prescriptions": synced,
    }


@router.get("/prescriptions/sync")
async def get_cloud_prescriptions(current_user: Dict[str, Any] = Depends(get_current_user)):
    """Retrieves all cloud prescriptions for current user."""
    prescriptions = user_service.get_user_prescriptions(current_user["id"])
    return {
        "success": True,
        "count": len(prescriptions),
        "prescriptions": prescriptions,
    }


@router.delete("/prescriptions/sync/{prescription_id}")
async def delete_cloud_prescription(
    prescription_id: str,
    current_user: Dict[str, Any] = Depends(get_current_user),
):
    """Deletes a prescription from user's cloud storage."""
    deleted = user_service.delete_user_prescription(current_user["id"], prescription_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="الروشتة غير موجودة أو تم حذفها مسبقاً")
    return {
        "success": True,
        "message": "تم حذف الروشتة من السحابة بنجاح 🗑️",
    }
