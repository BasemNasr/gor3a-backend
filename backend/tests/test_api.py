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
