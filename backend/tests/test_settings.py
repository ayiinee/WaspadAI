import pytest
from pydantic import ValidationError

from app.config import Settings


def test_production_rejects_mock_ai() -> None:
    with pytest.raises(ValidationError, match="AI_SERVICE_MODE must be remote"):
        Settings(app_env="production", ai_service_mode="mock")


def test_remote_requires_endpoint_and_key() -> None:
    with pytest.raises(ValidationError, match="remote AI mode requires"):
        Settings(ai_service_mode="remote", _env_file=None)


def test_production_requires_auth_and_database() -> None:
    with pytest.raises(ValidationError, match="production requires Supabase Auth"):
        Settings(
            _env_file=None,
            app_env="production",
            ai_service_mode="remote",
            ai_service_base_url="https://example.invalid",
            ai_service_api_key="test-key",
        )
