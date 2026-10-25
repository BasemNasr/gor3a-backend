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
