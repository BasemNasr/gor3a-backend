import json
import pytest
from fastapi.testclient import TestClient

from backend.app.dataset.bilingual_loader import bilingual_dataset_loader
from backend.app.main import app
from backend.app.training.few_shot_learner import few_shot_learner
from backend.app.training.fine_tune_exporter import fine_tune_exporter

client = TestClient(app)


def test_bilingual_loader_statistics():
    """Tests loading and partitioning of the Noor Alzoghby bilingual dataset."""
    stats = bilingual_dataset_loader.get_stats()
    assert stats["total_verified_cases"] >= 400
    assert stats["total_medications"] >= 1000
    assert stats["medications_with_arabic_instructions"] >= 500

    train = bilingual_dataset_loader.get_split("train")
    val = bilingual_dataset_loader.get_split("val")
    test = bilingual_dataset_loader.get_split("test")

    assert len(train) >= 300
    assert len(val) >= 50
    assert len(test) >= 50
    assert len(train) + len(val) + len(test) == stats["total_verified_cases"]

    # Verify Pydantic conversion
    sample_analysis = train[0].to_prescription_analysis()
    assert len(sample_analysis.medications) > 0
    assert sample_analysis.overall_confidence == 1.0


def test_few_shot_learner_exemplars():
    """Tests generating in-context learning demonstration blocks."""
    exemplars = few_shot_learner.select_curated_exemplars(k=3)
    assert len(exemplars) == 3

    prompt_text = few_shot_learner.generate_few_shot_prompt_text(k=2)
    assert "FEW-SHOT IN-CONTEXT EXAMPLES" in prompt_text
    assert "Medication:" in prompt_text
    assert "Instructions (AR):" in prompt_text


def test_fine_tune_exporter_sample(tmp_path):
    """Tests generating OpenAI Vision JSONL and VLM LoRA training manifests."""
    from backend.app.training.fine_tune_exporter import FineTuneDatasetExporter

    exporter = FineTuneDatasetExporter(exports_dir=tmp_path)
    res = exporter.export_all(max_cases=3)

    assert "openai_vision" in res
    assert "vlm_lora" in res

    # Check OpenAI JSONL format
    train_file = tmp_path / "openai_vision_train.jsonl"
    assert train_file.exists()
    with open(train_file, "r", encoding="utf-8") as f:
        lines = [line.strip() for line in f if line.strip()]
        assert len(lines) == 3
        first_obj = json.loads(lines[0])
        assert "messages" in first_obj
        assert first_obj["messages"][0]["role"] == "system"
        assert first_obj["messages"][1]["role"] == "user"
        assert first_obj["messages"][2]["role"] == "assistant"

    # Check VLM LoRA JSON format
    vlm_file = tmp_path / "vlm_lora_train.json"
    assert vlm_file.exists()
    with open(vlm_file, "r", encoding="utf-8") as f:
        vlm_data = json.load(f)
        assert len(vlm_data) == 3
        assert "conversations" in vlm_data[0]


def test_api_bilingual_stats_endpoint():
    """Tests /api/bilingual/stats."""
    resp = client.get("/api/bilingual/stats")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total_verified_cases"] >= 400
    assert data["total_registered_drugs_available"] >= 25000


def test_api_bilingual_cases_and_image():
    """Tests /api/bilingual/cases pagination and /api/bilingual/case/{case_id}/image."""
    resp = client.get("/api/bilingual/cases?limit=5")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total"] >= 400
    assert len(data["cases"]) == 5

    first_case = data["cases"][0]
    case_id = first_case["case_id"]

    img_resp = client.get(f"/api/bilingual/case/{case_id}/image")
    assert img_resp.status_code == 200
    assert img_resp.headers["content-type"] == "image/jpeg"


def test_api_bilingual_export_and_benchmark():
    """Tests /api/bilingual/export-training and /api/bilingual/benchmark/run."""
    exp_resp = client.post("/api/bilingual/export-training", json={"max_cases": 2})
    assert exp_resp.status_code == 200
    assert exp_resp.json()["status"] == "success"

    bench_resp = client.post(
        "/api/bilingual/benchmark/run",
        json={"provider": "mock", "pipeline_version": "v2", "max_cases": 2},
    )
    assert bench_resp.status_code == 200
    b_data = bench_resp.json()
    assert b_data["status"] == "completed"
    assert "metrics" in b_data["results"]
    assert b_data["results"]["evaluated_cases"] == 2
