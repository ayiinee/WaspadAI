import pytest
from pydantic import ValidationError

from app.config import Settings


def test_production_rejects_mock_ai() -> None:
    with pytest.raises(ValidationError, match="AI_SERVICE_MODE must be remote"):
        Settings(app_env="production", ai_service_mode="mock")


def test_remote_requires_endpoint_and_key() -> None:
    with pytest.raises(ValidationError, match="remote AI mode requires"):
        Settings(
            ai_service_mode="remote",
            ai_service_base_url=None,
            ai_service_api_key=None,
            _env_file=None,
        )


def test_production_requires_auth_and_database() -> None:
    with pytest.raises(ValidationError, match="production requires Supabase Auth"):
        Settings(
            _env_file=None,
            app_env="production",
            ai_service_mode="remote",
            ai_service_base_url="https://example.invalid",
            ai_service_api_key="test-key",
            community_evidence_fixture_enabled=False,
            supabase_url=None,
            supabase_publishable_key=None,
            database_url=None,
        )


def test_production_rejects_community_evidence_fixture() -> None:
    with pytest.raises(
        ValidationError,
        match="COMMUNITY_EVIDENCE_FIXTURE_ENABLED must be false",
    ):
        Settings(
            _env_file=None,
            app_env="production",
            ai_service_mode="remote",
            ai_service_base_url="https://example.invalid",
            ai_service_api_key="test-key",
            supabase_url="https://example.supabase.co",
            supabase_publishable_key="test-publishable-key",
            database_url="postgresql://example",
            community_evidence_fixture_enabled=True,
        )
