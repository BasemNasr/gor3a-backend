import pytest
from fastapi.testclient import TestClient
from backend.app.benchmark.metrics import (
    aggregate_model_metrics,
    evaluate_case,
    is_critical_medication_error,
    string_similarity,
)
from backend.app.benchmark.runner import BenchmarkRunner
from backend.app.main import app

client = TestClient(app)


def test_string_similarity():
    assert string_similarity("Flagyl", "Flagyl") == 1.0
    assert string_similarity("flagyl", "FLAGYL") == 1.0
    assert string_similarity("Flajyl", "Flagyl") > 0.8
    assert string_similarity("Flagyl", "Cetal") < 0.4


def test_cer_wer_metrics():
    from backend.app.utils.text import calculate_cer, calculate_wer

    # Exact match: CER and WER should be 0.0
    assert calculate_cer("Flagyl 125mg", "Flagyl 125mg") == 0.0
    assert calculate_wer("Flagyl 125mg tid", "Flagyl 125mg tid") == 0.0

    # Minor typo (1 char diff in 6 chars)
    cer = calculate_cer("Flagyl", "Flajyl")
    assert 0.0 < cer < 0.3

    # Word substitution
    wer = calculate_wer("Flagyl 125mg tid", "Flagyl 250mg tid")
    assert 0.2 < wer < 0.4

    # Empty edge cases
    assert calculate_cer("", "") == 0.0
    assert calculate_cer("Flagyl", "") == 1.0
    assert calculate_wer("", "") == 0.0
    assert calculate_wer("Flagyl", "") == 1.0


def test_precision_recall_f1():
    from backend.app.utils.text import calculate_precision_recall_f1

    # Perfect detection: 3 TP, 0 FP, 0 FN
    p, r, f1 = calculate_precision_recall_f1(3, 0, 0)
    assert p == 1.0 and r == 1.0 and f1 == 1.0

    # 2 TP, 1 FP, 1 FN
    p, r, f1 = calculate_precision_recall_f1(2, 1, 1)
    assert round(p, 2) == 0.67
    assert round(r, 2) == 0.67
    assert round(f1, 2) == 0.67



def test_critical_lasa_error_detection():
    # Losec vs Lasix is a high-risk LASA pair
    assert is_critical_medication_error("Losec", "Lasix") is True
    assert is_critical_medication_error("losec", "lasix") is True
    assert is_critical_medication_error("Daonil", "Daflon") is True
    assert is_critical_medication_error("Norvasc", "Navidoxine") is True

    # Same drug is NOT an error
    assert is_critical_medication_error("Losec", "Losec") is False

    # Completely different non-LASA pair
    assert is_critical_medication_error("Flagyl", "Cetal") is False


def test_evaluate_case_normal():
    gt = {
        "prescription": {"patient_age": "8 months", "patient_weight_kg": 8.14},
        "medications": [
            {
                "name": "Flagyl",
                "strength": "125mg/5ml",
                "dose": "3.5 ml",
                "frequency": "tid",
                "duration": "5 days",
                "confidence": 0.95,
                "status": "identified",
            }
        ],
    }

    pred = {
        "prescription": {"patient_age": "8 months", "patient_weight_kg": 8.14},
        "medications": [
            {
                "name": "Flagyl",
                "strength": "125mg/5ml",
                "dose": "3.5 ml",
                "frequency": "tid",
                "duration": "5 days",
                "confidence": 0.94,
                "status": "identified",
            }
        ],
    }

    case_metric = evaluate_case(
        case_id="test_01",
        ground_truth=gt,
        prediction=pred,
        provider="mock",
        model="test",
    )

    assert case_metric.medication_name_matches == 1
    assert case_metric.patient_age_correct is True
    assert case_metric.patient_weight_correct is True
    assert case_metric.critical_errors_count == 0


def test_evaluate_case_critical_lasa():
    gt = {
        "medications": [
            {
                "name": "Losec",
                "strength": "20mg",
                "dose": "1 capsule",
                "confidence": 0.95,
                "status": "identified",
            }
        ]
    }

    # Model falsely outputs Lasix with high confidence
    pred = {
        "medications": [
            {
                "name": "Lasix",
                "strength": "40mg",
                "dose": "1 tablet",
                "confidence": 0.95,
                "status": "identified",
            }
        ]
    }

    case_metric = evaluate_case(
        case_id="test_lasa",
        ground_truth=gt,
        prediction=pred,
        provider="mock",
        model="aggressive",
    )

    assert case_metric.critical_errors_count == 1
    assert case_metric.medication_name_matches == 0


@pytest.mark.asyncio
async def test_benchmark_runner_full_run():
    runner = BenchmarkRunner()
    cases = runner.discover_cases()
    assert len(cases) >= 5

    configs = [
        {"provider": "mock", "model": "mock-conservative-v1", "pipeline": "v1"},
        {"provider": "mock", "model": "mock-aggressive-v1", "pipeline": "v1"},
    ]

    results = await runner.run_benchmark(configs, use_cache=True)
    assert results["total_cases"] >= 5
    assert "mock:mock-conservative-v1 [V1]" in results["model_summaries"]
    assert "mock:mock-aggressive-v1 [V1]" in results["model_summaries"]

    conservative = results["model_summaries"]["mock:mock-conservative-v1 [V1]"]
    aggressive = results["model_summaries"]["mock:mock-aggressive-v1 [V1]"]

    # Conservative must have 0% critical errors
    assert conservative["critical_medication_error_rate"] == 0.0

    # Aggressive must trigger critical error detection
    assert aggressive["critical_medication_error_rate"] > 0.0

    # Markdown report generated
    md = runner.generate_markdown_report(results)
    assert "Benchmark Comparison Matrix" in md
    assert "Critical Error Rate" in md


def test_api_benchmark_cases():
    resp = client.get("/api/benchmark/cases")
    assert resp.status_code == 200
    data = resp.json()
    assert "cases" in data
    assert len(data["cases"]) >= 5


def test_api_benchmark_case_image():
    resp = client.get("/api/benchmark/case/rx_001/image")
    assert resp.status_code == 200
    assert resp.headers["content-type"] == "image/jpeg"


def test_api_benchmark_run_and_results():
    run_resp = client.post(
        "/api/benchmark/run",
        json={
            "models": [{"provider": "mock", "model": "mock-conservative-v1", "pipeline": "v1"}],
            "use_cache": True,
        },
    )
    assert run_resp.status_code == 200
    data = run_resp.json()
    assert data["status"] == "completed"
    assert "mock:mock-conservative-v1 [V1]" in data["results"]["model_summaries"]

    res_resp = client.get("/api/benchmark/results")
    assert res_resp.status_code == 200
    res_data = res_resp.json()
    assert "results" in res_data
    assert "markdown_report" in res_data
