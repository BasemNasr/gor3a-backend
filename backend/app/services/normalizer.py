import logging
from typing import Any, Dict, List, Optional, Tuple
from pydantic import BaseModel, Field

from ..benchmark.metrics import (
    KNOWN_CRITICAL_LASA_PAIRS,
    is_critical_medication_error,
    normalize_text,
    string_similarity,
)
from ..drugs.models import Drug
from ..drugs.repository import DrugRepository, drug_repository

logger = logging.getLogger(__name__)


class NormalizedDrugCandidate(BaseModel):
    """Candidate Egyptian drug matched from the Egyptian Drug Authority database."""
    drug_id: str
    name: str
    commercial_name_ar: Optional[str] = None
    generic_name: str
    active_ingredients: List[str] = Field(default_factory=list)
    dosage_form: str
    registered_strength: Optional[str] = None
    manufacturer: Optional[str] = None
    score: float = Field(..., ge=0.0, le=1.0)
    match_type: str  # "exact", "alias", "fuzzy_phonetic", "partial"
    safety_warning: Optional[str] = None


class DrugNormalizationResult(BaseModel):
    """Result of normalizing raw AI extraction against the Egyptian Drug Database."""
    original_text: str
    confirmed_drug: Optional[NormalizedDrugCandidate] = None
    top_candidates: List[NormalizedDrugCandidate] = Field(default_factory=list)
    calibrated_confidence: float = Field(..., ge=0.0, le=1.0)
    status: str  # "identified", "uncertain", "unreadable"
    uncertainty_reasons: List[str] = Field(default_factory=list)


class DrugNormalizer:
    """
    Normalizes raw OCR/Vision medication extractions against the official
    Egyptian Drug Authority (EDA) pharmaceutical database.
    Strictly preserves uncertainty and flags Look-Alike / Sound-Alike risks.
    """

    def __init__(self, repo: Optional[DrugRepository] = None):
        self.repo = repo or drug_repository

    def normalize(
        self,
        raw_text: str,
        predicted_name: Optional[str] = None,
        extracted_strength: Optional[str] = None,
        initial_confidence: float = 0.5,
    ) -> DrugNormalizationResult:
        """
        Normalizes a medication line or predicted name against registered Egyptian drugs.
        """
        query = (predicted_name or raw_text or "").strip()
        if not query or len(query) < 2:
            return DrugNormalizationResult(
                original_text=raw_text,
                confirmed_drug=None,
                top_candidates=[],
                calibrated_confidence=0.1,
                status="unreadable",
                uncertainty_reasons=["Text is empty or too short to identify any registered drug."],
            )

        # Retrieve top candidates from repository
        scored_drugs: List[Tuple[Drug, float]] = self.repo.search_candidates(query, limit=5)

        candidates: List[NormalizedDrugCandidate] = []
        for drug, sim_score in scored_drugs:
            # Check match type
            norm_q = normalize_text(query)
            if norm_q == normalize_text(drug.commercial_name_en) or (drug.commercial_name_ar and norm_q == normalize_text(drug.commercial_name_ar)):
                match_type = "exact"
            elif any(norm_q == normalize_text(al) for al in drug.aliases):
                match_type = "alias"
            elif sim_score >= 0.85:
                match_type = "fuzzy_phonetic"
            else:
                match_type = "partial"

            # Check for critical LASA warning
            warning = None
            for pair in KNOWN_CRITICAL_LASA_PAIRS:
                norm_en = normalize_text(drug.commercial_name_en)
                if norm_en in pair:
                    other_drug = [d for d in pair if d != norm_en][0]
                    warning = (
                        f"Look-Alike / Sound-Alike (LASA) Warning: High clinical risk of confusion with '{other_drug.title()}'. "
                        "Carefully inspect prescription image before confirming."
                    )
                    break

            cand = NormalizedDrugCandidate(
                drug_id=drug.id,
                name=drug.commercial_name_en,
                commercial_name_ar=drug.commercial_name_ar,
                generic_name=drug.generic_name,
                active_ingredients=drug.active_ingredients,
                dosage_form=drug.dosage_form,
                registered_strength=drug.strength,
                manufacturer=drug.manufacturer,
                score=round(sim_score, 2),
                match_type=match_type,
                safety_warning=warning,
            )
            candidates.append(cand)

        # Clinical Decision Rules & Uncertainty Preservation
        if not candidates:
            return DrugNormalizationResult(
                original_text=raw_text,
                confirmed_drug=None,
                top_candidates=[],
                calibrated_confidence=0.2,
                status="unreadable",
                uncertainty_reasons=[f"No registered Egyptian medication found matching '{query}'."],
            )

        top_cand = candidates[0]
        reasons: List[str] = []

        # If top match is high similarity (>= 0.88 or exact/alias) AND vision reading confidence >= 0.70
        if top_cand.score >= 0.88 and initial_confidence >= 0.70:
            # Strength consistency check
            strength_bonus = 0.0
            if extracted_strength and top_cand.registered_strength:
                norm_ext_str = normalize_text(extracted_strength)
                norm_reg_str = normalize_text(top_cand.registered_strength)
                if norm_ext_str and (norm_ext_str in norm_reg_str or norm_reg_str in norm_ext_str):
                    strength_bonus = 0.05
                else:
                    reasons.append(
                        f"Extracted strength '{extracted_strength}' differs from registered strength '{top_cand.registered_strength}'."
                    )

            final_conf = min(0.98, max(top_cand.score, initial_confidence) + strength_bonus)
            status = "identified"
            confirmed = top_cand

        elif top_cand.score >= 0.65 or (top_cand.score >= 0.88 and initial_confidence < 0.70):
            # Ambiguous / Fuzzy match OR low vision confidence: DO NOT auto-confirm per safety protocol
            final_conf = round(min(top_cand.score, initial_confidence or 0.5) * 0.9, 2)
            status = "uncertain"
            confirmed = None  # Do not confirm; present candidates only
            if initial_confidence < 0.70:
                reasons.append(
                    f"Vision reading confidence ({int(initial_confidence * 100)}%) is below clinical safety threshold (70%). Preserved as uncertain."
                )
            else:
                reasons.append(
                    f"Fuzzy match '{top_cand.name}' (score {top_cand.score}) not high enough for clinical certainty. Preserved as uncertain."
                )
        else:
            final_conf = 0.25
            status = "unreadable"
            confirmed = None
            reasons.append(f"Handwriting '{query}' is too ambiguous to identify safely.")

        if top_cand.safety_warning:
            reasons.append(top_cand.safety_warning)

        return DrugNormalizationResult(
            original_text=raw_text,
            confirmed_drug=confirmed,
            top_candidates=candidates,
            calibrated_confidence=round(final_conf, 2),
            status=status,
            uncertainty_reasons=reasons,
        )


# Global singleton instance
drug_normalizer = DrugNormalizer()
