import io
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from backend.app.drugs.clinical_knowledge import (
    get_clinical_knowledge,
    infer_prescription_overall_diagnosis,
)
from backend.app.drugs.packaging_image import generate_drug_package_svg
from backend.app.main import app

client = TestClient(app)


def create_dummy_image_bytes() -> bytes:
    img = Image.new("RGB", (300, 400), color=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_clinical_knowledge_curated_medications():
    """Validates rich clinical profile for top authentic Egyptian trade brands."""
    # Test 1: Augmentin
    aug = get_clinical_knowledge(drug_name="Augmentin 1gm", eda_id="EDA-00201")
    assert "مضاد حيوي" in aug["indication_short"]
    assert "التهاب" in aug["what_it_treats"]
    assert "عدوى بكتيرية" in aug["likely_diagnosis"]
    assert aug["image_url"] == "/api/drugs/EDA-00201/image"

    # Test 2: Antinal
    ant = get_clinical_knowledge(drug_name="Antinal susp", eda_id="EDA-00103")
    assert "مطهر معوي" in ant["indication_short"]
    assert "إسهال" in ant["what_it_treats"]
    assert "نزلة معوية" in ant["likely_diagnosis"]

    # Test 3: Flagyl
    fla = get_clinical_knowledge(drug_name="Flagyl", generic_name="Metronidazole", eda_id="EDA-00101")
    assert "طفيليات" in fla["what_it_does"] or "معوي" in fla["what_it_does"]
    assert "أميبا" in fla["what_it_treats"] or "معوي" in fla["what_it_treats"]
    assert "أميبا" in fla["likely_diagnosis"] or "معوي" in fla["likely_diagnosis"]

    # Test 4: Cataflam
    cat = get_clinical_knowledge(drug_name="Cataflam 50mg")
    assert "مسكن" in cat["indication_short"]
    assert "ألم" in cat["likely_diagnosis"] or "التهاب" in cat["likely_diagnosis"]


def test_clinical_knowledge_generic_and_class_fallbacks():
    """Validates fallback inference using generic substance and therapeutic class."""
    # Active substance fallback
    paracetamol_info = get_clinical_knowledge(generic_name="Paracetamol 500mg")
    assert "مسكن" in paracetamol_info["indication_short"] or "خافض" in paracetamol_info["indication_short"]

    # Class fallback
    cardio_info = get_clinical_knowledge(drug_class="ANTIHYPERTENSIVE")
    assert "علاج" in cardio_info["indication_short"] or "مستحضر" in cardio_info["indication_short"]


def test_infer_prescription_overall_diagnosis():
    """Tests holistic physician diagnosis inference across co-prescribed medicines."""
    # Combo 1: Gastroenteritis (Flagyl + Antinal + Cetal)
    gi_meds = [
        {"name": "Flagyl", "raw_text": "Flagyl susp", "likely_diagnosis": "أميبا"},
        {"name": "Antinal", "raw_text": "Antinal susp", "likely_diagnosis": "إسهال"},
        {"name": "Cetal", "raw_text": "Cetal 250", "likely_diagnosis": "حمى"},
    ]
    gi_diag = infer_prescription_overall_diagnosis(gi_meds)
    assert "نزلة معوية" in gi_diag

    # Combo 2: Acute Respiratory Infection (Augmentin + Congestal + Allvent)
    resp_meds = [
        {"name": "Augmentin", "raw_text": "Augmentin 1g", "likely_diagnosis": "عدوى بكتيرية"},
        {"name": "Congestal", "raw_text": "Congestal tab", "likely_diagnosis": "نزلة برد"},
        {"name": "Allvent", "raw_text": "Allvent syr", "likely_diagnosis": "كحة وبلغم"},
    ]
    resp_diag = infer_prescription_overall_diagnosis(resp_meds)
    assert "تنفس" in resp_diag or "برد" in resp_diag or "بكتيرية" in resp_diag


def test_generate_drug_package_svg():
    """Tests SVG packaging generation for various dosage forms."""
    svg_tablet = generate_drug_package_svg(
        commercial_name_en="Augmentin",
        commercial_name_ar="أوجمنتين",
        strength="1g",
        dosage_form="Tablet",
        generic_name="Amoxicillin + Clavulanic Acid",
        category_slug="ANTIBIOTICS",
        eda_id="EDA-REG-10492",
        price_egp=110.0,
    )
    assert "<svg" in svg_tablet
    assert "</svg>" in svg_tablet
    assert "Augmentin" in svg_tablet
    assert "أوجمنتين" in svg_tablet
    assert "1g" in svg_tablet

    # Test syrup dosage form
    svg_syr = generate_drug_package_svg(
        commercial_name_en="Allvent",
        commercial_name_ar="ألفنت",
        strength="120ml",
        dosage_form="Suspension",
        category_slug="RESPIRATORY",
    )
    assert "<svg" in svg_syr
    assert "Allvent" in svg_syr


def test_api_drug_image_endpoints():
    """Tests GET /api/drugs/{drug_id}/image and GET /api/drugs/image-by-name endpoints."""
    # 1. By ID
    res_id = client.get("/api/drugs/EDA-00101/image")
    assert res_id.status_code == 200
    assert "image/" in res_id.headers["content-type"]

    # 2. By Name
    res_name = client.get("/api/drugs/image-by-name?name=Panadol&form=Tablet&strength=500mg")
    assert res_name.status_code == 200
    assert "image/svg+xml" in res_name.headers["content-type"]
    assert "Panadol" in res_name.text


def test_api_drug_clinical_info_endpoint():
    """Tests GET /api/drugs/{drug_id}/clinical-info endpoint."""
    res = client.get("/api/drugs/EDA-00101/clinical-info")
    assert res.status_code == 200
    data = res.json()
    assert "indication_short" in data
    assert "what_it_treats" in data
    assert "likely_diagnosis" in data


def test_api_analyze_includes_clinical_indications_and_images():
    """Tests that /api/analyze enriches each medication and overall prescription diagnosis."""
    image_bytes = create_dummy_image_bytes()
    files = {"file": ("rx_test.jpg", image_bytes, "image/jpeg")}
    data = {"provider": "mock", "pipeline_version": "v2"}

    response = client.post("/api/analyze", files=files, data=data)
    assert response.status_code == 200
    res_data = response.json()

    analysis = res_data["analysis"]
    meds = analysis["medications"]
    assert len(meds) > 0

    first_med = meds[0]
    assert "clinical_indication" in first_med
    assert first_med["clinical_indication"] is not None
    assert "likely_diagnosis" in first_med
    assert "image_url" in first_med
    assert first_med["image_url"].startswith(("/api/drugs/", "http://", "https://"))

    # Overall prescription diagnosis
    assert "overall_likely_diagnosis" in analysis
    assert analysis["overall_likely_diagnosis"] is not None
