import base64
import json
import logging
import re
import time
try:
    from openai import AsyncOpenAI
except ImportError:
    AsyncOpenAI = None


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


class OpenAIProvider(VisionProvider):
    """Vision provider implementation using OpenAI API (via AsyncOpenAI)."""

    def __init__(self, api_key: Optional[str] = None):
        self._api_key = api_key or settings.openai_api_key
        self._client: Optional[AsyncOpenAI] = None
        if self._api_key:
            try:
                self._client = AsyncOpenAI(api_key=self._api_key)
            except Exception as e:
                logger.warning(f"Failed to initialize OpenAI client: {e}")

    @property
    def name(self) -> str:
        return "openai"

    @property
    def display_name(self) -> str:
        return "OpenAI"

    @property
    def supported_models(self) -> List[str]:
        return [
            "gpt-4o",
            "gpt-4o-mini",
        ]

    @property
    def default_model(self) -> str:
        return "gpt-4o"

    def is_configured(self) -> bool:
        if AsyncOpenAI is None:
            return False
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
                "OpenAI API key is not configured. "
                "Please set OPENAI_API_KEY in your .env file or environment."
            )

        model_name = model or self.default_model

        if not self._client:
            self._client = AsyncOpenAI(api_key=self._api_key)

        start_time = time.perf_counter()

        b64_image = base64.b64encode(image_bytes).decode("utf-8")
        image_data_uri = f"data:{mime_type};base64,{b64_image}"

        try:
            response = await self._client.chat.completions.create(
                model=model_name,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": image_data_uri,
                                    "detail": "high",
                                },
                            },
                        ],
                    }
                ],
                response_format={"type": "json_object"},
                temperature=0.1,  # Low temperature to minimize hallucination
            )
        except Exception as exc:
            logger.error(f"OpenAI API error ({model_name}): {exc}")
            raise RuntimeError(f"OpenAI API call failed: {str(exc)}") from exc

        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        raw_text = response.choices[0].message.content or ""
        cleaned_json_text = clean_json_response(raw_text)

        try:
            parsed_data = json.loads(cleaned_json_text)
        except json.JSONDecodeError as exc:
            logger.error(f"Failed to parse JSON from OpenAI output: {raw_text}")
            raise ValueError(
                f"OpenAI returned invalid JSON structure: {str(exc)}"
            ) from exc

        usage = TokenUsage()
        if response.usage:
            usage.prompt_tokens = response.usage.prompt_tokens or 0
            usage.completion_tokens = response.usage.completion_tokens or 0
            usage.total_tokens = response.usage.total_tokens or (
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
