import argparse
import asyncio
import json
import logging
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import PROJECT_ROOT
from ..models.prescription import AnalysisResponse
from ..providers.factory import get_provider
from ..services.analyzer import PrescriptionAnalyzer
from .metrics import (
    CaseEvaluationMetrics,
    ModelBenchmarkSummary,
    aggregate_model_metrics,
    evaluate_case,
)

logger = logging.getLogger(__name__)


class BenchmarkRunner:
    """Automated benchmark orchestration system for evaluating multimodal vision models on Egyptian prescriptions."""

    def __init__(self, benchmark_dir: Optional[Path] = None):
        self.benchmark_dir = benchmark_dir or (PROJECT_ROOT / "dataset" / "benchmark")
        self.analyzer = PrescriptionAnalyzer()

    def discover_cases(self) -> List[Path]:
        """Discovers all case folders containing image.jpg and ground_truth.json."""
        if not self.benchmark_dir.exists():
            return []

        case_dirs = []
        for p in sorted(self.benchmark_dir.iterdir()):
            if p.is_dir() and (p / "image.jpg").exists() and (p / "ground_truth.json").exists():
                case_dirs.append(p)
        return case_dirs

    async def run_model_on_case(
        self,
        case_dir: Path,
        provider_name: str,
        model_name: Optional[str] = None,
        pipeline_version: str = "v1",
        use_cache: bool = True
    ) -> Tuple[Dict[str, Any], int, float]:
        """
        Runs the specified vision model on a single benchmark case image,
        or loads cached output if available and requested.
        """
        from ..services.pipeline_v2 import pipeline_v2

        provider = get_provider(provider_name)
        target_model = model_name or provider.default_model

        # Sanitized output filename (e.g. "gemini_gemini-2.5-flash_v2.json")
        out_filename = f"{provider_name}_{target_model}_{pipeline_version}.json".replace("/", "_")
        out_path = case_dir / out_filename

        if use_cache and out_path.exists():
            try:
                with open(out_path, "r", encoding="utf-8") as f:
                    cached_data = json.load(f)
                meta = cached_data.get("metadata", {})
                latency = meta.get("processing_time_ms", 0)
                cost = (meta.get("cost_estimate") or {}).get("total_cost_usd", 0.0)
                return cached_data.get("analysis", {}), latency, cost
            except Exception as e:
                logger.warning(f"Error reading cache at {out_path}: {e}")

        # Run fresh analysis
        image_path = case_dir / "image.jpg"
        with open(image_path, "rb") as f:
            image_bytes = f.read()

        custom_prompt = self.analyzer.load_prompt() + f"\n[Case Context: {case_dir.name}]"

        if pipeline_version.lower() == "v2":
            response: AnalysisResponse = await pipeline_v2.process_prescription(
                image_bytes=image_bytes,
                filename=f"{case_dir.name}_image.jpg",
                mime_type="image/jpeg",
                provider_name=provider_name,
                model_name=target_model,
                custom_prompt=custom_prompt,
            )
        else:
            response: AnalysisResponse = await self.analyzer.analyze_image(
                image_bytes=image_bytes,
                filename=f"{case_dir.name}_image.jpg",
                mime_type="image/jpeg",
                provider_name=provider_name,
                model_name=target_model,
                custom_prompt=custom_prompt,
            )

        resp_dict = response.model_dump()
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(resp_dict, f, indent=2, ensure_ascii=False)

        latency = response.metadata.processing_time_ms
        cost = response.metadata.cost_estimate.total_cost_usd if response.metadata.cost_estimate else 0.0

        return resp_dict.get("analysis", {}), latency, cost

    async def run_benchmark(
        self,
        model_configs: List[Dict[str, str]],
        use_cache: bool = True
    ) -> Dict[str, Any]:
        """
        Runs the full benchmark suite across all discovered cases for the specified models.
        """
        cases = self.discover_cases()
        if not cases:
            raise FileNotFoundError(f"No benchmark test cases found in {self.benchmark_dir}")

        overall_results = {
            "total_cases": len(cases),
            "case_ids": [c.name for c in cases],
            "model_summaries": {},
            "case_details": {},
        }

        for config in model_configs:
            prov_name = config["provider"]
            model_name = config.get("model")
            pipeline_ver = config.get("pipeline", "v1").lower()
            provider = get_provider(prov_name)
            target_model = model_name or provider.default_model
            model_key = f"{prov_name}:{target_model} [{pipeline_ver.upper()}]"

            logger.info(f"--- Benchmarking {model_key} across {len(cases)} cases ---")
            case_evals: List[CaseEvaluationMetrics] = []

            for case_dir in cases:
                case_id = case_dir.name
                with open(case_dir / "ground_truth.json", "r", encoding="utf-8") as f:
                    ground_truth = json.load(f)

                try:
                    pred_analysis, latency, cost = await self.run_model_on_case(
                        case_dir=case_dir,
                        provider_name=prov_name,
                        model_name=target_model,
                        pipeline_version=pipeline_ver,
                        use_cache=use_cache
                    )

                    case_metric = evaluate_case(
                        case_id=case_id,
                        ground_truth=ground_truth,
                        prediction=pred_analysis,
                        provider=prov_name,
                        model=target_model,
                        latency_ms=latency,
                        cost_usd=cost
                    )
                    case_evals.append(case_metric)

                    if case_id not in overall_results["case_details"]:
                        overall_results["case_details"][case_id] = {}
                    overall_results["case_details"][case_id][model_key] = case_metric.model_dump()

                except Exception as exc:
                    logger.error(f"Error evaluating {case_id} on {model_key}: {exc}")

            summary = aggregate_model_metrics(case_evals, prov_name, f"{target_model} [{pipeline_ver.upper()}]")
            overall_results["model_summaries"][model_key] = summary.model_dump()

        # Save aggregated JSON results
        results_file = self.benchmark_dir / "benchmark_results.json"
        with open(results_file, "w", encoding="utf-8") as f:
            json.dump(overall_results, f, indent=2, ensure_ascii=False)

        # Generate and save Markdown Report
        markdown_report = self.generate_markdown_report(overall_results)
        report_file = self.benchmark_dir / "BENCHMARK_REPORT.md"
        with open(report_file, "w", encoding="utf-8") as f:
            f.write(markdown_report)

        return overall_results

    def generate_markdown_report(self, results: Dict[str, Any]) -> str:
        """Generates a professional Markdown benchmark comparison report."""
        summaries = results.get("model_summaries", {})
        total_cases = results.get("total_cases", 0)

        md = [
            "# 📊 Egyptian Prescription AI — Model Benchmark Report (Phase 2)",
            "",
            f"**Total Prescriptions Evaluated:** {total_cases} test cases  ",
            "**Primary Safety Principle:** Critical Error Rate (0.0% is required for clinical safety)",
            "",
            "## 🏆 Benchmark Comparison Matrix",
            "",
            "| Model | Med Name Acc. | Critical Error Rate 🛡️ | Uncertainty Acc. | Strength Acc. | Dose Acc. | Freq. Acc. | Latency (ms) | Cost / Rx |",
            "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |",
        ]

        # Sort models by critical error rate ascending (safest first), then by name accuracy descending
        sorted_models = sorted(
            summaries.items(),
            key=lambda item: (item[1]["critical_medication_error_rate"], -item[1]["medication_name_accuracy"])
        )

        for model_key, s in sorted_models:
            name_acc = f"{s['medication_name_accuracy'] * 100:.1f}%"
            crit_rate = f"**{s['critical_medication_error_rate'] * 100:.1f}%**"
            unc_acc = f"{s['uncertainty_detection_accuracy'] * 100:.1f}%"
            str_acc = f"{s['strength_accuracy'] * 100:.1f}%"
            dose_acc = f"{s['dose_accuracy'] * 100:.1f}%"
            freq_acc = f"{s['frequency_accuracy'] * 100:.1f}%"
            lat = f"{s['mean_latency_ms']:.0f} ms"
            cost = f"${s['mean_cost_per_rx_usd']:.4f}"

            md.append(f"| **{model_key}** | {name_acc} | {crit_rate} | {unc_acc} | {str_acc} | {dose_acc} | {freq_acc} | {lat} | {cost} |")

        md.extend([
            "",
            "## 🔍 Safety & Performance Analysis",
            "",
            "- **Critical Medication Error Rate:** Flags dangerous drug substitutions (e.g., misreading *Losec* as *Lasix*). Models with >0% critical error rate present serious safety risks.",
            "- **Uncertainty Calibration:** Measures whether the model correctly assigns `null` and `uncertain` to blurred/unreadable text instead of hallucinating plausible doses.",
            "- **Cost vs. Latency Tradeoff:** Lightweight flash models provide sub-second latency with fractional millicent costs.",
            "",
            "---",
            "*Report auto-generated by Prescription AI Benchmark Suite.*"
        ])

        return "\n".join(md)


async def main():
    parser = argparse.ArgumentParser(description="Run Egyptian Prescription AI Benchmark Suite")
    parser.add_argument("--providers", type=str, default="mock", help="Comma-separated provider names (gemini,openai,claude,mock)")
    parser.add_argument("--models", type=str, default=None, help="Comma-separated provider:model:pipeline specs (e.g. mock:mock-conservative-v1:v2)")
    parser.add_argument("--pipelines", type=str, default="v1,v2", help="Comma-separated pipeline versions to test (e.g. v1,v2)")
    parser.add_argument("--no-cache", action="store_true", help="Force fresh inference without cache")
    args = parser.parse_args()

    runner = BenchmarkRunner()
    configs = []

    if args.models:
        for spec in args.models.split(","):
            parts = spec.strip().split(":")
            if len(parts) == 3:
                configs.append({"provider": parts[0], "model": parts[1], "pipeline": parts[2]})
            elif len(parts) == 2:
                configs.append({"provider": parts[0], "model": parts[1], "pipeline": "v2"})
            elif len(parts) == 1:
                p_obj = get_provider(parts[0])
                configs.append({"provider": parts[0], "model": p_obj.default_model, "pipeline": "v2"})
    else:
        provider_names = [p.strip() for p in args.providers.split(",") if p.strip()]
        pipeline_versions = [pv.strip() for pv in args.pipelines.split(",") if pv.strip()]
        for prov in provider_names:
            p_obj = get_provider(prov)
            for pipe in pipeline_versions:
                configs.append({"provider": prov, "model": p_obj.default_model, "pipeline": pipe})

    print(f"Starting Egyptian Prescription AI Benchmark with {len(configs)} configuration(s):")
    for c in configs:
        print(f" - {c['provider']} ({c.get('model')}) [Pipeline: {c.get('pipeline', 'v1').upper()}]")

    results = await runner.run_benchmark(configs, use_cache=not args.no_cache)
    print("\nBenchmark Completed Successfully!")
    print(runner.generate_markdown_report(results))


if __name__ == "__main__":
    asyncio.run(main())
