import io
from fastapi.testclient import TestClient
from PIL import Image
from backend.app.main import app

client = TestClient(app)


def create_dummy_image_bytes() -> bytes:
    img = Image.new("RGB", (300, 400), color=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_health_check_endpoint():
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "Prescription AI" in data["service"]


def test_list_providers_endpoint():
    response = client.get("/api/providers")
    assert response.status_code == 200
    data = response.json()
    assert "providers" in data
    provider_ids = [p["id"] for p in data["providers"]]
    assert "gemini" in provider_ids
    assert "openai" in provider_ids
    assert "mock" in provider_ids


def test_analyze_with_mock_provider():
    image_bytes = create_dummy_image_bytes()
    files = {"file": ("rx_test.jpg", image_bytes, "image/jpeg")}
    data = {"provider": "mock"}

    response = client.post("/api/analyze", files=files, data=data)
    assert response.status_code == 200
    payload = response.json()

    assert "analysis" in payload
    assert "metadata" in payload
    assert payload["metadata"]["provider"] == "mock"
    assert len(payload["analysis"]["medications"]) > 0
    assert payload["analysis"]["prescription"]["patient_age"] == "8 months"


def test_analyze_invalid_file():
    files = {"file": ("bad.txt", b"plain text is not an image", "text/plain")}
    data = {"provider": "mock"}

    response = client.post("/api/analyze", files=files, data=data)
    assert response.status_code == 400
    assert "Invalid or corrupted image format" in response.json()["detail"]


def test_serve_static_index():
    response = client.get("/")
    assert response.status_code == 200
    assert "Prescription AI Lab" in response.text
    assert "Google Play" in response.text
    assert "App Store" in response.text
    assert "/static/screens/2.jpeg" in response.text
    assert 'href="/privacy"' in response.text
    assert 'href="/terms"' in response.text


def test_serve_privacy_and_terms_pages():
    privacy_res = client.get("/privacy")
    assert privacy_res.status_code == 200
    assert "Privacy Policy" in privacy_res.text
    assert "com.bn.gor3a" in privacy_res.text

    terms_res = client.get("/terms")
    assert terms_res.status_code == 200
    assert "Terms of Use" in terms_res.text
    assert "Medical Disclaimer" in terms_res.text


def test_serve_admin_with_scanner_tab():
    response = client.get("/admin")
    assert response.status_code == 200
    assert "tabScanner" in response.text
    assert "adminScannerTabBtn" in response.text
    assert "analyzeBtn" in response.text
    assert "loadSampleBtn" in response.text
    assert "medicationDetailModal" in response.text


def test_serve_mobile_screens_static():
    for screen_file in ["1.png", "2.jpeg", "3.jpeg", "4.jpeg", "5.jpeg", "6.jpeg"]:
        res = client.get(f"/static/screens/{screen_file}")
        assert res.status_code == 200
        assert len(res.content) > 1000
