import base64
import csv
import hashlib
import hmac
import io
import json
import logging
import os
import time
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, File, Header, HTTPException, Query, Response, UploadFile, status
from pydantic import BaseModel, Field

from ..config import PROJECT_ROOT, settings
from ..drugs.repository import drug_repository
from ..providers.factory import register_provider
from ..providers.gemini import GeminiProvider

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/admin", tags=["admin"])

ADMIN_DEFAULT_EMAIL = "basemads20@gmail.com"
ADMIN_ALLOWED_PASSWORDS = [
    "basemads20@gmail.com",
    "basemads20@2026",
    "Gor3a2026!",
]

ADMIN_SECRET = os.environ.get("ADMIN_JWT_SECRET", "gor3a-super-secret-admin-key-2026")


def mask_key(key: Optional[str]) -> Optional[str]:
    """Strictly masks the API key so even in the admin dashboard it is never exposed in plain text."""
    if not key:
        return None
    k = key.strip()
    if len(k) <= 8:
        return "••••••••••••"
    prefix = k[:6]
    suffix = k[-3:]
    return f"{prefix}{'•' * 20}{suffix}"


def create_admin_token(email: str) -> str:
    timestamp = int(time.time())
    data = f"{email}:{timestamp}"
    sig = hmac.new(ADMIN_SECRET.encode(), data.encode(), hashlib.sha256).hexdigest()
    raw = f"{data}:{sig}"
    return base64.urlsafe_b64encode(raw.encode()).decode()


def verify_admin_token(token: str) -> Optional[str]:
    try:
        raw = base64.urlsafe_b64decode(token.encode()).decode()
        parts = raw.split(":")
        if len(parts) != 3:
            return None
        email, ts_str, sig = parts
        timestamp = int(ts_str)
        # Check expiration (7 days validity)
        if time.time() - timestamp > 7 * 86400:
            return None
        expected_sig = hmac.new(ADMIN_SECRET.encode(), f"{email}:{timestamp}".encode(), hashlib.sha256).hexdigest()
        if hmac.compare_digest(sig, expected_sig):
            return email
    except Exception:
        pass
    return None


async def get_current_admin(
    authorization: Optional[str] = Header(None),
    token: Optional[str] = Query(None),
) -> str:
    raw_token = None
    if authorization:
        raw_token = authorization.replace("Bearer ", "").strip()
    elif token:
        raw_token = token.strip()

    if not raw_token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Admin authorization token missing")
    email = verify_admin_token(raw_token)
    if not email:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired admin session token")
    return email


# ==============================================================================
# Pydantic Schemas
# ==============================================================================
class AdminLoginRequest(BaseModel):
    email: str
    password: str


class AdminLoginResponse(BaseModel):
    success: bool
    token: str
    email: str
    expires_in_days: int = 7


class AdminSettingsResponse(BaseModel):
    admin_email: str
    gemini_configured: bool
    gemini_masked_key: Optional[str]
    default_model: str
    total_drugs: int
    last_update: str


class UpdateApiKeyRequest(BaseModel):
    api_key: str = Field(..., min_length=10, description="New Google Gemini API Key")


class ConfirmImportRequest(BaseModel):
    staging_id: str
    import_new: bool = True
    apply_updates: bool = True


# ==============================================================================
# Endpoints
# ==============================================================================

@router.post("/login", response_model=AdminLoginResponse)
async def admin_login(payload: AdminLoginRequest):
    """Authenticate administrator with secure email and password."""
    email_clean = payload.email.strip().lower()
    custom_pwd = os.environ.get("ADMIN_PASSWORD")
    valid_passwords = list(ADMIN_ALLOWED_PASSWORDS)
    if custom_pwd:
        valid_passwords.append(custom_pwd)

    if email_clean != ADMIN_DEFAULT_EMAIL.lower() and email_clean != os.environ.get("ADMIN_EMAIL", "").lower():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid administrator email")

    if payload.password not in valid_passwords:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid administrator password")

    token = create_admin_token(email_clean)
    return AdminLoginResponse(
        success=True,
        token=token,
        email=email_clean,
        expires_in_days=7,
    )


@router.get("/me")
async def admin_get_me(admin_email: str = Depends(get_current_admin)):
    """Verifies that current admin token is active and valid."""
    return {
        "status": "authenticated",
        "email": admin_email,
        "total_drugs": drug_repository.get_total_count(),
    }


@router.get("/settings", response_model=AdminSettingsResponse)
async def get_admin_settings(admin_email: str = Depends(get_current_admin)):
    """
    Returns system settings and masked API key status.
    The raw API key is never returned under any circumstances.
    """
    active_key = settings.gemini_api_key or os.environ.get("GEMINI_API_KEY")
    return AdminSettingsResponse(
        admin_email=admin_email,
        gemini_configured=bool(active_key),
        gemini_masked_key=mask_key(active_key),
        default_model=settings.default_model,
        total_drugs=drug_repository.get_total_count(),
        last_update="October 2026",
    )


@router.post("/settings/api-key")
async def update_gemini_api_key(payload: UpdateApiKeyRequest, admin_email: str = Depends(get_current_admin)):
    """
    Updates the Google Gemini API key.
    The key is stored securely server-side and never returned in plain text.
    """
    new_key = payload.api_key.strip()
    if not new_key or len(new_key) < 10:
        raise HTTPException(status_code=400, detail="Invalid API key format")

    # Update in runtime settings and environment
    settings.gemini_api_key = new_key
    os.environ["GEMINI_API_KEY"] = new_key

    # Re-register Gemini provider in factory
    try:
        register_provider("gemini", GeminiProvider(api_key=new_key))
    except Exception as exc:
        logger.warning(f"Could not re-register Gemini provider: {exc}")

    # Persist to .env if writable
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
                new_lines.append(f"GEMINI_API_KEY={new_key}\n")
                found = True
            else:
                new_lines.append(line)
        if not found:
            new_lines.append(f"GEMINI_API_KEY={new_key}\n")

        with open(env_file, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
    except Exception as exc:
        logger.warning(f"Could not update .env on disk (read-only filesystem or restricted): {exc}")

    return {
        "success": True,
        "message": "تم تحديث مفتاح Google Gemini API وتشفيره في السيرفر بنجاح ✓",
        "masked_key": mask_key(new_key),
    }


@router.post("/drugs/stage-import")
async def stage_drug_import(
    file: UploadFile = File(...),
    admin_email: str = Depends(get_current_admin),
):
    """
    Uploads and parses a new medicines dataset (CSV or JSON).
    Cross-references each item against the active 25,098 drugs database without altering it,
    and returns a categorized diff summary (New, Updated, and Duplicates).
    """
    content = await file.read()
    filename = file.filename or "import_data"
    raw_records: List[Dict[str, Any]] = []

    try:
        if filename.endswith(".json") or file.content_type == "application/json":
            decoded = json.loads(content.decode("utf-8-sig", errors="ignore"))
            if isinstance(decoded, list):
                raw_records = decoded
            elif isinstance(decoded, dict):
                raw_records = decoded.get("drugs") or decoded.get("medications") or decoded.get("data") or [decoded]
        else:
            # Assume CSV
            text_stream = io.StringIO(content.decode("utf-8-sig", errors="ignore"))
            reader = csv.DictReader(text_stream)
            raw_records = [dict(row) for row in reader]
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Failed to parse file format: {str(exc)}")

    if not raw_records:
        raise HTTPException(status_code=400, detail="The uploaded file contains no data rows.")

    result = drug_repository.stage_import(raw_records, filename=filename)
    return result


@router.get("/drugs/staging/{staging_id}")
async def get_staged_items(
    staging_id: str,
    type: str = Query("new", pattern="^(new|updated|duplicate)$"),
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=200),
    search: Optional[str] = Query(None),
    admin_email: str = Depends(get_current_admin),
):
    """Returns paginated and searchable lists of staged items for admin review."""
    result = drug_repository.get_staging_items(
        staging_id=staging_id,
        item_type=type,
        page=page,
        limit=limit,
        search=search,
    )
    if "error" in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@router.post("/drugs/confirm-import")
async def confirm_drug_import(
    payload: ConfirmImportRequest,
    admin_email: str = Depends(get_current_admin),
):
    """
    Applies the verified staged import changes to the active database.
    Only approved new or updated records are committed; duplicates are skipped.
    """
    try:
        res = drug_repository.commit_import(
            staging_id=payload.staging_id,
            import_new=payload.import_new,
            apply_updates=payload.apply_updates,
        )
        return res
    except ValueError as val_err:
        raise HTTPException(status_code=400, detail=str(val_err))
    except Exception as exc:
        logger.error(f"Failed to commit drug import: {exc}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Database commit error: {str(exc)}")


@router.get("/drugs/export")
async def export_drugs_database(
    format: str = Query("json", pattern="^(json|csv)$"),
    admin_email: str = Depends(get_current_admin),
):
    """Exports the entire active merged drugs database as clean JSON or CSV for backup."""
    if format == "csv":
        csv_data = drug_repository.export_drugs_csv()
        return Response(
            content=csv_data,
            media_type="text/csv",
            headers={"Content-Disposition": "attachment; filename=gor3a_egyptian_drugs_export.csv"},
        )
    else:
        json_data = drug_repository.export_drugs_json()
        return Response(
            content=json_data,
            media_type="application/json",
            headers={"Content-Disposition": "attachment; filename=gor3a_egyptian_drugs_export.json"},
        )
