import base64
import json
import logging
import re
import time
from typing import Any, Dict, List, Optional
from anthropic import AsyncAnthropic

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


class ClaudeProvider(VisionProvider):
    """Vision provider implementation using Anthropic Claude API (via AsyncAnthropic)."""

    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key or settings.anthropic_api_key
        self._client: Optional[AsyncAnthropic] = None
        if self._api_key:
            try:
                self._client = AsyncAnthropic(api_key=self._api_key)
            except Exception as e:
                logger.warning(f"Failed to initialize Anthropic client: {e}")

    @property
    def name(self) -> str:
        return "claude"

    @property
    def display_name(self) -> str:
        return "Anthropic Claude"

    @property
    def supported_models(self) -> List[str]:
        return [
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
        ]

    @property
    def default_model(self) -> str:
        return "claude-3-5-sonnet-20241022"

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
                "Anthropic API key is not configured. "
                "Please set ANTHROPIC_API_KEY in your .env file or environment."
            )

        model_name = model or self.default_model

        if not self._client:
            self._client = AsyncAnthropic(api_key=self._api_key)

        start_time = time.perf_counter()

        b64_image = base64.b64encode(image_bytes).decode("utf-8")

        # Map common mime types to Claude's supported media types
        media_type = mime_type
        if media_type not in ("image/jpeg", "image/png", "image/gif", "image/webp"):
            media_type = "image/jpeg"

        try:
            response = await self._client.messages.create(
                model=model_name,
                max_tokens=4096,
                temperature=0.1,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image",
                                "source": {
                                    "type": "base64",
                                    "media_type": media_type,
                                    "data": b64_image,
                                },
                            },
                            {
                                "type": "text",
                                "text": prompt,
                            },
                        ],
                    }
                ],
            )
        except Exception as exc:
            logger.error(f"Claude API error ({model_name}): {exc}")
            raise RuntimeError(f"Claude API call failed: {str(exc)}") from exc

        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        # Extract text content
        raw_text = ""
        for block in response.content:
            if getattr(block, "type", "") == "text":
                raw_text += block.text

        cleaned_json_text = clean_json_response(raw_text)

        try:
            parsed_data = json.loads(cleaned_json_text)
        except json.JSONDecodeError as exc:
            logger.error(f"Failed to parse JSON from Claude output: {raw_text}")
            raise ValueError(
                f"Claude returned invalid JSON structure: {str(exc)}"
            ) from exc

        usage = TokenUsage()
        if hasattr(response, "usage") and response.usage:
            usage.prompt_tokens = getattr(response.usage, "input_tokens", 0) or 0
            usage.completion_tokens = getattr(response.usage, "output_tokens", 0) or 0
            usage.total_tokens = usage.prompt_tokens + usage.completion_tokens

        return ProviderResult(
            provider=self.name,
            model=model_name,
            raw_text=raw_text,
            parsed_json=parsed_data,
            usage=usage,
            latency_ms=elapsed_ms,
        )
