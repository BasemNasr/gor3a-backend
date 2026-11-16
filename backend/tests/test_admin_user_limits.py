import os
import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.user_service import detect_client_platform, user_service
from backend.app.services.quota_service import quota_service
from backend.app.api.admin_routes import create_admin_token, ADMIN_DEFAULT_EMAIL

client = TestClient(app)


def test_user_daily_limit_and_stats():
    # 1. Register a test user
    test_phone = "01099998888"
    user_data = {
        "phone": test_phone,
        "password": "Password123!",
        "name": "كابتن ماجد",
        "gender": "male",
        "governorate": "القاهرة",
        "age": 28,
    }
    # Clean up any existing user with this phone
    user_service._users = [u for u in user_service._users if u.get("phone") != test_phone]
    user, token, _ = user_service.register_user(
        phone=test_phone,
        password="Password123!",
        name="كابتن ماجد",
        governorate="القاهرة",
        gender="male",
        age=28,
    )
    user_id = user["id"]

    # 2. Verify default limit is 5
    assert quota_service.get_user_daily_limit(user_id) == 5

    # 3. Authenticate admin
    admin_token = create_admin_token(ADMIN_DEFAULT_EMAIL)
    headers = {"Authorization": f"Bearer {admin_token}"}

    # 4. Check GET /api/admin/users
    resp = client.get("/api/admin/users", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["success"] is True
    assert "users" in data
    assert "stats" in data
    assert "total_scans_today" in data["stats"]
    assert "total_scans_month" in data["stats"]

    target_user = next((u for u in data["users"] if u["id"] == user_id), None)
    assert target_user is not None
    assert target_user.get("daily_limit") == 5
    assert "today_scans" in target_user
    assert "month_scans" in target_user

    # 5. Update user daily limit via admin endpoint
    put_resp = client.put(
        f"/api/admin/users/{user_id}/limit",
        headers=headers,
        json={"daily_limit": 15},
    )
    assert put_resp.status_code == 200
    put_data = put_resp.json()
    assert put_data["success"] is True
    assert put_data["user"]["daily_limit"] == 15

    # Verify quota service reflects new limit
    assert quota_service.get_user_daily_limit(user_id) == 15
    quota = quota_service.check_quota(user_id, "test_device_123")
    assert quota["max_scans"] == 15
    assert quota["allowed"] is True

    # 6. Clean up test user
    user_service._users = [u for u in user_service._users if u.get("id") != user_id]
    user_service._save_users()


def test_auto_platform_detection_without_app_update():
    assert detect_client_platform("okhttp/4.12.0") == "android"
    assert detect_client_platform("Gor3a/3 CFNetwork/1568.200.51 Darwin/24.1.0") == "ios"
    assert detect_client_platform("Mozilla/5.0 (Macintosh; Intel Mac OS X)") is None

    android_phone = "01099997771"
    ios_phone = "01099997772"
    user_service._users = [
        u for u in user_service._users if u.get("phone") not in (android_phone, ios_phone)
    ]

    reg_android = client.post(
        "/api/auth/register",
        headers={"User-Agent": "okhttp/4.12.0"},
        json={
            "phone": android_phone,
            "password": "Password123!",
            "name": "مستخدم أندرويد",
            "governorate": "الجيزة",
            "gender": "male",
            "age": 30,
        },
    )
    assert reg_android.status_code == 200
    android_uid = reg_android.json()["user"]["id"]
    assert reg_android.json()["user"]["platform"] == "android"

    reg_ios = client.post(
        "/api/auth/register",
        headers={"User-Agent": "iosApp/3 CFNetwork/1568.200.51 Darwin/24.1.0"},
        json={
            "phone": ios_phone,
            "password": "Password123!",
            "name": "مستخدم آيفون",
            "governorate": "القاهرة",
            "gender": "female",
            "age": 26,
        },
    )
    assert reg_ios.status_code == 200
    ios_uid = reg_ios.json()["user"]["id"]
    assert reg_ios.json()["user"]["platform"] == "ios"

    admin_token = create_admin_token(ADMIN_DEFAULT_EMAIL)
    admin_resp = client.get(
        "/api/admin/users",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert admin_resp.status_code == 200
    admin_data = admin_resp.json()
    u_map = {u["id"]: u for u in admin_data["users"]}
    assert u_map[android_uid]["platform"] == "android"
    assert u_map[ios_uid]["platform"] == "ios"
    assert admin_data["stats"]["platform_counts"]["android"] >= 1
    assert admin_data["stats"]["platform_counts"]["ios"] >= 1

    # Clean up test users & platform entries
    for uid in (android_uid, ios_uid):
        client.delete(f"/api/admin/users/{uid}", headers={"Authorization": f"Bearer {admin_token}"})
