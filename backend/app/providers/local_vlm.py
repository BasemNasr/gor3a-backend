import base64
import json
import logging
import re
import time
from typing import Any, Dict, List, Optional
try:
    from openai import AsyncOpenAI
except ImportError:
    AsyncOpenAI = None



from ..config import settings
from ..models.prescription import TokenUsage
from .base import ProviderResult, VisionProvider

logger = logging.getLogger(__name__)


def clean_json_response(text: str) -> str:
    """Extract clean JSON string by removing markdown code blocks if present, with resilient repair."""
    text = text.strip()
    match = re.search(r"```(?:json)?\s*([\s\S]*?)(?:```|$)", text)
    if match:
        extracted = match.group(1).strip()
        if extracted:
            return extracted
    first_brace = text.find("{")
    last_brace = text.rfind("}")
    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        return text[first_brace : last_brace + 1].strip()
    return text


def try_repair_truncated_json(text: str) -> Optional[Dict[str, Any]]:
    """Attempts to repair slightly truncated JSON from small Vision models."""
    clean = text.strip()
    first_b = clean.find("{")
    if first_b == -1:
        return None
    clean = clean[first_b:]

    for suffix in ["}", "]}", "]}", "null]}", "null}]}", "\"\"]}}"]:
        try:
            return json.loads(clean + suffix)
        except Exception:
            continue
    return None


class LocalVLMProvider(VisionProvider):
    """
    Vision provider for local open-source Vision-Language Models (Qwen2.5-VL, LLaVA, etc.)
    served via Ollama, vLLM, LM Studio, or any OpenAI-compatible local API server.
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        default_model: Optional[str] = None,
    ):
        self._base_url = base_url or settings.local_vlm_url
        self._api_key = api_key or settings.local_vlm_api_key or "local"
        self._default_model = default_model or settings.local_vlm_model
        self._client: Optional[AsyncOpenAI] = None

    @property
    def name(self) -> str:
        return "local_vlm"

    @property
    def display_name(self) -> str:
        return "Local Open-Source VLM (Ollama / vLLM / Qwen2.5-VL)"

    @property
    def supported_models(self) -> List[str]:
        return [
            "gor3a-rx",
            "qwen2.5vl:3b",
            "qwen2.5vl:7b",
            "qwen2.5-vl:7b",
            "qwen2.5-vl:3b",
            "llama3.2-vision:11b",
            "egyptian-rx-qwen-lora",
        ]

    @property
    def default_model(self) -> str:
        return self._default_model

    def is_configured(self) -> bool:
        # Local provider is always enabled for local discovery
        return bool(self._base_url)

    async def analyze(
        self,
        image_bytes: bytes,
        mime_type: str,
        prompt: str,
        model: Optional[str] = None,
    ) -> ProviderResult:
        model_name = model or self.default_model

        # Normalize model names for Ollama library tags
        if model_name.startswith("qwen2.5-vl"):
            model_name = model_name.replace("qwen2.5-vl", "qwen2.5vl")

        start_time = time.perf_counter()
        b64_image = base64.b64encode(image_bytes).decode("utf-8")
        is_ollama = "11434" in self._base_url or "ollama" in self._base_url.lower()

        if is_ollama:
            import httpx
            ollama_url = self._base_url.rstrip("/")
            if ollama_url.endswith("/v1"):
                ollama_url = ollama_url[:-3]
            ollama_chat_url = f"{ollama_url}/api/chat"

            payload = {
                "model": model_name,
                "messages": [
                    {
                        "role": "user",
                        "content": prompt,
                        "images": [b64_image],
                    }
                ],
                "stream": False,
                "options": {
                    "num_ctx": 16384,
                    "num_predict": 2048,
                    "temperature": 0.0,
                },
            }

            try:
                async with httpx.AsyncClient(timeout=120.0) as client:
                    resp = await client.post(ollama_chat_url, json=payload)
                    resp.raise_for_status()
                    data = resp.json()
                    raw_content = data.get("message", {}).get("content", "")
                    prompt_tokens = data.get("prompt_eval_count", 0) or 0
                    completion_tokens = data.get("eval_count", 0) or 0
                    total_tokens = prompt_tokens + completion_tokens
            except Exception as exc:
                logger.error(f"Local Ollama inference failed: {exc}")
                raise RuntimeError(
                    f"Could not connect to local VLM server at '{self._base_url}'. "
                    f"Please ensure your local inference engine is running (e.g. `ollama serve` and `ollama pull {model_name}`). "
                    f"Error details: {str(exc)}"
                ) from exc
        else:
            if not self._client:
                self._client = AsyncOpenAI(
                    base_url=self._base_url,
                    api_key=self._api_key,
                )

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
                                    "image_url": {"url": image_data_uri},
                                },
                            ],
                        }
                    ],
                    temperature=0.0,
                    max_tokens=2048,
                )
                raw_content = response.choices[0].message.content or "{}"
                prompt_tokens = response.usage.prompt_tokens if response.usage else 0
                completion_tokens = response.usage.completion_tokens if response.usage else 0
                total_tokens = response.usage.total_tokens if response.usage else 0
            except Exception as exc:
                logger.error(f"Local VLM inference failed: {exc}")
                raise RuntimeError(
                    f"Could not connect to local VLM server at '{self._base_url}'. "
                    f"Please ensure your local inference engine is running (e.g. `ollama serve` and `ollama pull {model_name}`, "
                    f"or `vllm serve Qwen/{model_name}`). Error details: {str(exc)}"
                ) from exc

        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        cleaned = clean_json_response(raw_content)

        try:
            parsed = json.loads(cleaned)
        except json.JSONDecodeError as err:
            logger.warning(f"Local VLM JSON parse error, attempting repair: {str(err)}")
            repaired = try_repair_truncated_json(cleaned)
            if repaired:
                parsed = repaired
            else:
                logger.warning(f"Local VLM returned non-JSON text: {raw_content[:200]}")
                parsed = {
                    "prescription": {},
                    "medications": [],
                    "uncertain_items": [f"Local model output was not valid JSON: {str(err)}"],
                    "overall_confidence": 0.0,
                }

        usage = TokenUsage(
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
        )

        return ProviderResult(
            provider=self.name,
            model=model_name,
            raw_text=raw_content,
            parsed_json=parsed,
            usage=usage,
            latency_ms=elapsed_ms,
        )
