import asyncio
import json
import logging
import re
import time
from typing import Any, Dict, List, Optional
try:
    from google import genai
    from google.genai import types
except ImportError:
    genai = None
    types = None

from ..config import settings
from ..models.prescription import TokenUsage
from .base import ProviderResult, VisionProvider

logger = logging.getLogger(__name__)

_UNSET = object()


def _get_key_manager():
    from ..services.gemini_key_manager import gemini_key_manager
    return gemini_key_manager


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


def clean_json_response(text: str) -> str:
    """Extract clean JSON string by removing markdown code blocks if present."""
    text = text.strip()
    match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if match:
        return match.group(1).strip()
    return text


class GeminiProvider(VisionProvider):
    """
    Vision provider implementation using Google Gemini API with smart multi-key rotation,
    automatic failover on rate limits (429), and token usage accounting.
    """

    def __init__(self, api_key: Any = _UNSET):
        if api_key is _UNSET:
            self._explicit_key = False
            self._api_key = None
        else:
            self._explicit_key = True
            self._api_key = api_key or ""

        self._client = None
        primary_k = self._api_key if self._explicit_key else None
        if not primary_k and not self._explicit_key:
            try:
                primary_k = _get_key_manager().get_primary_key()
            except Exception:
                primary_k = settings.gemini_api_key

        if primary_k and genai:
            try:
                self._client = genai.Client(api_key=primary_k)
            except Exception as e:
                logger.warning(f"Failed to initialize Gemini Client: {e}")

    @property
    def name(self) -> str:
        return "gemini"

    @property
    def display_name(self) -> str:
        return "Google Gemini"

    @property
    def supported_models(self) -> List[str]:
        return [
            "gemini-flash-lite-latest",
            "gemini-3.5-flash-lite",
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-pro-latest",
        ]

    @property
    def default_model(self) -> str:
        return "gemini-flash-lite-latest"

    def is_configured(self) -> bool:
        if self._explicit_key:
            return bool(self._api_key and self._api_key.strip())
        try:
            return _get_key_manager().has_configured_keys()
        except Exception:
            return bool(settings.gemini_api_key and settings.gemini_api_key.strip())

    async def analyze(
        self,
        image_bytes: bytes,
        mime_type: str,
        prompt: str,
        model: Optional[str] = None,
    ) -> ProviderResult:
        if not self.is_configured():
            raise RuntimeError(
                "Gemini API key is not configured. "
                "Please set GEMINI_API_KEY in your .env file or environment."
            )

        model_name = model or self.default_model

        import base64
        import httpx

        start_time = time.perf_counter()
        b64_image = base64.b64encode(image_bytes).decode("utf-8")

        payload = {
            "contents": [
                {
                    "parts": [
                        {
                            "inline_data": {
                                "mime_type": mime_type,
                                "data": b64_image,
                            }
                        },
                        {"text": prompt},
                    ]
                }
            ],
            "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.1,
            },
        }

        models_to_try = [model_name]
        for fallback in [
            "gemini-flash-lite-latest",
            "gemini-3.5-flash-lite",
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-pro-latest",
        ]:
            if fallback not in models_to_try:
                models_to_try.append(fallback)

        km = _get_key_manager()
        if self._explicit_key:
            keys_to_try = [{
                "id": "explicit",
                "key": self._api_key,
                "masked_key": mask_key(self._api_key),
                "label": "المفتاح اليدوي",
            }]
        else:
            keys_to_try = km.get_usable_keys()
            if not keys_to_try:
                raise RuntimeError(
                    "All Gemini API keys in pool are currently exhausted or unavailable. "
                    "Please check your quota in the admin dashboard."
                )

        resp_data = None
        attempted_errors: Dict[str, str] = {}
        used_model = model_name
        used_key_id = None

        async with httpx.AsyncClient(timeout=60.0) as http_client:
            for key_info in keys_to_try:
                current_api_key = key_info["key"]
                key_id = key_info["id"]
                key_label = key_info.get("label", key_id)
                masked_k = key_info.get("masked_key", mask_key(current_api_key))

                headers = {
                    "Content-Type": "application/json",
                    "X-goog-api-key": current_api_key,
                }
                key_success = False

                for current_model in models_to_try:
                    url = f"https://generativelanguage.googleapis.com/v1beta/models/{current_model}:generateContent"
                    try:
                        resp = await http_client.post(url, headers=headers, json=payload)
                        if resp.status_code == 200:
                            resp_data = resp.json()
                            used_model = current_model
                            used_key_id = key_id
                            key_success = True
                            break
                        elif resp.status_code == 429:
                            err_msg = f"HTTP 429: {resp.text}"
                            logger.warning(
                                f"Gemini key '{key_label}' ({masked_k}) hit 429 Rate Limit. "
                                "Switching automatically to next available key..."
                            )
                            if not self._explicit_key:
                                km.record_failure(key_id, 429, resp.text)
                            attempted_errors[f"{key_label}:{current_model}"] = err_msg
                            # Break inner model loop to immediately switch to next key in pool
                            break
                        elif resp.status_code in (400, 403):
                            err_msg = f"HTTP {resp.status_code}: {resp.text}"
                            logger.warning(
                                f"Gemini key '{key_label}' ({masked_k}) returned auth/permission error {resp.status_code}. "
                                "Trying next key..."
                            )
                            if not self._explicit_key:
                                km.record_failure(key_id, resp.status_code, resp.text)
                            attempted_errors[f"{key_label}:{current_model}"] = err_msg
                            break
                        elif resp.status_code in (503, 500, 502, 404):
                            err_msg = f"HTTP {resp.status_code}: {resp.text}"
                            logger.warning(f"Gemini {current_model} returned {resp.status_code}, trying fallback in 1s...")
                            attempted_errors[f"{key_label}:{current_model}"] = err_msg
                            await asyncio.sleep(1.0)
                            continue
                        else:
                            err_msg = f"HTTP {resp.status_code}: {resp.text}"
                            attempted_errors[f"{key_label}:{current_model}"] = err_msg
                            break
                    except Exception as exc:
                        logger.warning(f"Gemini error with {key_label} on {current_model}: {exc}")
                        attempted_errors[f"{key_label}:{current_model}"] = str(exc)

                if key_success:
                    break

        if not resp_data:
            err_summary = "; ".join(f"{k}: {v[:80]}" for k, v in attempted_errors.items())
            raise RuntimeError(f"All Gemini models/keys failed: {err_summary}")

        candidates = resp_data.get("candidates", [])
        if not candidates:
            raise RuntimeError(f"No candidates returned: {resp_data}")
        parts = candidates[0].get("content", {}).get("parts", [])
        raw_text = parts[0].get("text", "") if parts else ""
        usage_meta = resp_data.get("usageMetadata", {})
        model_name = used_model

        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        cleaned_json_text = clean_json_response(raw_text)

        try:
            parsed_data = json.loads(cleaned_json_text)
            if isinstance(parsed_data, list):
                if len(parsed_data) > 0 and isinstance(parsed_data[0], dict):
                    parsed_data = parsed_data[0]
                else:
                    parsed_data = {"items": parsed_data}
        except json.JSONDecodeError as exc:
            logger.error(f"Failed to parse JSON from Gemini output: {raw_text}")
            raise ValueError(
                f"Gemini returned invalid JSON structure: {str(exc)}"
            ) from exc

        usage = TokenUsage()
        if usage_meta:
            usage.prompt_tokens = usage_meta.get("promptTokenCount", 0) or 0
            usage.completion_tokens = usage_meta.get("candidatesTokenCount", 0) or 0
            usage.total_tokens = usage_meta.get("totalTokenCount", 0) or (
                usage.prompt_tokens + usage.completion_tokens
            )

        # Record successful usage and tokens on the key that succeeded
        if used_key_id and not self._explicit_key:
            km.record_success(
                used_key_id,
                prompt_tokens=usage.prompt_tokens,
                completion_tokens=usage.completion_tokens,
            )

        return ProviderResult(
            provider=self.name,
            model=model_name,
            raw_text=raw_text,
            parsed_json=parsed_data,
            usage=usage,
            latency_ms=elapsed_ms,
        )
