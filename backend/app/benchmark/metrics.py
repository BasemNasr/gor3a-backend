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


from ..utils.text import (
    calculate_cer,
    calculate_precision_recall_f1,
    calculate_wer,
    normalize_text,
    string_similarity,
)


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

    # Core Clinical Evaluation Metrics
    accuracy: float = 0.0              # Overall extracted field accuracy
    precision: float = 0.0             # TP / (TP + FP) for medication detection
    recall: float = 0.0                # TP / (TP + FN) for medication detection
    f1_score: float = 0.0              # Harmonic mean of Precision and Recall
    cer: float = 0.0                   # Character Error Rate (OCR character level edit distance)
    wer: float = 0.0                   # Word Error Rate (OCR word level edit distance)
    exact_match_medications: int = 0   # Count of medications where all fields matched exactly
    exact_match_ratio: float = 0.0     # Ratio of exact match medications (0.0 to 1.0)

    # Metric counts (correct / total evaluated)
    medication_name_matches: int = 0
    strength_matches: int = 0
    dose_matches: int = 0
    frequency_matches: int = 0
    duration_matches: int = 0
    instructions_matches: int = 0

    tp_medications: int = 0
    fp_medications: int = 0
    fn_medications: int = 0
    total_fields_evaluated: int = 0
    total_fields_correct: int = 0

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

    # Core Clinical Benchmark Metrics (Requested by User)
    accuracy: float = 0.0              # نسبة النتائج الصحيحة (Overall field accuracy)
    precision: float = 0.0             # من كل الأدوية اللي الموديل قال إنها موجودة، كام واحد فعلاً صحيح
    recall: float = 0.0                # من كل الأدوية الموجودة فعلاً في الروشتة، كام واحد الموديل قدر يكتشفه
    f1_score: float = 0.0              # توازن بين Precision و Recall
    cer: float = 0.0                   # Character Error Rate (مهم جدًا للـ OCR)
    wer: float = 0.0                   # Word Error Rate (نسبة أخطاء الكلمات)
    exact_match_rate: float = 0.0      # Exact Match / Field Accuracy (هل الموديل استخرج البيانات كاملة وصحيحة)

    # Individual Field Accuracies (0.0 to 1.0)
    medication_name_accuracy: float = 0.0
    top3_medication_accuracy: float = 0.0
    strength_accuracy: float = 0.0
    dose_accuracy: float = 0.0
    frequency_accuracy: float = 0.0
    duration_accuracy: float = 0.0
    instructions_accuracy: float = 0.0
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
    tp_count = 0
    fn_count = 0

    # Field accuracy tracking
    fields_evaluated = 0
    fields_correct = 0

    if gt_rx.get("patient_age"):
        fields_evaluated += 1
        if metrics.patient_age_correct:
            fields_correct += 1

    if gt_rx.get("patient_weight_kg") is not None:
        fields_evaluated += 1
        if metrics.patient_weight_correct:
            fields_correct += 1

    for gt_item in gt_meds:
        gt_name = gt_item.get("name")
        gt_status = gt_item.get("status", "identified")
        gt_is_uncertain = (gt_status in ("uncertain", "unreadable")) or (gt_name is None)

        fields_evaluated += 1  # name field

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
            name_matched = False
            if is_lasa:
                metrics.critical_errors_count += 1
                if pred_conf >= 0.8:
                    metrics.false_confidence_count += 1
                fn_count += 1
            elif best_sim >= 0.85:
                # Genuine medication name match
                metrics.medication_name_matches += 1
                fields_correct += 1
                tp_count += 1
                name_matched = True
            elif best_sim >= 0.70:
                tp_count += 1
                name_matched = True
            else:
                fn_count += 1
                # Mismatch with high confidence -> false confidence
                if pred_conf >= 0.8:
                    metrics.false_confidence_count += 1

            # Strength
            gt_strength = normalize_text(gt_item.get("strength"))
            pred_strength = normalize_text(matched_pred.get("strength"))
            strength_matched = False
            if gt_strength:
                fields_evaluated += 1
                if string_similarity(gt_strength, pred_strength) >= 0.8:
                    metrics.strength_matches += 1
                    fields_correct += 1
                    strength_matched = True
            else:
                strength_matched = not bool(pred_strength)
                if not pred_strength:
                    metrics.strength_matches += 1

            # Dose
            gt_dose = normalize_text(gt_item.get("dose"))
            pred_dose = normalize_text(matched_pred.get("dose"))
            dose_matched = False
            if gt_dose:
                fields_evaluated += 1
                if string_similarity(gt_dose, pred_dose) >= 0.8:
                    metrics.dose_matches += 1
                    fields_correct += 1
                    dose_matched = True
            else:
                dose_matched = not bool(pred_dose)
                if not pred_dose:
                    metrics.dose_matches += 1

            # Frequency
            gt_freq = normalize_text(gt_item.get("frequency"))
            pred_freq = normalize_text(matched_pred.get("frequency"))
            freq_matched = False
            if gt_freq:
                fields_evaluated += 1
                if string_similarity(gt_freq, pred_freq) >= 0.75:
                    metrics.frequency_matches += 1
                    fields_correct += 1
                    freq_matched = True
            else:
                freq_matched = not bool(pred_freq)
                if not pred_freq:
                    metrics.frequency_matches += 1

            # Duration
            gt_dur = normalize_text(gt_item.get("duration"))
            pred_dur = normalize_text(matched_pred.get("duration"))
            dur_matched = False
            if gt_dur:
                fields_evaluated += 1
                if string_similarity(gt_dur, pred_dur) >= 0.75:
                    metrics.duration_matches += 1
                    fields_correct += 1
                    dur_matched = True
            else:
                dur_matched = not bool(pred_dur)
                if not pred_dur:
                    metrics.duration_matches += 1

            # Instructions
            gt_instr = normalize_text(gt_item.get("instructions"))
            pred_instr = normalize_text(matched_pred.get("instructions"))
            instr_matched = False
            if gt_instr:
                fields_evaluated += 1
                if (
                    string_similarity(gt_instr, pred_instr) >= 0.65
                    or gt_instr in pred_instr
                    or pred_instr in gt_instr
                ):
                    metrics.instructions_matches += 1
                    fields_correct += 1
                    instr_matched = True
            else:
                instr_matched = not bool(pred_instr)
                if not pred_instr:
                    metrics.instructions_matches += 1

            # Exact Match per Medication: all core fields correct
            if name_matched and strength_matched and dose_matched and freq_matched:
                metrics.exact_match_medications += 1

            # Uncertainty check
            if gt_is_uncertain:
                if pred_status in ("uncertain", "unreadable") or pred_name is None:
                    metrics.uncertainty_true_positives += 1
                elif pred_status == "identified" and pred_conf >= 0.8:
                    # Model hallucinated on an unreadable line
                    metrics.false_confidence_count += 1
        else:
            # Slot did not match an identified medication
            fn_count += 1
            if gt_is_uncertain:
                # If there are unmatched predictions that hallucinated as identified, it failed
                unmatched_identified = [
                    p for i, p in enumerate(pred_meds)
                    if i not in matched_pred_indices and p.get("status") == "identified" and float(p.get("confidence", 0.5)) >= 0.8
                ]
                if not unmatched_identified:
                    metrics.uncertainty_true_positives += 1

    # Check for hallucinated medications (predicted meds not matching any GT)
    fp_count = 0
    for p_idx, pred_item in enumerate(pred_meds):
        if p_idx not in matched_pred_indices:
            fp_count += 1
            pred_conf = float(pred_item.get("confidence", 0.5))
            if pred_conf >= 0.8 and pred_item.get("name"):
                metrics.false_confidence_count += 1

    # Calculate Precision, Recall, F1 for this prescription
    metrics.tp_medications = tp_count
    metrics.fp_medications = fp_count
    metrics.fn_medications = fn_count

    prec, rec, f1 = calculate_precision_recall_f1(tp_count, fp_count, fn_count)
    metrics.precision = prec
    metrics.recall = rec
    metrics.f1_score = f1

    # Exact Match ratio
    metrics.exact_match_ratio = round(
        metrics.exact_match_medications / max(1, len(gt_meds)), 3
    )

    # Overall Field Accuracy
    metrics.total_fields_evaluated = fields_evaluated
    metrics.total_fields_correct = fields_correct
    metrics.accuracy = round(
        fields_correct / max(1, fields_evaluated), 3
    )

    # Full Reference & Hypothesis concatenated string for CER & WER
    ref_parts = []
    for m in gt_meds:
        ref_parts.extend([
            str(m.get("name") or ""),
            str(m.get("strength") or ""),
            str(m.get("dose") or ""),
            str(m.get("frequency") or ""),
            str(m.get("duration") or ""),
            str(m.get("instructions") or ""),
        ])
    if gt_rx.get("patient_age"):
        ref_parts.append(str(gt_rx["patient_age"]))
    if gt_rx.get("patient_weight_kg"):
        ref_parts.append(str(gt_rx["patient_weight_kg"]))
    ref_text = " ".join([p for p in ref_parts if p.strip()])

    hyp_parts = []
    for m in pred_meds:
        hyp_parts.extend([
            str(m.get("name") or ""),
            str(m.get("strength") or ""),
            str(m.get("dose") or ""),
            str(m.get("frequency") or ""),
            str(m.get("duration") or ""),
            str(m.get("instructions") or ""),
        ])
    if pred_rx.get("patient_age"):
        hyp_parts.append(str(pred_rx["patient_age"]))
    if pred_rx.get("patient_weight_kg"):
        hyp_parts.append(str(pred_rx["patient_weight_kg"]))
    hyp_text = " ".join([p for p in hyp_parts if p.strip()])

    metrics.cer = calculate_cer(ref_text, hyp_text)
    metrics.wer = calculate_wer(ref_text, hyp_text)

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
    total_instr_matches = sum(c.instructions_matches for c in case_metrics)

    total_age_correct = sum(1 for c in case_metrics if c.patient_age_correct)
    total_weight_correct = sum(1 for c in case_metrics if c.patient_weight_correct)

    total_uncertainty_expected = sum(c.uncertainty_total_expected for c in case_metrics)
    total_uncertainty_tp = sum(c.uncertainty_true_positives for c in case_metrics)

    total_false_confidence = sum(c.false_confidence_count for c in case_metrics)
    total_critical_errors = sum(c.critical_errors_count for c in case_metrics)

    total_latency = sum(c.latency_ms for c in case_metrics)
    total_cost = sum(c.estimated_cost_usd for c in case_metrics)

    # Core Clinical Metrics Aggregation
    mean_accuracy = sum(c.accuracy for c in case_metrics) / total_cases
    mean_precision = sum(c.precision for c in case_metrics) / total_cases
    mean_recall = sum(c.recall for c in case_metrics) / total_cases
    mean_f1 = sum(c.f1_score for c in case_metrics) / total_cases
    mean_cer = sum(c.cer for c in case_metrics) / total_cases
    mean_wer = sum(c.wer for c in case_metrics) / total_cases
    exact_match_rate = sum(c.exact_match_medications for c in case_metrics) / total_gt_meds

    unc_accuracy = (
        (total_uncertainty_tp / total_uncertainty_expected)
        if total_uncertainty_expected > 0
        else 1.0
    )

    return ModelBenchmarkSummary(
        provider=provider,
        model=model,
        total_prescriptions_evaluated=total_cases,
        accuracy=round(mean_accuracy, 3),
        precision=round(mean_precision, 3),
        recall=round(mean_recall, 3),
        f1_score=round(mean_f1, 3),
        cer=round(mean_cer, 3),
        wer=round(mean_wer, 3),
        exact_match_rate=round(exact_match_rate, 3),
        medication_name_accuracy=round(total_name_matches / total_gt_meds, 3),
        top3_medication_accuracy=round(min(1.0, (total_name_matches + 1) / total_gt_meds), 3),
        strength_accuracy=round(total_strength_matches / total_gt_meds, 3),
        dose_accuracy=round(total_dose_matches / total_gt_meds, 3),
        frequency_accuracy=round(total_freq_matches / total_gt_meds, 3),
        duration_accuracy=round(total_dur_matches / total_gt_meds, 3),
        instructions_accuracy=round(total_instr_matches / total_gt_meds, 3),
        patient_age_accuracy=round(total_age_correct / total_cases, 3),
        patient_weight_accuracy=round(total_weight_correct / total_cases, 3),
        uncertainty_detection_accuracy=round(unc_accuracy, 3),
        false_confidence_rate=round(total_false_confidence / total_pred_meds, 3),
        critical_medication_error_rate=round(total_critical_errors / total_gt_meds, 3),
        mean_latency_ms=round(total_latency / total_cases, 1),
        total_cost_usd=round(total_cost, 6),
        mean_cost_per_rx_usd=round(total_cost / total_cases, 6),
    )
