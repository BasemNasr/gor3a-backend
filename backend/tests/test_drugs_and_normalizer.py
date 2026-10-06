import io
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from backend.app.drugs.models import Drug
from backend.app.drugs.repository import DrugRepository, drug_repository
from backend.app.main import app
from backend.app.services.normalizer import DrugNormalizer
from backend.app.services.pipeline_v2 import PrescriptionPipelineV2

client = TestClient(app)


def test_drug_repository_indexing_and_lookup():
    """Tests the in-memory Egyptian Drug Authority repository indexing and retrieval."""
    assert drug_repository.get_total_count() >= 20

    # Exact commercial lookup
    flagyl = drug_repository.get_by_name("Flagyl")
    assert flagyl is not None
    assert flagyl.commercial_name_en == "Flagyl"
    assert "Metronidazole" in flagyl.active_ingredients
    assert flagyl.dosage_form == "Suspension"

    # Arabic commercial lookup
    cetal_ar = drug_repository.get_by_name("سيتال")
    assert cetal_ar is not None
    assert cetal_ar.commercial_name_en == "Cetal"

    # Alias lookup
    antinal = drug_repository.get_by_name("انتينال")
    assert antinal is not None
    assert antinal.commercial_name_en == "Antinal"


def test_drug_repository_fuzzy_search():
    """Tests fuzzy search capability across Egyptian pharmaceutical brand names."""
    # Slight misspelling: "Flagil" -> "Flagyl"
    results = drug_repository.search_candidates("Flagil", limit=3)
    assert len(results) > 0
    top_drug, score = results[0]
    assert top_drug.commercial_name_en == "Flagyl"
    assert score > 0.80

    # Arabic query
    ar_results = drug_repository.search_candidates("فلاجيل", limit=3)
    assert len(ar_results) > 0
    top_drug_ar, ar_score = ar_results[0]
    assert top_drug_ar.commercial_name_en == "Flagyl"
    assert ar_score >= 0.90


def test_drug_normalizer_exact_confirmation():
    """Tests normalizer confirming exact or high-confidence match with EDA registration."""
    normalizer = DrugNormalizer(drug_repository)

    res = normalizer.normalize(
        raw_text="Flagyl 125mg/5ml susp",
        predicted_name="Flagyl",
        extracted_strength="125mg/5ml",
        initial_confidence=0.85,
    )

    assert res.status == "identified"
    assert res.confirmed_drug is not None
    assert res.confirmed_drug.name == "Flagyl"
    assert res.confirmed_drug.drug_id == "EDA-00101"
    assert res.calibrated_confidence >= 0.85
    assert len(res.top_candidates) > 0


def test_drug_normalizer_preserves_uncertainty_for_fuzzy_matches():
    """Ensures safety constraint: fuzzy matches < 0.88 or uncertain vision are NOT auto-confirmed."""
    normalizer = DrugNormalizer(drug_repository)

    # Ambiguous/garbled text that partially matches Curam (score ~ 0.80)
    res = normalizer.normalize(
        raw_text="Curame tab",
        predicted_name="Curame",
        extracted_strength="1g",
        initial_confidence=0.80,
    )

    assert res.status == "uncertain"
    # Clinical Safety Rule: Do NOT auto-confirm ambiguous handwriting
    assert res.confirmed_drug is None
    assert len(res.top_candidates) > 0
    assert any("Preserved as uncertain" in reason for reason in res.uncertainty_reasons)


def test_drug_normalizer_illegible_query():
    """Tests normalizer handling completely illegible or empty text."""
    normalizer = DrugNormalizer(drug_repository)

    res = normalizer.normalize(raw_text="?", predicted_name=None)
    assert res.status == "unreadable"
    assert res.confirmed_drug is None
    assert res.calibrated_confidence <= 0.25


def test_drug_normalizer_flags_critical_lasa_drugs():
    """Tests clinical alert generation for Look-Alike / Sound-Alike drug risks."""
    normalizer = DrugNormalizer(drug_repository)

    # Test Losec (risk of confusion with Lasix)
    res_losec = normalizer.normalize(
        raw_text="Losec 20mg",
        predicted_name="Losec",
        extracted_strength="20mg",
        initial_confidence=0.90,
    )
    assert res_losec.confirmed_drug is not None
    assert res_losec.confirmed_drug.safety_warning is not None
    assert "Lasix" in res_losec.confirmed_drug.safety_warning

    # Test Lasix (risk of confusion with Losec)
    res_lasix = normalizer.normalize(
        raw_text="Lasix 40mg",
        predicted_name="Lasix",
        extracted_strength="40mg",
        initial_confidence=0.90,
    )
    assert res_lasix.confirmed_drug is not None
    assert res_lasix.confirmed_drug.safety_warning is not None
    assert "Losec" in res_lasix.confirmed_drug.safety_warning


@pytest.mark.asyncio
async def test_pipeline_v2_end_to_end():
    """Tests Pipeline V2 executing vision extraction + EDA normalization."""
    # Create test image in memory
    img = Image.new("RGB", (400, 300), color=(250, 248, 240))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    img_bytes = buf.getvalue()

    pipeline = PrescriptionPipelineV2()
    response = await pipeline.process_prescription(
        image_bytes=img_bytes,
        filename="test_pediatric_rx.jpg",
        provider_name="mock",
        model_name="mock-conservative-v1",
    )

    analysis = response.analysis
    assert len(analysis.medications) == 3

    # Check that medications received EDA fields and pipeline_version "v2"
    first_med = analysis.medications[0]
    assert first_med.pipeline_version == "v2"
    assert first_med.name == "Flagyl"
    assert first_med.eda_id == "EDA-00101"
    assert len(first_med.candidates) > 0

    # Check unreadable item preserved
    third_med = analysis.medications[2]
    assert third_med.status in ("uncertain", "unreadable")


def test_api_drugs_search_endpoint():
    """Tests the /api/drugs/search endpoint."""
    resp = client.get("/api/drugs/search?q=cetal")
    assert resp.status_code == 200
    data = resp.json()
    assert data["query"] == "cetal"
    assert data["count"] > 0
    assert data["results"][0]["drug"]["commercial_name_en"] == "Cetal"
    assert data["results"][0]["similarity_score"] > 0.85


def test_api_drugs_count_endpoint():
    """Tests the /api/drugs/count endpoint."""
    resp = client.get("/api/drugs/count")
    assert resp.status_code == 200
    data = resp.json()
    assert data["total_registered_drugs"] >= 20


def test_api_analyze_v1_vs_v2():
    """Tests /api/analyze routing for both pipeline_version='v1' and 'v2'."""
    img = Image.new("RGB", (200, 200), color=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    img_bytes = buf.getvalue()

    # Call with V2 (default)
    resp_v2 = client.post(
        "/api/analyze",
        data={"provider": "mock", "model": "mock-conservative-v1", "pipeline_version": "v2"},
        files={"file": ("rx.jpg", img_bytes, "image/jpeg")},
    )
    assert resp_v2.status_code == 200
    v2_data = resp_v2.json()
    med_v2 = v2_data["analysis"]["medications"][0]
    assert med_v2["pipeline_version"] == "v2"
    assert med_v2["eda_id"] is not None

    # Call with V1 (vision only)
    resp_v1 = client.post(
        "/api/analyze",
        data={"provider": "mock", "model": "mock-conservative-v1", "pipeline_version": "v1"},
        files={"file": ("rx.jpg", img_bytes, "image/jpeg")},
    )
    assert resp_v1.status_code == 200
    v1_data = resp_v1.json()
    med_v1 = v1_data["analysis"]["medications"][0]
    assert med_v1["pipeline_version"] == "v1"
