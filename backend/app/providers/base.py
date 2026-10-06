from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional
from pydantic import BaseModel
from ..models.prescription import TokenUsage


class ProviderResult(BaseModel):
    """Raw result returned by an AI vision provider."""
    provider: str
    model: str
    raw_text: str
    parsed_json: Dict[str, Any]
    usage: TokenUsage
    latency_ms: int


class VisionProvider(ABC):
    """Abstract base class for all AI Vision providers."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Provider identifier (e.g. 'gemini', 'openai')."""
        pass

    @property
    @abstractmethod
    def display_name(self) -> str:
        """Human-readable provider name."""
        pass

    @property
    @abstractmethod
    def supported_models(self) -> List[str]:
        """List of model identifiers supported by this provider."""
        pass

    @property
    @abstractmethod
    def default_model(self) -> str:
        """Default model identifier."""
        pass

    @abstractmethod
    def is_configured(self) -> bool:
        """Returns True if the required API credentials are present."""
        pass

    @abstractmethod
    async def analyze(
        self,
        image_bytes: bytes,
        mime_type: str,
        prompt: str,
        model: Optional[str] = None,
    ) -> ProviderResult:
        """
        Executes vision analysis on the given image bytes using the provided prompt.
        Must return structured ProviderResult.
        """
        pass
