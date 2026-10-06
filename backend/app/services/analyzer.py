import logging
import re
from pathlib import Path
from typing import Any, Dict, Optional

from ..config import settings
from ..models.prescription import (
    AnalysisMetadata,
    AnalysisResponse,
    ImageInfo,
    MedicationItem,
    PatientInfo,
    PrescriptionAnalysis,
)
from ..providers.base import ProviderResult
from ..providers.factory import get_provider
from ..utils.cost import estimate_cost
from ..utils.image import validate_and_inspect_image

logger = logging.getLogger(__name__)


def _sanitize_patient_data(data: Dict[str, Any]) -> PatientInfo:
    """Safely extracts and converts patient info from AI JSON output."""
    raw_age = data.get("patient_age")
    age_str = str(raw_age).strip() if raw_age is not None else None

    raw_weight = data.get("patient_weight_kg")
    weight_val: Optional[float] = None
    if raw_weight is not None:
        if isinstance(raw_weight, (int, float)):
            weight_val = float(raw_weight)
        elif isinstance(raw_weight, str):
            # Try to extract numbers (e.g. "8.14 kg" -> 8.14)
            match = re.search(r"(\d+(?:\.\d+)?)", raw_weight)
            if match:
                try:
                    weight_val = float(match.group(1))
                except ValueError:
                    weight_val = None

    return PatientInfo(
        patient_age=age_str,
        patient_weight_kg=weight_val
    )


def _sanitize_medication(item: Dict[str, Any]) -> MedicationItem:
    """Validates and sanitizes a single medication dictionary."""
    raw_text = str(item.get("raw_text") or item.get("name") or "Unspecified handwriting").strip()
    name = str(item["name"]).strip() if item.get("name") else None
    active_ingredient = (
        str(item["active_ingredient"]).strip()
        if item.get("active_ingredient")
        else None
    )
    strength = str(item["strength"]).strip() if item.get("strength") else None
    dose = str(item["dose"]).strip() if item.get("dose") else None
    frequency = str(item["frequency"]).strip() if item.get("frequency") else None
    duration = str(item["duration"]).strip() if item.get("duration") else None
    instructions = str(item["instructions"]).strip() if item.get("instructions") else None

    # Calibrate confidence score
    raw_conf = item.get("confidence", 0.5)
    try:
        confidence = float(raw_conf)
        confidence = max(0.0, min(1.0, confidence))
    except (ValueError, TypeError):
        confidence = 0.5

    # Validate status enum
    status = str(item.get("status", "")).lower().strip()
    if status not in ("identified", "uncertain", "unreadable"):
        if confidence < 0.6 or not name:
            status = "uncertain"
        else:
            status = "identified"

    return MedicationItem(
        raw_text=raw_text,
        name=name,
        active_ingredient=active_ingredient,
        strength=strength,
        dose=dose,
        frequency=frequency,
        duration=duration,
        instructions=instructions,
        confidence=round(confidence, 2),
        status=status,
    )


class PrescriptionAnalyzer:
    """Core domain service for orchestrating handwritten prescription analysis."""

    def __init__(self, prompt_path: Optional[Path] = None):
        self._prompt_path = prompt_path or settings.resolved_prompt_path
        self._cached_prompt: Optional[str] = None

    def load_prompt(self) -> str:
        """Loads and caches the versioned system prompt."""
        if self._cached_prompt:
            return self._cached_prompt

        if not self._prompt_path.exists():
            raise FileNotFoundError(
                f"Prompt template file not found at {self._prompt_path}. "
                "Ensure prompts/prescription_analysis_v1.txt exists."
            )

        with open(self._prompt_path, "r", encoding="utf-8") as f:
            content = f.read().strip()

        if not content:
            raise ValueError(f"Prompt template at {self._prompt_path} is empty.")

        self._cached_prompt = content
        return self._cached_prompt

    async def analyze_image(
        self,
        image_bytes: bytes,
        filename: str = "prescription.jpg",
        mime_type: str = "image/jpeg",
        provider_name: Optional[str] = None,
        model_name: Optional[str] = None,
        custom_prompt: Optional[str] = None,
    ) -> AnalysisResponse:
        """
        Validates the prescription image, invokes the requested vision provider,
        enforces clinical safety parsing, and packages the complete result.
        """
        # 1. Validate image format and dimensions
        image_info, validated_bytes, resolved_mime = validate_and_inspect_image(
            image_bytes=image_bytes,
            filename=filename,
            mime_type=mime_type,
        )

        # 2. Resolve Vision Provider
        prov_key = provider_name or settings.default_provider
        provider = get_provider(prov_key)

        if not provider.is_configured():
            raise RuntimeError(
                f"Provider '{provider.display_name}' ({provider.name}) is not configured. "
                f"Please set the appropriate API key in your .env file."
            )

        # 3. Load System Prompt
        prompt = custom_prompt or self.load_prompt()
        target_model = model_name or provider.default_model

        # 4. Invoke Provider
        logger.info(
            f"Analyzing prescription '{filename}' with provider={provider.name}, model={target_model}"
        )
        provider_result: ProviderResult = await provider.analyze(
            image_bytes=validated_bytes,
            mime_type=resolved_mime,
            prompt=prompt,
            model=target_model,
        )

        # 5. Parse and sanitize structured data
        raw_json = provider_result.parsed_json or {}

        patient_dict = raw_json.get("prescription") or {}
        patient_info = _sanitize_patient_data(patient_dict)

        raw_meds = raw_json.get("medications") or []
        medication_items = [_sanitize_medication(m) for m in raw_meds]

        raw_uncertain = raw_json.get("uncertain_items") or []
        uncertain_items = [str(u).strip() for u in raw_uncertain if u]

        # Overall confidence calculation
        raw_overall = raw_json.get("overall_confidence")
        if raw_overall is not None:
            try:
                overall_conf = max(0.0, min(1.0, float(raw_overall)))
            except (ValueError, TypeError):
                overall_conf = 0.5
        elif medication_items:
            overall_conf = sum(m.confidence for m in medication_items) / len(medication_items)
        else:
            overall_conf = 0.5

        analysis = PrescriptionAnalysis(
            prescription=patient_info,
            medications=medication_items,
            uncertain_items=uncertain_items,
            overall_confidence=round(overall_conf, 2),
        )

        # 6. Estimate Cost
        cost = estimate_cost(
            model=provider_result.model,
            prompt_tokens=provider_result.usage.prompt_tokens,
            completion_tokens=provider_result.usage.completion_tokens,
        )

        metadata = AnalysisMetadata(
            provider=provider.name,
            model=provider_result.model,
            processing_time_ms=provider_result.latency_ms,
            cost_estimate=cost,
            usage=provider_result.usage,
            image_info=image_info,
            raw_model_output=provider_result.raw_text,
        )

        return AnalysisResponse(
            analysis=analysis,
            metadata=metadata,
        )
