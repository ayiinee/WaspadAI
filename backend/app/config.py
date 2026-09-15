from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

PROJECT_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Runtime configuration. Empty values in .env are treated as unavailable."""

    model_config = SettingsConfigDict(
        env_file=PROJECT_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_env: Literal["development", "test", "staging", "production"] = "development"
    app_port: int = 8001
    log_level: str = "INFO"
    public_base_url: str = "http://127.0.0.1:8001"
    ai_service_mode: Literal["mock", "remote"] = "mock"
    ai_service_base_url: str | None = None
    ai_service_api_key: SecretStr | None = None
    ai_service_connect_timeout_seconds: float = 5
    ai_service_pool_timeout_seconds: float = 5
    ai_service_write_timeout_seconds: float = 30
    ai_service_read_timeout_seconds: float = 120
    ai_service_deadline_seconds: float = 120
    product_request_deadline_seconds: float = 135
    ai_service_max_connections: int = 10
    ai_service_max_keepalive_connections: int = 5
    ai_service_max_concurrency: int = 4
    supabase_url: str | None = None
    supabase_publishable_key: SecretStr | None = None
    supabase_service_role_key: SecretStr | None = None
    supabase_auth_mode: Literal["get_user", "jwks"] = "get_user"
    supabase_jwt_issuer: str | None = None
    supabase_jwt_audience: str = "authenticated"
    database_url: SecretStr | None = None
    migration_database_url: SecretStr | None = None
    db_pool_size: int = 5
    db_max_overflow: int = 5
    db_statement_timeout_seconds: int = 15
    history_policy: Literal["REVIEW_REQUIRED", "ALL"] = "REVIEW_REQUIRED"
    history_retention_days: int = 90
    idempotency_cache_ttl_seconds: int = 600
    preview_ttl_seconds: int = 900
    signed_url_ttl_seconds: int = 300
    store_screenshots_enabled: bool = False
    screenshot_retention_hours: int = 24
    max_image_bytes: int = 8_000_000
    community_rag_sync_enabled: bool = False
    debug_enabled: bool = False

    @field_validator("*", mode="before")
    @classmethod
    def empty_string_is_none(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @model_validator(mode="after")
    def validate_remote_mode(self) -> Settings:
        if self.app_env == "production" and self.ai_service_mode != "remote":
            raise ValueError("AI_SERVICE_MODE must be remote in production")
        if self.ai_service_mode == "remote" and (
            not self.ai_service_base_url or self.ai_service_api_key is None
        ):
            raise ValueError("remote AI mode requires AI_SERVICE_BASE_URL and AI_SERVICE_API_KEY")
        if self.app_env == "production" and (
            not self.supabase_auth_is_configured or not self.database_is_configured
        ):
            raise ValueError("production requires Supabase Auth and DATABASE_URL")
        return self

    @property
    def supabase_auth_is_configured(self) -> bool:
        return self.supabase_url is not None and self.supabase_publishable_key is not None

    @property
    def database_is_configured(self) -> bool:
        return self.database_url is not None


@lru_cache
def get_settings() -> Settings:
    return Settings()
