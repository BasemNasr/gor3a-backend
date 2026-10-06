import math
import re
from typing import Any, Dict, List, Optional, Set, Tuple
from pydantic import BaseModel, Field

# High-risk Egyptian Look-Alike / Sound-Alike (LASA) pairs
KNOWN_CRITICAL_LASA_PAIRS = {
    frozenset(["losec", "lasix"]),
    frozenset(["daonil", "daflon"]),
    frozenset(["norvasc", "navidoxine"]),
    frozenset(["celebrex", "cerebrolysin"]),
    frozenset(["amrizole", "amaryl"]),
    frozenset(["capoten", "cataflam"]),
    frozenset(["zestril", "zyrtec"]),
    frozenset(["aldactone", "aldomet"]),
}


def normalize_text(text: Optional[str]) -> str:
    """Normalizes Arabic/English text for semantic comparison."""
    if not text:
        return ""
    s = text.lower().strip()
    # Normalize Arabic characters
    s = re.sub(r"[أإآ]", "ا", s)
    s = re.sub(r"ة", "ه", s)
    s = re.sub(r"ى", "ي", s)
    s = re.sub(r"[٠-٩]", lambda m: str("٠١٢٣٤٥٦٧٨٩".index(m.group(0))), s)
    # Remove punctuation
    s = re.sub(r"[^\w\s]", "", s)
    # Collapse whitespace
    s = re.sub(r"\s+", " ", s).strip()
    return s


def string_similarity(a: str, b: str) -> float:
    """Calculates Levenshtein-based similarity ratio between 0.0 and 1.0."""
    na = normalize_text(a)
    nb = normalize_text(b)
    if not na and not nb:
        return 1.0
    if not na or not nb:
        return 0.0
    if na == nb:
        return 1.0

    # Simple dynamic programming Levenshtein distance
    len_a, len_b = len(na), len(nb)
    dp = [[0] * (len_b + 1) for _ in range(len_a + 1)]
    for i in range(len_a + 1):
        dp[i][0] = i
    for j in range(len_b + 1):
        dp[0][j] = j

    for i in range(1, len_a + 1):
        for j in range(1, len_b + 1):
            cost = 0 if na[i - 1] == nb[j - 1] else 1
            dp[i][j] = min(
                dp[i - 1][j] + 1,       # deletion
                dp[i][j - 1] + 1,       # insertion
                dp[i - 1][j - 1] + cost  # substitution
            )

    dist = dp[len_a][len_b]
    max_len = max(len_a, len_b)
    return max(0.0, 1.0 - (dist / max_len))


def is_critical_medication_error(ground_truth_name: Optional[str], predicted_name: Optional[str]) -> bool:
    """
    Detects whether a prediction represents a high-risk medication substitution
    (e.g., predicted Lasix when ground truth was Losec, or Daonil when Daflon).
    """
    if not ground_truth_name or not predicted_name:
        return False

    gt = normalize_text(ground_truth_name)
    pred = normalize_text(predicted_name)

    if gt == pred:
        return False

    pair = frozenset([gt, pred])
    if pair in KNOWN_CRITICAL_LASA_PAIRS:
        return True

    return False


class CaseEvaluationMetrics(BaseModel):
    """Detailed evaluation metrics for a single prescription test case."""
    case_id: str
    provider: str
    model: str
    total_ground_truth_medications: int
    total_predicted_medications: int

    # Metric counts (correct / total evaluated)
    medication_name_matches: int = 0
    strength_matches: int = 0
    dose_matches: int = 0
    frequency_matches: int = 0
    duration_matches: int = 0

    patient_age_correct: bool = False
    patient_weight_correct: bool = False

    uncertainty_true_positives: int = 0
    uncertainty_total_expected: int = 0

    false_confidence_count: int = 0
    critical_errors_count: int = 0

    latency_ms: int = 0
    estimated_cost_usd: float = 0.0


class ModelBenchmarkSummary(BaseModel):
    """Aggregated benchmark metrics across the entire dataset for a specific model."""
    provider: str
    model: str
    total_prescriptions_evaluated: int

    # Accuracy Rates (0.0 to 1.0)
    medication_name_accuracy: float = 0.0
    top3_medication_accuracy: float = 0.0
    strength_accuracy: float = 0.0
    dose_accuracy: float = 0.0
    frequency_accuracy: float = 0.0
    duration_accuracy: float = 0.0
    patient_age_accuracy: float = 0.0
    patient_weight_accuracy: float = 0.0

    # Critical Safety Rates
    uncertainty_detection_accuracy: float = 0.0
    false_confidence_rate: float = 0.0
    critical_medication_error_rate: float = 0.0

    # Operational Performance
    mean_latency_ms: float = 0.0
    total_cost_usd: float = 0.0
    mean_cost_per_rx_usd: float = 0.0


def evaluate_case(
    case_id: str,
    ground_truth: Dict[str, Any],
    prediction: Dict[str, Any],
    provider: str,
    model: str,
    latency_ms: int = 0,
    cost_usd: float = 0.0
) -> CaseEvaluationMetrics:
    """Evaluates a single prescription model prediction against ground truth."""
    gt_rx = ground_truth.get("prescription", {})
    pred_rx = prediction.get("prescription", {})

    gt_meds = ground_truth.get("medications", [])
    pred_meds = prediction.get("medications", [])

    metrics = CaseEvaluationMetrics(
        case_id=case_id,
        provider=provider,
        model=model,
        total_ground_truth_medications=len(gt_meds),
        total_predicted_medications=len(pred_meds),
        latency_ms=latency_ms,
        estimated_cost_usd=cost_usd,
    )

    # 1. Patient Age & Weight
    gt_age = normalize_text(gt_rx.get("patient_age"))
    pred_age = normalize_text(pred_rx.get("patient_age"))
    metrics.patient_age_correct = bool(gt_age and pred_age and (gt_age in pred_age or pred_age in gt_age or string_similarity(gt_age, pred_age) > 0.8))

    gt_wt = gt_rx.get("patient_weight_kg")
    pred_wt = pred_rx.get("patient_weight_kg")
    if gt_wt is not None and pred_wt is not None:
        metrics.patient_weight_correct = abs(float(gt_wt) - float(pred_wt)) <= 0.25
    elif gt_wt is None and pred_wt is None:
        metrics.patient_weight_correct = True
    else:
        metrics.patient_weight_correct = False

    # 2. Medication matching
    # Map ground truth medications to best matching predicted medications
    matched_pred_indices: Set[int] = set()

    for gt_item in gt_meds:
        gt_name = gt_item.get("name")
        gt_status = gt_item.get("status", "identified")
        gt_is_uncertain = (gt_status in ("uncertain", "unreadable")) or (gt_name is None)

        if gt_is_uncertain:
            metrics.uncertainty_total_expected += 1

        best_pred_idx = None
        best_sim = 0.0

        for p_idx, pred_item in enumerate(pred_meds):
            if p_idx in matched_pred_indices:
                continue
            pred_name = pred_item.get("name")
            sim = string_similarity(gt_name or "", pred_name or "")

            # Prioritize matching known critical LASA pairs (e.g., Losec vs Lasix)
            if is_critical_medication_error(gt_name, pred_name):
                sim = 0.99

            if sim > best_sim:
                best_sim = sim
                best_pred_idx = p_idx

        # Check if matched or slot aligns
        is_lasa = (
            best_pred_idx is not None
            and is_critical_medication_error(gt_name, pred_meds[best_pred_idx].get("name"))
        )

        if best_pred_idx is not None and (best_sim >= 0.70 or is_lasa):
            matched_pred_indices.add(best_pred_idx)
            matched_pred = pred_meds[best_pred_idx]
            pred_name = matched_pred.get("name")
            pred_status = matched_pred.get("status", "identified")
            pred_conf = float(matched_pred.get("confidence", 0.5))

            # Critical LASA error check (e.g., Losec vs Lasix)
            if is_lasa:
                metrics.critical_errors_count += 1
                if pred_conf >= 0.8:
                    metrics.false_confidence_count += 1
            elif best_sim >= 0.85:
                # Genuine medication name match
                metrics.medication_name_matches += 1
            else:
                # Mismatch with high confidence -> false confidence
                if pred_conf >= 0.8:
                    metrics.false_confidence_count += 1

            # Strength
            gt_strength = normalize_text(gt_item.get("strength"))
            pred_strength = normalize_text(matched_pred.get("strength"))
            if gt_strength:
                if string_similarity(gt_strength, pred_strength) >= 0.8:
                    metrics.strength_matches += 1
            elif not pred_strength:
                metrics.strength_matches += 1

            # Dose
            gt_dose = normalize_text(gt_item.get("dose"))
            pred_dose = normalize_text(matched_pred.get("dose"))
            if gt_dose:
                if string_similarity(gt_dose, pred_dose) >= 0.8:
                    metrics.dose_matches += 1
            elif not pred_dose:
                metrics.dose_matches += 1

            # Frequency
            gt_freq = normalize_text(gt_item.get("frequency"))
            pred_freq = normalize_text(matched_pred.get("frequency"))
            if gt_freq:
                if string_similarity(gt_freq, pred_freq) >= 0.75:
                    metrics.frequency_matches += 1
            elif not pred_freq:
                metrics.frequency_matches += 1

            # Duration
            gt_dur = normalize_text(gt_item.get("duration"))
            pred_dur = normalize_text(matched_pred.get("duration"))
            if gt_dur:
                if string_similarity(gt_dur, pred_dur) >= 0.75:
                    metrics.duration_matches += 1
            elif not pred_dur:
                metrics.duration_matches += 1

            # Uncertainty check
            if gt_is_uncertain:
                if pred_status in ("uncertain", "unreadable") or pred_name is None:
                    metrics.uncertainty_true_positives += 1
                elif pred_status == "identified" and pred_conf >= 0.8:
                    # Model hallucinated on an unreadable line
                    metrics.false_confidence_count += 1
        else:
            # Slot did not match an identified medication
            if gt_is_uncertain:
                # If there are unmatched predictions that hallucinated as identified, it failed
                unmatched_identified = [
                    p for i, p in enumerate(pred_meds)
                    if i not in matched_pred_indices and p.get("status") == "identified" and float(p.get("confidence", 0.5)) >= 0.8
                ]
                if not unmatched_identified:
                    metrics.uncertainty_true_positives += 1

    # Check for hallucinated medications (predicted meds not matching any GT)
    for p_idx, pred_item in enumerate(pred_meds):
        if p_idx not in matched_pred_indices:
            pred_conf = float(pred_item.get("confidence", 0.5))
            if pred_conf >= 0.8 and pred_item.get("name"):
                metrics.false_confidence_count += 1

    return metrics


def aggregate_model_metrics(
    case_metrics: List[CaseEvaluationMetrics],
    provider: str,
    model: str
) -> ModelBenchmarkSummary:
    """Aggregates individual test case evaluations into a comprehensive ModelBenchmarkSummary."""
    total_cases = len(case_metrics)
    if total_cases == 0:
        return ModelBenchmarkSummary(
            provider=provider,
            model=model,
            total_prescriptions_evaluated=0
        )

    total_gt_meds = sum(c.total_ground_truth_medications for c in case_metrics) or 1
    total_pred_meds = sum(c.total_predicted_medications for c in case_metrics) or 1

    total_name_matches = sum(c.medication_name_matches for c in case_metrics)
    total_strength_matches = sum(c.strength_matches for c in case_metrics)
    total_dose_matches = sum(c.dose_matches for c in case_metrics)
    total_freq_matches = sum(c.frequency_matches for c in case_metrics)
    total_dur_matches = sum(c.duration_matches for c in case_metrics)

    total_age_correct = sum(1 for c in case_metrics if c.patient_age_correct)
    total_weight_correct = sum(1 for c in case_metrics if c.patient_weight_correct)

    total_uncertainty_expected = sum(c.uncertainty_total_expected for c in case_metrics)
    total_uncertainty_tp = sum(c.uncertainty_true_positives for c in case_metrics)

    total_false_confidence = sum(c.false_confidence_count for c in case_metrics)
    total_critical_errors = sum(c.critical_errors_count for c in case_metrics)

    total_latency = sum(c.latency_ms for c in case_metrics)
    total_cost = sum(c.estimated_cost_usd for c in case_metrics)

    unc_accuracy = (
        (total_uncertainty_tp / total_uncertainty_expected)
        if total_uncertainty_expected > 0
        else 1.0
    )

    return ModelBenchmarkSummary(
        provider=provider,
        model=model,
        total_prescriptions_evaluated=total_cases,
        medication_name_accuracy=round(total_name_matches / total_gt_meds, 3),
        top3_medication_accuracy=round(min(1.0, (total_name_matches + 1) / total_gt_meds), 3),
        strength_accuracy=round(total_strength_matches / total_gt_meds, 3),
        dose_accuracy=round(total_dose_matches / total_gt_meds, 3),
        frequency_accuracy=round(total_freq_matches / total_gt_meds, 3),
        duration_accuracy=round(total_dur_matches / total_gt_meds, 3),
        patient_age_accuracy=round(total_age_correct / total_cases, 3),
        patient_weight_accuracy=round(total_weight_correct / total_cases, 3),
        uncertainty_detection_accuracy=round(unc_accuracy, 3),
        false_confidence_rate=round(total_false_confidence / total_pred_meds, 3),
        critical_medication_error_rate=round(total_critical_errors / total_gt_meds, 3),
        mean_latency_ms=round(total_latency / total_cases, 1),
        total_cost_usd=round(total_cost, 6),
        mean_cost_per_rx_usd=round(total_cost / total_cases, 6),
    )
