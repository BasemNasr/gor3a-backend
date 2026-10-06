from typing import Dict, Optional, Tuple
from ..models.prescription import CostEstimate, TokenUsage

# Pricing per 1,000,000 tokens (USD)
# Format: model_key: (input_price_per_1M, output_price_per_1M)
MODEL_PRICING: Dict[str, Tuple[float, float]] = {
    # Gemini Models
    "gemini-2.5-flash": (0.075, 0.30),
    "gemini-2.0-flash": (0.10, 0.40),
    "gemini-2.0-flash-exp": (0.00, 0.00),
    "gemini-1.5-flash": (0.075, 0.30),
    "gemini-1.5-pro": (1.25, 5.00),
    # OpenAI Models
    "gpt-4o": (2.50, 10.00),
    "gpt-4o-mini": (0.15, 0.60),
    "gpt-4-turbo": (10.00, 30.00),
    "o3-mini": (1.10, 4.40),
    # Anthropic Claude Models
    "claude-3-5-sonnet": (3.00, 15.00),
    "claude-3-5-haiku": (0.80, 4.00),
    "claude-3-opus": (15.00, 75.00),
}


def estimate_cost(
    model: str,
    prompt_tokens: int,
    completion_tokens: int
) -> Optional[CostEstimate]:
    """Calculate the estimated USD cost for a given model and token usage."""
    normalized_model = model.lower()

    # Look up direct match or partial match
    matched_pricing = None
    for key, pricing in MODEL_PRICING.items():
        if key in normalized_model or normalized_model in key:
            matched_pricing = pricing
            break

    if not matched_pricing:
        return None

    in_price_per_m, out_price_per_m = matched_pricing
    prompt_cost = (prompt_tokens / 1_000_000.0) * in_price_per_m
    completion_cost = (completion_tokens / 1_000_000.0) * out_price_per_m
    total_cost = prompt_cost + completion_cost

    return CostEstimate(
        prompt_cost_usd=round(prompt_cost, 6),
        completion_cost_usd=round(completion_cost, 6),
        total_cost_usd=round(total_cost, 6),
        currency="USD"
    )
