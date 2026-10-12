import logging
import re
from pathlib import Path
from typing import Any, Dict, Optional

from ..config import settings
from ..drugs.clinical_knowledge import (
    get_clinical_knowledge,
    infer_prescription_overall_diagnosis,
)
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
from ..utils.text import clean_clinical_field

logger = logging.getLogger(__name__)


def _sanitize_patient_data(data: Dict[str, Any]) -> PatientInfo:
    """Safely extracts and converts patient info from AI JSON output."""
    raw_age = clean_clinical_field(data.get("patient_age"))
    age_str = raw_age if raw_age is not None else None

    raw_weight = data.get("patient_weight_kg")
    weight_val: Optional[float] = None
    if raw_weight is not None:
        if isinstance(raw_weight, (int, float)):
            weight_val = float(raw_weight) if raw_weight > 0 else None
        elif isinstance(raw_weight, str):
            clean_w = clean_clinical_field(raw_weight)
            if clean_w:
                # Try to extract numbers (e.g. "8.14 kg" -> 8.14)
                match = re.search(r"(\d+(?:\.\d+)?)", clean_w)
                if match:
                    try:
                        w_parsed = float(match.group(1))
                        weight_val = w_parsed if w_parsed > 0 else None
                    except ValueError:
                        weight_val = None

    return PatientInfo(
        patient_age=age_str,
        patient_weight_kg=weight_val
    )


def _sanitize_medication(item: Dict[str, Any]) -> MedicationItem:
    """Validates and sanitizes a single medication dictionary."""
    clean_raw = clean_clinical_field(item.get("raw_text"))
    clean_name = clean_clinical_field(item.get("name"))
    raw_text = clean_raw or clean_name or "Unspecified handwriting"

    name = clean_name
    active_ingredient = clean_clinical_field(item.get("active_ingredient"))
    strength = clean_clinical_field(item.get("strength"))
    dose = clean_clinical_field(item.get("dose"))
    frequency = clean_clinical_field(item.get("frequency"))
    duration = clean_clinical_field(item.get("duration"))
    instructions = clean_clinical_field(item.get("instructions"))

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

    clinical_profile = get_clinical_knowledge(
        drug_name=name,
        generic_name=active_ingredient,
        drug_class=None,
        dosage_form=None,
        eda_id=None,
    )

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
        clinical_indication=clinical_profile.get("indication_short"),
        likely_diagnosis=clinical_profile.get("likely_diagnosis"),
        indication_details=clinical_profile,
        image_url=clinical_profile.get("image_url"),
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
        provider = get_provider(provider_name)

        if not provider.is_configured():
            # Try auto-fallback to any configured provider
            provider = get_provider(None)

        if not provider.is_configured():
            raise RuntimeError(
                f"No active vision AI provider configured. Please configure an API key or run a local VLM."
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
        prompt_templates = (
            "faded ink",
            "medication #3 name is illegible",
            "medication #2 could not be verified",
            "list of human-readable statements",
        )
        uncertain_items = [
            str(u).strip()
            for u in raw_uncertain
            if clean_clinical_field(u) and not any(pt in str(u).lower() for pt in prompt_templates)
        ]

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

        # Dietary, lifestyle, and herbal advice extraction
        raw_dietary = clean_clinical_field(
            raw_json.get("dietary_and_herbal_advice")
            or raw_json.get("herbal_advice")
            or raw_json.get("non_pharmacological_instructions")
            or raw_json.get("lifestyle_advice")
        )
        if raw_dietary:
            lower_d = raw_dietary.lower().strip()
            prompt_boilerplate = ("transcribe", "look for", "e.g.", "if absent", "if none", "verbatim")
            negative_answers = ("لا", "لا توجد", "لايوجد", "لا يوجد", "غير موجود", "غير متوفر", "none", "n/a", "no")
            if lower_d in negative_answers or any(bp in lower_d for bp in prompt_boilerplate):
                raw_dietary = None

        overall_diag = infer_prescription_overall_diagnosis([m.model_dump() for m in medication_items])

        analysis = PrescriptionAnalysis(
            prescription=patient_info,
            medications=medication_items,
            uncertain_items=uncertain_items,
            dietary_and_herbal_advice=raw_dietary,
            overall_likely_diagnosis=overall_diag,
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
