import logging
import re
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
from ..utils.text import clean_clinical_field, extract_drug_brand_stem

logger = logging.getLogger(__name__)

# Common OCR distortions / phonetic misreadings for Egyptian medications
COMMON_OCR_DRUG_ALIASES: Dict[str, str] = {
    # Septazole / Co-trimoxazole variants
    "sejotazal": "Septazole",
    "sejotazole": "Septazole",
    "sedotazol": "Septazole",
    "septazol": "Septazole",
    "septazal": "Septazole",
    "septo": "Septazole",
    # Respipect / Respiratory syrups
    "respicleps": "Respipect",
    "respicleys": "Respipect",
    "respiclens": "Respipect",
    "respiclap": "Respipect",
    "respiklar": "Respipect",
    "respiclear": "Respipect",
    # Limitless Fortalase (Alpha-Amylase) anti-edema
    "himelless fortiless": "Limitless Fortalase",
    "himetless fortiless": "Limitless Fortalase",
    "himetless fortalase": "Limitless Fortalase",
    "himelless fortalase": "Limitless Fortalase",
    "limetless fortiless": "Limitless Fortalase",
    "limitless fortaless": "Limitless Fortalase",
    "limetless fortalase": "Limitless Fortalase",
    "limitless fortalase": "Limitless Fortalase",
    "fortaless": "Limitless Fortalase",
    "fortalase": "Limitless Fortalase",
    # Pediatric Drops & OCR variants
    "cetul": "Cetal",
    "setul": "Cetal",
    "cervilin": "Cevilene",
    "cerviline": "Cevilene",
    "cevilin": "Cevilene",
    "ceviline": "Cevilene",
    "cevarol": "Cevarol",
    "atlabacillus": "Atlabacillus",
    "atlabacill": "Atlabacillus",
    "enrich": "Enrich",
    # Vacation & Pediatric Emergency Kit
    "cough sed": "Coughsed",
    "cough sed spp": "Coughsed",
    "cough seed": "Coughsed",
    "cough seed spp": "Coughsed",
    "cough seed s-pp": "Coughsed",
    "cough sed s-pp": "Coughsed",
    "off spray": "Off!",
    "solo lotion": "Solo Lotion",
    "bringo lotion": "Bringo Lotion",
    "bringo": "Bringo Lotion",
    "fenistil drops": "Fenistil",
}


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
        clean_pred = clean_clinical_field(predicted_name)
        clean_raw = clean_clinical_field(raw_text)
        query = (clean_pred or clean_raw or "").strip()
        if not query or len(query) < 2:
            return DrugNormalizationResult(
                original_text=raw_text or "",
                confirmed_drug=None,
                top_candidates=[],
                calibrated_confidence=0.1,
                status="unreadable",
                uncertainty_reasons=["Text is empty or too short to identify any registered drug."],
            )

        # Retrieve top candidates from repository for primary query
        scored_drugs_dict: Dict[str, Tuple[Drug, float]] = {}

        # Check Egyptian OCR drug aliases
        norm_q = normalize_text(query)
        if norm_q in COMMON_OCR_DRUG_ALIASES:
            canonical_alias = COMMON_OCR_DRUG_ALIASES[norm_q]
            for d, s in self.repo.search_candidates(canonical_alias, limit=5):
                scored_drugs_dict[d.id] = (d, max(s, 0.95))
        elif "fortalase" in norm_q or "fortaless" in norm_q:
            for d, s in self.repo.search_candidates("Limitless Fortalase", limit=5):
                scored_drugs_dict[d.id] = (d, max(s, 0.95))
        elif "septaz" in norm_q or "sejota" in norm_q:
            for d, s in self.repo.search_candidates("Septazole", limit=5):
                scored_drugs_dict[d.id] = (d, max(s, 0.95))


        for d, s in self.repo.search_candidates(query, limit=5):
            if d.id not in scored_drugs_dict or s > scored_drugs_dict[d.id][1]:
                scored_drugs_dict[d.id] = (d, s)

        # Also search using isolated commercial brand stem (e.g. stripping 'Eye Drop' / 'Tab')
        stem = extract_drug_brand_stem(clean_pred) if clean_pred else extract_drug_brand_stem(clean_raw)
        if stem and normalize_text(stem) != normalize_text(query) and len(stem) >= 2:
            norm_stem = normalize_text(stem)
            if norm_stem in COMMON_OCR_DRUG_ALIASES:
                canonical_stem = COMMON_OCR_DRUG_ALIASES[norm_stem]
                for d, s in self.repo.search_candidates(canonical_stem, limit=5):
                    if d.id not in scored_drugs_dict or s > scored_drugs_dict[d.id][1]:
                        scored_drugs_dict[d.id] = (d, max(s, 0.95))
            for d, s in self.repo.search_candidates(stem, limit=5):
                if d.id not in scored_drugs_dict or s > scored_drugs_dict[d.id][1]:
                    scored_drugs_dict[d.id] = (d, s)

        # Liquid / pediatric formulation hint (sm / ml / syrup / suspension)
        is_liquid_query = bool(
            re.search(r"\b(سم|مل|ml|syrup|syr|susp|suspension|شراب|معلق)\b", f"{clean_pred} {clean_raw}", re.IGNORECASE)
        )
        if is_liquid_query:
            for drug_id, (d, s) in list(scored_drugs_dict.items()):
                if d.dosage_form in ("Suspension", "Syrup", "Oral Liquid", "Drops"):
                    scored_drugs_dict[drug_id] = (d, min(1.0, s + 0.15))
                elif d.dosage_form == "Tablet" and s < 0.95:
                    scored_drugs_dict[drug_id] = (d, max(0.1, s - 0.20))

        # Drops formulation hint (drops / drop / قطرة / قطره / نقط / قطارة)
        is_drops_query = bool(
            re.search(r"\b(drops?|قطرة|قطره|نقط|نقطة|قطارة|قطاره)\b", f"{clean_pred} {clean_raw}", re.IGNORECASE)
        )
        if is_drops_query:
            for drug_id, (d, s) in list(scored_drugs_dict.items()):
                d_form = str(d.dosage_form or "").lower()
                if "drop" in d_form:
                    scored_drugs_dict[drug_id] = (d, min(1.0, s + 0.30))
                else:
                    scored_drugs_dict[drug_id] = (d, max(0.10, s - 0.25))

        # Suppository formulation hint (spp / supp / لبوس / تحاميل)
        is_supp_query = bool(
            re.search(r"\b(spp|supp|suppositor(?:y|ies)?|لبوس|تحاميل)\b", f"{clean_pred} {clean_raw}", re.IGNORECASE)
        )
        if is_supp_query:
            for drug_id, (d, s) in list(scored_drugs_dict.items()):
                d_form = str(d.dosage_form or "").lower()
                if "supp" in d_form:
                    scored_drugs_dict[drug_id] = (d, min(1.0, s + 0.35))
                else:
                    scored_drugs_dict[drug_id] = (d, max(0.10, s - 0.35))

        # Topical / Lotion / Cream / Ointment formulation hint
        is_topical_query = bool(
            re.search(r"\b(lotion|lotions?|cream|creams?|ointment|ointments?|gel|topical|دهان|لوشن|كريم|مرهم|مرطب)\b", f"{clean_pred} {clean_raw}", re.IGNORECASE)
        )
        if is_topical_query:
            for drug_id, (d, s) in list(scored_drugs_dict.items()):
                d_form = str(d.dosage_form or "").lower()
                if "topical" in d_form or "lotion" in d_form or "cream" in d_form or "gel" in d_form:
                    scored_drugs_dict[drug_id] = (d, min(1.0, s + 0.30))
                else:
                    scored_drugs_dict[drug_id] = (d, max(0.10, s - 0.30))

        # Spray formulation hint
        is_spray_query = bool(
            re.search(r"\b(sprays?|بخاخ|سبراي|رذاذ)\b", f"{clean_pred} {clean_raw}", re.IGNORECASE)
        )
        if is_spray_query:
            for drug_id, (d, s) in list(scored_drugs_dict.items()):
                d_form = str(d.dosage_form or "").lower()
                if "spray" in d_form or "inhal" in d_form or "topical" in d_form:
                    scored_drugs_dict[drug_id] = (d, min(1.0, s + 0.30))
                else:
                    scored_drugs_dict[drug_id] = (d, max(0.10, s - 0.30))

        scored_drugs = sorted(scored_drugs_dict.values(), key=lambda x: x[1], reverse=True)[:5]


        candidates: List[NormalizedDrugCandidate] = []
        for drug, sim_score in scored_drugs:
            # Check match type against both full query and brand stem
            norm_q = normalize_text(query)
            norm_stem = normalize_text(stem) if stem else ""
            norm_en = normalize_text(drug.commercial_name_en)
            norm_full = normalize_text(drug.full_commercial_name_en) if drug.full_commercial_name_en else ""
            norm_ar = normalize_text(drug.commercial_name_ar) if drug.commercial_name_ar else ""

            is_exact = (
                norm_q == norm_en
                or (norm_stem and norm_stem == norm_en)
                or (norm_ar and (norm_q == norm_ar or (norm_stem and norm_stem == norm_ar)))
                or (norm_full and (norm_q == norm_full or (norm_stem and norm_stem == norm_full)))
            )
            is_alias = any(
                norm_q == normalize_text(al) or (norm_stem and norm_stem == normalize_text(al))
                for al in drug.aliases
            )

            if is_exact:
                match_type = "exact"
            elif is_alias:
                match_type = "alias"
            elif norm_q.split() and norm_en.split() and norm_q.split()[0] == norm_en.split()[0]:
                match_type = "brand_match"
            elif sim_score >= 0.85:
                match_type = "fuzzy_phonetic"
            else:
                match_type = "partial"

            # Check for critical LASA warning
            warning = None
            for pair in KNOWN_CRITICAL_LASA_PAIRS:
                norm_en_raw = normalize_text(drug.commercial_name_en)
                if norm_en_raw in pair:
                    other_drug = [d for d in pair if d != norm_en_raw][0]
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
                original_text=raw_text or "",
                confirmed_drug=None,
                top_candidates=[],
                calibrated_confidence=0.2,
                status="unreadable",
                uncertainty_reasons=[f"No registered Egyptian medication found matching '{query}'."],
            )

        top_cand = candidates[0]
        reasons: List[str] = []

        is_exact_or_alias = top_cand.match_type in ("exact", "alias") or top_cand.score >= 0.95
        is_strong_match = (
            is_exact_or_alias
            or top_cand.score >= 0.85
            or top_cand.match_type == "brand_match"
        )

        can_confirm = is_strong_match and initial_confidence >= 0.70

        if can_confirm:
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

        elif top_cand.score >= 0.50 or (is_strong_match and initial_confidence < 0.70):
            # Ambiguous / Fuzzy match OR low vision confidence: DO NOT auto-confirm per safety protocol
            final_conf = round(min(top_cand.score, initial_confidence or 0.5) * 0.9, 2)
            status = "uncertain"
            confirmed = None  # Do not confirm; present candidates only
            if initial_confidence < 0.70:
                reasons.append(
                    f"Vision reading confidence ({int(initial_confidence * 100)}%) is below clinical confirmation threshold (70%). Preserved as uncertain."
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
