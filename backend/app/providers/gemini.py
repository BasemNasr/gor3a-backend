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


def clean_json_response(text: str) -> str:
    """Extract clean JSON string by removing markdown code blocks if present."""
    text = text.strip()
    match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
    if match:
        return match.group(1).strip()
    return text


class GeminiProvider(VisionProvider):
    """Vision provider implementation using Google Gemini API (via google-genai SDK)."""

    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key or settings.gemini_api_key
        self._client = None
        if self._api_key and genai:
            try:
                self._client = genai.Client(api_key=self._api_key)
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
        return bool(self._api_key and self._api_key.strip())

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

        if not self._client and genai:
            try:
                self._client = genai.Client(api_key=self._api_key)
            except Exception:
                pass

        import base64
        import httpx

        start_time = time.perf_counter()
        b64_image = base64.b64encode(image_bytes).decode("utf-8")

        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent"
        headers = {
            "Content-Type": "application/json",
            "X-goog-api-key": self._api_key,
        }
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

        resp_data = None
        attempted_errors: Dict[str, str] = {}
        used_model = model_name

        async with httpx.AsyncClient(timeout=60.0) as http_client:
            for current_model in models_to_try:
                url = f"https://generativelanguage.googleapis.com/v1beta/models/{current_model}:generateContent"
                try:
                    resp = await http_client.post(url, headers=headers, json=payload)
                    if resp.status_code == 200:
                        resp_data = resp.json()
                        used_model = current_model
                        break
                    else:
                        err_msg = f"HTTP {resp.status_code}: {resp.text}"
                        attempted_errors[current_model] = err_msg
                        if resp.status_code in (503, 429, 404):
                            logger.warning(f"Gemini {current_model} returned {resp.status_code}, trying fallback in 1s...")
                            await asyncio.sleep(1.0)
                            continue
                        else:
                            raise RuntimeError(f"Gemini {current_model} error: {err_msg}")
                except Exception as exc:
                    logger.warning(f"Gemini error with {current_model}: {exc}")
                    attempted_errors[current_model] = str(exc)

        if not resp_data:
            err_summary = "; ".join(f"{k}: {v[:100]}" for k, v in attempted_errors.items())
            raise RuntimeError(f"All Gemini models failed: {err_summary}")

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

        return ProviderResult(
            provider=self.name,
            model=model_name,
            raw_text=raw_text,
            parsed_json=parsed_data,
            usage=usage,
            latency_ms=elapsed_ms,
        )
