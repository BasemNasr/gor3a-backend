import asyncio
import json
import logging
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from ..dataset.bilingual_loader import (
    BilingualDatasetLoader,
    BilingualPrescriptionCase,
    bilingual_dataset_loader,
)
from ..drugs.repository import drug_repository
from ..models.prescription import AnalysisResponse
from ..providers.factory import get_provider
from ..services.analyzer import PrescriptionAnalyzer
from ..services.pipeline_v2 import pipeline_v2
from ..utils.text import (
    calculate_cer,
    calculate_precision_recall_f1,
    calculate_wer,
    normalize_text,
    string_similarity,
)

logger = logging.getLogger(__name__)


class BilingualEvaluationMetrics:
    """Metrics evaluated on a single bilingual prescription test case."""

    def __init__(
        self,
        case_id: str,
        filename: str,
        gt_medications: List[Dict[str, Any]],
        pred_medications: List[Dict[str, Any]],
        latency_ms: int,
        cost_usd: float,
    ):
        self.case_id = case_id
        self.filename = filename
        self.gt_medications = gt_medications
        self.pred_medications = pred_medications
        self.latency_ms = latency_ms
        self.cost_usd = cost_usd

        # Calculate scores
        self.name_accuracy = self._eval_names()
        self.dose_accuracy = self._eval_field("Dosage", "dose")
        self.freq_accuracy = self._eval_field("Frequency", "frequency")
        self.instructions_accuracy = self._eval_instructions()
        self.eda_grounded_count, self.eda_grounding_rate = self._eval_eda_grounding()

        # Core clinical metrics
        self.precision, self.recall, self.f1_score = self._eval_precision_recall_f1()
        self.cer, self.wer = self._eval_cer_wer()
        self.exact_match_rate = self._eval_exact_match()
        self.accuracy = self._eval_accuracy()

    def _eval_precision_recall_f1(self) -> Tuple[float, float, float]:
        tp, fn = 0, 0
        matched_preds = set()
        for gt in self.gt_medications:
            gt_name = normalize_text(gt.get("Medication_name") or gt.get("Medication_name_raw") or "")
            matched = False
            for p_idx, pred in enumerate(self.pred_medications):
                if p_idx in matched_preds:
                    continue
                pred_name = normalize_text(pred.get("name") or "")
                if string_similarity(gt_name, pred_name) >= 0.70 or gt_name in pred_name or pred_name in gt_name:
                    matched_preds.add(p_idx)
                    tp += 1
                    matched = True
                    break
            if not matched:
                fn += 1
        fp = len(self.pred_medications) - len(matched_preds)
        return calculate_precision_recall_f1(tp, fp, fn)

    def _eval_cer_wer(self) -> Tuple[float, float]:
        ref_text = " ".join([
            f"{m.get('Medication_name') or ''} {m.get('Dosage') or ''} {m.get('Frequency') or ''} {m.get('Instructions') or ''}".strip()
            for m in self.gt_medications
        ]).strip()
        hyp_text = " ".join([
            f"{m.get('name') or ''} {m.get('strength') or ''} {m.get('dose') or ''} {m.get('frequency') or ''} {m.get('instructions') or ''}".strip()
            for m in self.pred_medications
        ]).strip()
        return calculate_cer(ref_text, hyp_text), calculate_wer(ref_text, hyp_text)

    def _eval_exact_match(self) -> float:
        if not self.gt_medications:
            return 1.0
        exact_count = 0
        for i, gt in enumerate(self.gt_medications):
            if i >= len(self.pred_medications):
                break
            pred = self.pred_medications[i]
            gt_name = normalize_text(gt.get("Medication_name") or gt.get("Medication_name_raw") or "")
            pred_name = normalize_text(pred.get("name") or "")
            name_ok = (string_similarity(gt_name, pred_name) >= 0.85)
            dose_ok = string_similarity(str(gt.get("Dosage") or ""), str(pred.get("dose") or "")) >= 0.75
            freq_ok = string_similarity(str(gt.get("Frequency") or ""), str(pred.get("frequency") or "")) >= 0.75
            if name_ok and dose_ok and freq_ok:
                exact_count += 1
        return round(exact_count / len(self.gt_medications), 3)

    def _eval_accuracy(self) -> float:
        total_fields = len(self.gt_medications) * 3  # name, dose, freq
        if total_fields == 0:
            return 1.0
        correct_fields = (
            (self.name_accuracy * len(self.gt_medications))
            + (self.dose_accuracy * len(self.gt_medications))
            + (self.freq_accuracy * len(self.gt_medications))
        )
        return round(correct_fields / total_fields, 3)

    def _eval_names(self) -> float:
        if not self.gt_medications:
            return 1.0
        matched = 0
        for gt in self.gt_medications:
            gt_name = gt.get("Medication_name") or gt.get("Medication_name_raw") or ""
            norm_gt = normalize_text(gt_name)
            if not norm_gt:
                continue
            for pred in self.pred_medications:
                pred_name = pred.get("name") or ""
                norm_pred = normalize_text(pred_name)
                if string_similarity(norm_gt, norm_pred) >= 0.70 or norm_gt in norm_pred or norm_pred in norm_gt:
                    matched += 1
                    break
        return matched / len(self.gt_medications)

    def _eval_field(self, gt_key: str, pred_key: str) -> float:
        if not self.gt_medications:
            return 1.0
        scores = []
        for i, gt in enumerate(self.gt_medications):
            gt_val = gt.get(gt_key) or gt.get(f"{gt_key}_raw")
            pred_val = self.pred_medications[i].get(pred_key) if i < len(self.pred_medications) else None

            if gt_val is None and pred_val is None:
                scores.append(1.0)
            elif gt_val is None or pred_val is None:
                scores.append(0.0)
            else:
                sim = string_similarity(str(gt_val), str(pred_val))
                scores.append(1.0 if sim >= 0.70 else sim)
        return sum(scores) / len(scores) if scores else 1.0

    def _eval_instructions(self) -> float:
        """Evaluates Arabic clinical administration instructions."""
        scores = []
        for i, gt in enumerate(self.gt_medications):
            gt_instr = gt.get("Instructions") or gt.get("Instructions_raw")
            pred_instr = self.pred_medications[i].get("instructions") if i < len(self.pred_medications) else None

            if not gt_instr and not pred_instr:
                scores.append(1.0)
            elif not gt_instr or not pred_instr:
                scores.append(0.0)
            else:
                sim = string_similarity(str(gt_instr), str(pred_instr))
                scores.append(1.0 if sim >= 0.65 else sim)
        return sum(scores) / len(scores) if scores else 1.0

    def _eval_eda_grounding(self) -> Tuple[int, float]:
        """Calculates percentage of predicted drugs matching registered Egyptian medicines."""
        if not self.pred_medications:
            return 0, 0.0
        grounded = 0
        for p in self.pred_medications:
            name = p.get("name")
            if name:
                candidates = drug_repository.search_candidates(name, limit=1)
                if candidates and candidates[0][1] >= 0.75:
                    grounded += 1
        return grounded, grounded / len(self.pred_medications)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "case_id": self.case_id,
            "filename": self.filename,
            "accuracy": round(self.accuracy, 3),
            "precision": round(self.precision, 3),
            "recall": round(self.recall, 3),
            "f1_score": round(self.f1_score, 3),
            "cer": round(self.cer, 3),
            "wer": round(self.wer, 3),
            "exact_match_rate": round(self.exact_match_rate, 3),
            "name_accuracy": round(self.name_accuracy, 3),
            "dose_accuracy": round(self.dose_accuracy, 3),
            "freq_accuracy": round(self.freq_accuracy, 3),
            "instructions_accuracy": round(self.instructions_accuracy, 3),
            "eda_grounding_rate": round(self.eda_grounding_rate, 3),
            "latency_ms": self.latency_ms,
            "cost_usd": self.cost_usd,
        }


class BilingualBenchmarkRunner:
    """
    Automated evaluation runner for the Noor Alzoghby Bilingual Medical Prescription Dataset.
    Runs vision models & Pipeline V2 on test split cases and generates comparative accuracy reports.
    """

    def __init__(self, loader: Optional[BilingualDatasetLoader] = None):
        self.loader = loader or bilingual_dataset_loader
        self.analyzer = PrescriptionAnalyzer()

    async def evaluate_model_on_test_split(
        self,
        provider_name: str = "mock",
        model_name: Optional[str] = None,
        pipeline_version: str = "v2",
        max_cases: int = 15,
    ) -> Dict[str, Any]:
        """
        Runs evaluation across test split cases for the specified model and pipeline version.
        """
        test_cases = self.loader.get_split("test")
        if max_cases:
            test_cases = test_cases[:max_cases]

        provider = get_provider(provider_name)
        target_model = model_name or provider.default_model

        case_results: List[BilingualEvaluationMetrics] = []

        for case in test_cases:
            if not case.image_path.exists():
                continue

            with open(case.image_path, "rb") as f:
                img_bytes = f.read()

            try:
                if pipeline_version.lower() == "v2":
                    response: AnalysisResponse = await pipeline_v2.process_prescription(
                        image_bytes=img_bytes,
                        filename=case.filename,
                        mime_type="image/jpeg",
                        provider_name=provider_name,
                        model_name=target_model,
                    )
                else:
                    response: AnalysisResponse = await self.analyzer.analyze_image(
                        image_bytes=img_bytes,
                        filename=case.filename,
                        mime_type="image/jpeg",
                        provider_name=provider_name,
                        model_name=target_model,
                    )

                pred_meds = [m.model_dump() for m in response.analysis.medications]
                latency = response.metadata.processing_time_ms
                cost = (
                    response.metadata.cost_estimate.total_cost_usd
                    if response.metadata.cost_estimate
                    else 0.0
                )

                metric = BilingualEvaluationMetrics(
                    case_id=case.case_id,
                    filename=case.filename,
                    gt_medications=case.raw_medications,
                    pred_medications=pred_meds,
                    latency_ms=latency,
                    cost_usd=cost,
                )
                case_results.append(metric)

            except Exception as exc:
                logger.error(f"Error evaluating bilingual case {case.case_id}: {exc}")

        # Aggregate metrics
        n = len(case_results)
        if n == 0:
            return {"error": "No test cases could be evaluated."}

        mean_acc = sum(m.accuracy for m in case_results) / n
        mean_prec = sum(m.precision for m in case_results) / n
        mean_rec = sum(m.recall for m in case_results) / n
        mean_f1 = sum(m.f1_score for m in case_results) / n
        mean_cer = sum(m.cer for m in case_results) / n
        mean_wer = sum(m.wer for m in case_results) / n
        mean_exact = sum(m.exact_match_rate for m in case_results) / n
        mean_name_acc = sum(m.name_accuracy for m in case_results) / n
        mean_dose_acc = sum(m.dose_accuracy for m in case_results) / n
        mean_freq_acc = sum(m.freq_accuracy for m in case_results) / n
        mean_instr_acc = sum(m.instructions_accuracy for m in case_results) / n
        mean_eda_rate = sum(m.eda_grounding_rate for m in case_results) / n
        mean_lat = sum(m.latency_ms for m in case_results) / n
        mean_cost = sum(m.cost_usd for m in case_results) / n

        return {
            "model_key": f"{provider_name}:{target_model} [{pipeline_version.upper()}]",
            "provider": provider_name,
            "model": target_model,
            "pipeline": pipeline_version,
            "evaluated_cases": n,
            "metrics": {
                "accuracy": round(mean_acc, 3),
                "precision": round(mean_prec, 3),
                "recall": round(mean_rec, 3),
                "f1_score": round(mean_f1, 3),
                "cer": round(mean_cer, 3),
                "wer": round(mean_wer, 3),
                "exact_match_rate": round(mean_exact, 3),
                "medication_name_accuracy": round(mean_name_acc, 3),
                "dosage_accuracy": round(mean_dose_acc, 3),
                "frequency_accuracy": round(mean_freq_acc, 3),
                "arabic_instructions_accuracy": round(mean_instr_acc, 3),
                "eda_database_grounding_rate": round(mean_eda_rate, 3),
                "mean_latency_ms": round(mean_lat, 1),
                "mean_cost_per_rx_usd": round(mean_cost, 4),
            },
            "sample_cases": [m.to_dict() for m in case_results[:5]],
        }


bilingual_benchmark_runner = BilingualBenchmarkRunner()
