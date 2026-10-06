import logging
from typing import Optional

from ..models.prescription import AnalysisResponse, MedicationItem, PrescriptionAnalysis
from .analyzer import PrescriptionAnalyzer
from .normalizer import DrugNormalizer, drug_normalizer

logger = logging.getLogger(__name__)


class PrescriptionPipelineV2:
    """
    AI Pipeline V2:
    Prescription Image
         ↓
    Image Quality & Preprocessing Check
         ↓
    Vision Model (OCR & Entity Extraction)
         ↓
    Raw Prescription Extraction
         ↓
    Egyptian Drug Database (EDA) Lookup
         ↓
    Drug Normalization & Candidate Ranking
         ↓
    Strength & Formulation Reconciliation
         ↓
    Confidence Recalibration & LASA Safety Verification
         ↓
    Final Structured Prescription
    """

    def __init__(
        self,
        analyzer: Optional[PrescriptionAnalyzer] = None,
        normalizer: Optional[DrugNormalizer] = None
    ):
        self.analyzer = analyzer or PrescriptionAnalyzer()
        self.normalizer = normalizer or drug_normalizer

    async def process_prescription(
        self,
        image_bytes: bytes,
        filename: str = "prescription.jpg",
        mime_type: str = "image/jpeg",
        provider_name: Optional[str] = None,
        model_name: Optional[str] = None,
        custom_prompt: Optional[str] = None,
    ) -> AnalysisResponse:
        """
        Executes the end-to-end V2 pipeline combining multimodal vision with
        the official Egyptian Drug Database.
        """
        # Step 1 & 2: Raw Vision Extraction via Analyzer
        v1_response: AnalysisResponse = await self.analyzer.analyze_image(
            image_bytes=image_bytes,
            filename=filename,
            mime_type=mime_type,
            provider_name=provider_name,
            model_name=model_name,
            custom_prompt=custom_prompt,
        )

        analysis = v1_response.analysis
        v2_medications = []
        v2_uncertain_items = list(analysis.uncertain_items)

        # Step 3, 4 & 5: Normalize every medication through the Egyptian Drug Authority Database
        for med in analysis.medications:
            norm_result = self.normalizer.normalize(
                raw_text=med.raw_text,
                predicted_name=med.name,
                extracted_strength=med.strength,
                initial_confidence=med.confidence,
            )

            # Build candidate list for UI and API inspection
            candidate_dicts = [
                {
                    "drug_id": c.drug_id,
                    "name": c.name,
                    "commercial_name_ar": c.commercial_name_ar,
                    "generic_name": c.generic_name,
                    "dosage_form": c.dosage_form,
                    "strength": c.registered_strength,
                    "score": c.score,
                    "match_type": c.match_type,
                    "warning": c.safety_warning,
                }
                for c in norm_result.top_candidates
            ]

            confirmed = norm_result.confirmed_drug
            if confirmed:
                # Use normalized official EDA name and active ingredients
                v2_name = confirmed.name
                v2_active = ", ".join(confirmed.active_ingredients) if confirmed.active_ingredients else confirmed.generic_name
                v2_eda_id = confirmed.drug_id
                v2_lasa = confirmed.safety_warning
                v2_status = "identified"
            else:
                # Ambiguous or unreadable per safety rules
                v2_name = med.name if norm_result.status == "uncertain" else None
                v2_active = med.active_ingredient if norm_result.status == "uncertain" else None
                v2_eda_id = None
                v2_lasa = norm_result.top_candidates[0].safety_warning if norm_result.top_candidates else None
                v2_status = norm_result.status

            # Append LASA warnings and uncertainty notices to uncertain_items
            for reason in norm_result.uncertainty_reasons:
                if reason not in v2_uncertain_items:
                    v2_uncertain_items.append(f"[{med.raw_text[:20]}...] {reason}")

            v2_med = MedicationItem(
                raw_text=med.raw_text,
                name=v2_name,
                active_ingredient=v2_active,
                strength=med.strength,
                dose=med.dose,
                frequency=med.frequency,
                duration=med.duration,
                instructions=med.instructions,
                confidence=norm_result.calibrated_confidence,
                status=v2_status,
                candidates=candidate_dicts,
                eda_id=v2_eda_id,
                lasa_warning=v2_lasa,
                pipeline_version="v2",
            )
            v2_medications.append(v2_med)

        # Recalibrate overall confidence
        if v2_medications:
            v2_overall_conf = sum(m.confidence for m in v2_medications) / len(v2_medications)
        else:
            v2_overall_conf = analysis.overall_confidence

        v2_analysis = PrescriptionAnalysis(
            prescription=analysis.prescription,
            medications=v2_medications,
            uncertain_items=v2_uncertain_items,
            overall_confidence=round(v2_overall_conf, 2),
        )

        return AnalysisResponse(
            analysis=v2_analysis,
            metadata=v1_response.metadata,
        )


pipeline_v2 = PrescriptionPipelineV2()
