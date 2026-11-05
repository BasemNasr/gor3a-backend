import os
import pytest
from unittest.mock import MagicMock, patch
from backend.app.services.supabase_service import SupabaseService
from backend.app.services.user_service import UserService
from backend.app.services.banner_service import BannerService


def test_supabase_service_unconfigured():
    svc = SupabaseService()
    svc.url = ""
    svc.key = ""
    assert not svc.is_configured()
    assert svc.fetch_all_users() is None
    assert svc.fetch_all_prescriptions() is None
    assert svc.fetch_all_banners() is None
    assert not svc.insert_user({"id": "1"})
    assert not svc.update_user("1", {})
    assert not svc.delete_user("1")


def test_supabase_service_configured_mock():
    svc = SupabaseService()
    svc.url = "https://example.supabase.co"
    svc.key = "fake-key"
    assert svc.is_configured()
    assert svc._headers()["apikey"] == "fake-key"
    assert "Bearer fake-key" in svc._headers()["Authorization"]

    mock_resp = MagicMock()
    mock_resp.is_success = True
    mock_resp.json.return_value = [{"id": "usr-1", "name": "أحمد"}]

    with patch("httpx.Client.get", return_value=mock_resp):
        users = svc.fetch_all_users()
        assert users == [{"id": "usr-1", "name": "أحمد"}]

    with patch("httpx.Client.post", return_value=mock_resp):
        assert svc.insert_user({"id": "usr-2"}) is True
        assert svc.insert_banner({"id": "ban-1"}) is True

    with patch("httpx.Client.patch", return_value=mock_resp):
        assert svc.update_user("usr-2", {"name": "جديد"}) is True
        assert svc.update_banner("ban-1", {"title": "جديد"}) is True

    with patch("httpx.Client.delete", return_value=mock_resp):
        assert svc.delete_user("usr-2") is True
        assert svc.delete_banner("ban-1") is True
        assert svc.delete_prescription("rx-1", "usr-1") is True


def test_user_service_sync_prescriptions_preserves_image_base64():
    with patch("backend.app.services.user_service.supabase_service") as mock_supa, \
         patch.object(UserService, "_load"), \
         patch.object(UserService, "_reload_if_needed"), \
         patch.object(UserService, "_save_prescriptions"):
        mock_supa.is_configured.return_value = False
        svc = UserService()

        synced = svc.sync_user_prescriptions("usr-test-1", [
            {
                "id": "rx_test_100",
                "title": "روشتة تجريبية",
                "doctorName": "د. أحمد",
                "date": "25/10/2026",
                "medicationsCount": 1,
                "medications": [{"englishName": "Panadol"}],
                "imageBase64": "YWJjMTIz",
                "createdAt": 1700000000000,
            }
        ])
        assert len(synced) == 1
        assert synced[0]["id"] == "rx_test_100"
        assert synced[0]["image_url"] == "YWJjMTIz"


def test_user_service_register_existing_user_smart_login():
    with patch("backend.app.services.user_service.supabase_service") as mock_supa, \
         patch.object(UserService, "_load"), \
         patch.object(UserService, "_reload_if_needed"), \
         patch.object(UserService, "_save_users"):
        mock_supa.is_configured.return_value = False
        svc = UserService()

        # 1. Register brand new user
        user, token, is_existing = svc.register_user(
            phone="01011112222",
            password="securePassword123",
            name="دكتور تجريبي",
            governorate="الجيزة",
            gender="male"
        )
        assert is_existing is False
        assert user["phone"] == "01011112222"
        assert user["name"] == "دكتور تجريبي"
        assert token is not None

        # 2. Register again with same phone and matching password -> seamless login!
        user2, token2, is_existing2 = svc.register_user(
            phone="01011112222",
            password="securePassword123",
            name="دكتور تجريبي",
            governorate="الجيزة",
            gender="male"
        )
        assert is_existing2 is True
        assert user2["id"] == user["id"]
        assert token2 is not None

        # 3. Register with same phone and wrong password -> raises ValueError
        with pytest.raises(ValueError, match="رقم الهاتف مسجل مسبقاً بالفعل"):
            svc.register_user(
                phone="01011112222",
                password="wrongPassword!",
                name="دكتور تجريبي",
            )

        # 4. Register with International phone number (+966 for Saudi Arabia)
        intl_user, intl_token, intl_existing = svc.register_user(
            phone="+966501234567",
            password="securePassword123",
            name="د. محمد الشمري",
            governorate="خارج مصر",
            gender="male"
        )
        assert intl_existing is False
        assert intl_user["phone"] == "+966501234567"
        assert intl_user["governorate"] == "خارج مصر"
        assert intl_token is not None




