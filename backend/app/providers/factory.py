from typing import Dict, List, Optional
from ..config import settings
from .base import VisionProvider
from .claude import ClaudeProvider
from .gemini import GeminiProvider
from .local_vlm import LocalVLMProvider
from .mock_provider import MockProvider
from .openai_provider import OpenAIProvider


_PROVIDER_REGISTRY: Dict[str, VisionProvider] = {}


def init_providers() -> Dict[str, VisionProvider]:
    """Instantiate and register available vision providers."""
    global _PROVIDER_REGISTRY
    _PROVIDER_REGISTRY = {
        "gemini": GeminiProvider(),
        "openai": OpenAIProvider(),
        "claude": ClaudeProvider(),
        "local_vlm": LocalVLMProvider(),
        "mock": MockProvider(),
    }
    return _PROVIDER_REGISTRY


def register_provider(name: str, provider: VisionProvider) -> None:
    """Register or replace a vision provider instance dynamically."""
    global _PROVIDER_REGISTRY
    if not _PROVIDER_REGISTRY:
        init_providers()
    _PROVIDER_REGISTRY[name.lower().strip()] = provider


def get_provider(name: Optional[str] = None) -> VisionProvider:
    """
    Retrieve a vision provider instance by identifier.
    If name is omitted, smartly selects the configured default provider,
    or falls back to the first active configured provider.
    """
    global _PROVIDER_REGISTRY
    if not _PROVIDER_REGISTRY:
        init_providers()

    if not name:
        def_name = (settings.default_provider or "").lower().strip()
        if def_name in _PROVIDER_REGISTRY and _PROVIDER_REGISTRY[def_name].is_configured():
            return _PROVIDER_REGISTRY[def_name]
        # Auto-fallback to the first configured non-mock provider (e.g. local_vlm or gemini)
        for key, prov in _PROVIDER_REGISTRY.items():
            if prov.is_configured() and key != "mock":
                return prov
        return _PROVIDER_REGISTRY.get("mock", _PROVIDER_REGISTRY["local_vlm"])

    provider_key = name.lower().strip()
    if provider_key not in _PROVIDER_REGISTRY:
        available = ", ".join(_PROVIDER_REGISTRY.keys())
        raise ValueError(
            f"Unsupported vision provider '{provider_key}'. Available providers: {available}"
        )

    return _PROVIDER_REGISTRY[provider_key]


def list_available_providers() -> List[dict]:
    """Returns status information for all registered vision providers."""
    global _PROVIDER_REGISTRY
    if not _PROVIDER_REGISTRY:
        init_providers()

    result = []
    for key, prov in _PROVIDER_REGISTRY.items():
        result.append({
            "id": prov.name,
            "display_name": prov.display_name,
            "is_configured": prov.is_configured(),
            "default_model": prov.default_model,
            "supported_models": prov.supported_models,
        })
    return result
