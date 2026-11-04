import io
import uuid
from fastapi.testclient import TestClient
from PIL import Image
from backend.app.main import app
from backend.app.services.user_service import create_user_token
from backend.app.services.quota_service import quota_service, DAILY_SCAN_LIMIT

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


def test_analyze_legacy_compatibility_and_strict_toggle():
    from backend.app.config import settings
    image_bytes = create_dummy_image_bytes()
    files = {"file": ("rx_test.jpg", image_bytes, "image/jpeg")}
    data = {"provider": "mock"}

    # 1. When legacy compatibility is enabled (True) -> old app works!
    settings.allow_legacy_guest_scans = True
    res_legacy = client.post("/api/analyze", files=files, data=data)
    assert res_legacy.status_code == 200

    # 2. When legacy compatibility is disabled (False) -> strictly requires login 401
    settings.allow_legacy_guest_scans = False
    res_strict = client.post("/api/analyze", files=files, data=data)
    assert res_strict.status_code == 401
    assert "تسجيل الدخول" in res_strict.json()["detail"]

    # Restore default
    settings.allow_legacy_guest_scans = True


def test_analyze_with_mock_provider():
    image_bytes = create_dummy_image_bytes()
    files = {"file": ("rx_test.jpg", image_bytes, "image/jpeg")}
    data = {"provider": "mock"}
    token = create_user_token("user_test_valid", "01099998888")
    headers = {"Authorization": f"Bearer {token}", "X-Device-Id": "device_test_valid_1"}

    response = client.post("/api/analyze", files=files, data=data, headers=headers)
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
    token = create_user_token("user_test_valid", "01099998888")
    headers = {"Authorization": f"Bearer {token}", "X-Device-Id": "device_test_valid_2"}

    response = client.post("/api/analyze", files=files, data=data, headers=headers)
    assert response.status_code == 400
    assert "Invalid or corrupted image format" in response.json()["detail"]


def test_quota_endpoint_and_limit_enforcement():
    unique_user = f"user_quota_test_{uuid.uuid4().hex[:8]}"
    token = create_user_token(unique_user, "01011112222")
    dev_id = f"device_quota_test_{uuid.uuid4().hex[:8]}"
    headers = {"Authorization": f"Bearer {token}", "X-Device-Id": dev_id}

    # 1. Check initial quota
    res_quota = client.get(f"/api/scans/quota?device_id={dev_id}", headers=headers)
    assert res_quota.status_code == 200
    q_data = res_quota.json()
    assert q_data["allowed"] is True
    assert q_data["max_scans"] == 5
    assert q_data["whatsapp_number"] == "+201064290486"

    # 2. Consume remaining quota up to limit
    for _ in range(5):
        quota_service.record_scan(unique_user, dev_id)

    # 3. Check quota after limit reached
    res_exceeded = client.get(f"/api/scans/quota?device_id={dev_id}", headers=headers)
    assert res_exceeded.status_code == 200
    assert res_exceeded.json()["allowed"] is False
    assert res_exceeded.json()["scans_remaining"] == 0

    # 4. Attempt to analyze when quota exceeded -> 429
    image_bytes = create_dummy_image_bytes()
    files = {"file": ("rx_test.jpg", image_bytes, "image/jpeg")}
    data = {"provider": "mock"}
    res_block = client.post("/api/analyze", files=files, data=data, headers=headers)
    assert res_block.status_code == 429
    detail = res_block.json()["detail"]
    assert detail["error"] == "DAILY_QUOTA_EXCEEDED"
    assert detail["whatsapp_number"] == "+201064290486"
    assert "https://wa.me/201064290486" in detail["whatsapp_link"]

    # 5. Anti-Abuse Test: Same device, different user -> STILL BLOCKED!
    another_user = f"brand_new_user_{uuid.uuid4().hex[:8]}"
    another_token = create_user_token(another_user, "01033334444")
    another_headers = {"Authorization": f"Bearer {another_token}", "X-Device-Id": dev_id}
    res_anti_abuse = client.post("/api/analyze", files=files, data=data, headers=another_headers)
    assert res_anti_abuse.status_code == 429
    assert res_anti_abuse.json()["detail"]["error"] == "DAILY_QUOTA_EXCEEDED"


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
