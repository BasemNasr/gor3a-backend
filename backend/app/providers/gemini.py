import json
import logging
import re
import time
from typing import Any, Dict, List, Optional
from google import genai
from google.genai import types

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
        self._client: Optional[genai.Client] = None
        if self._api_key:
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
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash",
        ]

    @property
    def default_model(self) -> str:
        return "gemini-2.5-flash"

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

        if not self._client:
            self._client = genai.Client(api_key=self._api_key)

        start_time = time.perf_counter()

        image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)
        contents = [image_part, prompt]

        config = types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0.1,  # Low temperature to minimize hallucination
        )

        try:
            response = await self._client.aio.models.generate_content(
                model=model_name,
                contents=contents,
                config=config,
            )
        except Exception as exc:
            logger.error(f"Gemini API error ({model_name}): {exc}")
            raise RuntimeError(f"Gemini API call failed: {str(exc)}") from exc

        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        raw_text = response.text or ""
        cleaned_json_text = clean_json_response(raw_text)

        try:
            parsed_data = json.loads(cleaned_json_text)
        except json.JSONDecodeError as exc:
            logger.error(f"Failed to parse JSON from Gemini output: {raw_text}")
            raise ValueError(
                f"Gemini returned invalid JSON structure: {str(exc)}"
            ) from exc

        usage = TokenUsage()
        if hasattr(response, "usage_metadata") and response.usage_metadata:
            meta = response.usage_metadata
            usage.prompt_tokens = getattr(meta, "prompt_token_count", 0) or 0
            usage.completion_tokens = getattr(meta, "candidates_token_count", 0) or 0
            usage.total_tokens = getattr(meta, "total_token_count", 0) or (
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
