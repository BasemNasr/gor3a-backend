import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from fastapi.testclient import TestClient

from backend.app.main import app
from backend.app.services.gemini_key_manager import GeminiKeyManager, mask_key
from backend.app.providers.gemini import GeminiProvider


def test_mask_key():
    assert mask_key("") == "لم يتم التعيين"
    assert mask_key(None) == "لم يتم التعيين"
    assert mask_key("short") == "••••••••"
    key = "AIzaSyD-1234567890abcdef-XYZ"
    masked = mask_key(key)
    assert masked.startswith("AIzaSy")
    assert masked.endswith("-XYZ")
    assert "••••" in masked
    assert "1234567890" not in masked


def test_key_manager_crud_and_tokens(tmp_path):
    # Isolated manager with tmp path
    with patch("backend.app.services.gemini_key_manager.DEFAULT_KEYS_PATH", tmp_path / "keys.json"), \
         patch("backend.app.services.gemini_key_manager.TMP_KEYS_PATH", tmp_path / "tmp_keys.json"):
        km = GeminiKeyManager()
        km._keys = []  # start clean

        # 1. Add primary key
        k1 = km.add_key("AIzaSyKey1_DummyMockKey123", label="مفتاح أساسي 1", daily_limit=100)
        assert k1["is_primary"] is True
        assert k1["requests_today"] == 0
        assert k1["remaining_requests_today"] == 100
        assert k1["usage_percentage"] == 0.0

        # 2. Add secondary backup key
        k2 = km.add_key("AIzaSyKey2_DummyMockKey456", label="مفتاح احتياطي 2", daily_limit=200)
        assert k2["is_primary"] is False
        assert k2["remaining_requests_today"] == 200

        # 3. Usable keys returns healthy keys with primary first
        usable = km.get_usable_keys()
        assert len(usable) == 2
        assert usable[0]["id"] == k1["id"]

        # 4. Record success with tokens
        km.record_success(k1["id"], prompt_tokens=150, completion_tokens=50)
        summary = km.get_pool_summary()
        key1_info = next(k for k in summary["keys"] if k["id"] == k1["id"])
        assert key1_info["requests_today"] == 1
        assert key1_info["remaining_requests_today"] == 99
        assert key1_info["usage_percentage"] == 1.0
        assert key1_info["tokens_today"] == 200
        assert key1_info["total_tokens"] == 200

        # 5. Record 429 Rate Limit failure on k1
        km.record_failure(k1["id"], 429, "Resource has been exhausted (e.g. check quota).")
        key1_after_fail = next(k for k in km.get_pool_summary()["keys"] if k["id"] == k1["id"])
        assert key1_after_fail["status"] in ("rate_limited", "exhausted")

        # 6. Now get_usable_keys should prioritize k2 (the healthy key!)
        usable_now = km.get_usable_keys()
        assert usable_now[0]["id"] == k2["id"]

        # 7. Reset stats
        km.reset_key_stats(k1["id"])
        key1_reset = next(k for k in km.get_pool_summary()["keys"] if k["id"] == k1["id"])
        assert key1_reset["status"] == "healthy"
        assert key1_reset["requests_today"] == 0


@pytest.mark.asyncio
async def test_gemini_provider_auto_failover_on_429():
    """Verifies that if Key 1 hits HTTP 429, GeminiProvider automatically rotates to Key 2 seamlessly."""
    km = GeminiKeyManager()
    km._keys = [
        {
            "id": "key_first",
            "key": "AIzaSyFirstKey_ExhaustedMock",
            "label": "المفتاح الأول (سينفد)",
            "is_active": True,
            "is_primary": True,
            "status": "healthy",
            "daily_limit_requests": 1500,
            "requests_today": 0,
            "tokens_today": 0,
            "prompt_tokens_today": 0,
            "completion_tokens_today": 0,
            "total_requests": 0,
            "total_tokens": 0,
            "failed_requests": 0,
            "consecutive_failures": 0,
            "daily_date": "2026-11-12",
        },
        {
            "id": "key_second",
            "key": "AIzaSySecondKey_SuccessMock",
            "label": "المفتاح الثاني (سينجح)",
            "is_active": True,
            "is_primary": False,
            "status": "healthy",
            "daily_limit_requests": 1500,
            "requests_today": 0,
            "tokens_today": 0,
            "prompt_tokens_today": 0,
            "completion_tokens_today": 0,
            "total_requests": 0,
            "total_tokens": 0,
            "failed_requests": 0,
            "consecutive_failures": 0,
            "daily_date": "2026-11-12",
        },
    ]

    provider = GeminiProvider()

    # Mock httpx responses:
    # First request with key_first -> returns 429 Too Many Requests
    # Second request with key_second -> returns 200 OK with valid prescription JSON
    mock_success_json = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": '{"prescription": {"diagnosis": "Test Diagnosis", "medications": []}}'
                        }
                    ]
                }
            }
        ],
        "usageMetadata": {
            "promptTokenCount": 240,
            "candidatesTokenCount": 60,
            "totalTokenCount": 300,
        },
    }

    call_keys_seen = []

    async def mock_post(url, headers=None, json=None):
        api_k = headers.get("X-goog-api-key")
        call_keys_seen.append(api_k)
        mock_resp = MagicMock()
        if api_k == "AIzaSyFirstKey_ExhaustedMock":
            mock_resp.status_code = 429
            mock_resp.text = '{"error": {"code": 429, "message": "Resource has been exhausted"}}'
        else:
            mock_resp.status_code = 200
            mock_resp.json.return_value = mock_success_json
        return mock_resp

    with patch("backend.app.providers.gemini._get_key_manager", return_value=km), \
         patch("httpx.AsyncClient.post", side_effect=mock_post):
        
        result = await provider.analyze(
            image_bytes=b"dummy_image_data",
            mime_type="image/jpeg",
            prompt="Analyze prescription",
        )

        # Verification:
        # 1. Both keys were attempted (Key 1 -> 429 -> Key 2 -> 200)
        assert len(call_keys_seen) == 2
        assert call_keys_seen[0] == "AIzaSyFirstKey_ExhaustedMock"
        assert call_keys_seen[1] == "AIzaSySecondKey_SuccessMock"

        # 2. Result succeeded without exception
        assert result.provider == "gemini"
        assert result.parsed_json["prescription"]["diagnosis"] == "Test Diagnosis"
        assert result.usage.total_tokens == 300

        # 3. Key 1 is marked as rate_limited / exhausted
        k1 = next(k for k in km._keys if k["id"] == "key_first")
        assert k1["status"] in ("rate_limited", "exhausted")

        # 4. Key 2 recorded the successful request and 300 tokens
        k2 = next(k for k in km._keys if k["id"] == "key_second")
        assert k2["requests_today"] == 1
        assert k2["tokens_today"] == 300


def test_admin_keys_api_routes():
    """Tests the admin REST endpoints for keys pool management."""
    client = TestClient(app)

    # 1. Login as admin
    login_res = client.post(
        "/api/admin/login",
        json={"email": "basemads20@gmail.com", "password": "G0r3a@Admin#2026$Rx!"},
    )
    assert login_res.status_code == 200
    token = login_res.json()["token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. List keys
    list_res = client.get("/api/admin/keys", headers=headers)
    assert list_res.status_code == 200
    pool = list_res.json()
    assert "keys" in pool
    assert "total_capacity_today" in pool
    assert "auto_failover_enabled" in pool
    assert pool["auto_failover_enabled"] is True

    # 3. Add a new key
    add_res = client.post(
        "/api/admin/keys",
        headers=headers,
        json={
            "api_key": "AIzaSyAdminTestKey_999988887777",
            "label": "مفتاح اختبار الأدمن",
            "daily_limit_requests": 1500,
        },
    )
    assert add_res.status_code == 200
    added = add_res.json()
    assert added["success"] is True
    new_key_id = added["key"]["id"]
    assert added["key"]["masked_key"].startswith("AIzaSy")

    # 4. Set as primary
    set_p_res = client.post(f"/api/admin/keys/{new_key_id}/set-primary", headers=headers)
    assert set_p_res.status_code == 200
    assert set_p_res.json()["success"] is True

    # 5. Toggle active
    toggle_res = client.post(f"/api/admin/keys/{new_key_id}/toggle", headers=headers)
    assert toggle_res.status_code == 200
    assert toggle_res.json()["key"]["is_active"] is False

    # Toggle back on
    toggle_res2 = client.post(f"/api/admin/keys/{new_key_id}/toggle", headers=headers)
    assert toggle_res2.status_code == 200
    assert toggle_res2.json()["key"]["is_active"] is True

    # 6. Reset stats
    reset_res = client.post(f"/api/admin/keys/{new_key_id}/reset-stats", headers=headers)
    assert reset_res.status_code == 200
    assert reset_res.json()["key"]["requests_today"] == 0

    # 7. Delete key
    del_res = client.delete(f"/api/admin/keys/{new_key_id}", headers=headers)
    assert del_res.status_code == 200
    assert del_res.json()["success"] is True
