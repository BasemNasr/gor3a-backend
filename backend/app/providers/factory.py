from .base import VisionProvider
from .claude import ClaudeProvider
from .gemini import GeminiProvider
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
        "mock": MockProvider(),
    }
    return _PROVIDER_REGISTRY


def get_provider(name: Optional[str] = None) -> VisionProvider:
    """
    Retrieve a vision provider instance by identifier.
    Falls back to 'gemini' if name is omitted or empty.
    """
    global _PROVIDER_REGISTRY
    if not _PROVIDER_REGISTRY:
        init_providers()

    provider_key = (name or "gemini").lower().strip()
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
