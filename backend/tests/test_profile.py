from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

import app.repositories.postgres.profile_repository as profile_repository_module
from app.auth import AuthenticatedUser, get_current_user
from app.main import create_app
from app.repositories.postgres.profile_repository import PostgresProfileRepository
from app.schemas.profile import ProfileResponse


def test_profile_requires_authentication() -> None:
    with TestClient(create_app()) as client:
        response = client.get("/api/v1/me/profile")

    assert response.status_code == 401


def test_profile_route_returns_authenticated_identity(monkeypatch: pytest.MonkeyPatch) -> None:
    user_id = uuid4()

    async def fake_get_profile(*_args: Any, **_kwargs: Any) -> ProfileResponse:
        return ProfileResponse(
            user_id=user_id,
            email="user@example.com",
            display_name="Pengguna Uji",
            bio="Bio aman",
            avatar_url=None,
            created_at=datetime.now(UTC),
        )

    monkeypatch.setattr("app.api.v1.routes.profile.get_profile", fake_get_profile)
    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(
        user_id,
        "user@example.com",
    )

    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.get("/api/v1/me/profile")
        app.state.db_pool = None

    assert response.status_code == 200
    assert response.json()["user_id"] == str(user_id)
    assert response.json()["email"] == "user@example.com"


def test_profile_patch_rejects_privileged_fields(monkeypatch: pytest.MonkeyPatch) -> None:
    user_id = uuid4()
    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)

    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.patch(
            "/api/v1/me/profile",
            json={"display_name": "Nama Baru", "role": "ADMIN", "is_active": True},
        )
        app.state.db_pool = None

    assert response.status_code == 422


def test_publication_query_types_nullable_status(monkeypatch: pytest.MonkeyPatch) -> None:
    class Cursor:
        async def fetchall(self) -> list[dict[str, Any]]:
            return []

    class Connection:
        query = ""
        parameters: tuple[Any, ...] = ()

        async def execute(self, query: str, parameters: tuple[Any, ...]) -> Cursor:
            self.query = query
            self.parameters = parameters
            return Cursor()

    connection = Connection()

    @asynccontextmanager
    async def fake_transaction(*_args: Any, **_kwargs: Any):
        yield connection

    monkeypatch.setattr(profile_repository_module, "user_transaction", fake_transaction)
    repository = PostgresProfileRepository(object(), 5)  # type: ignore[arg-type]
    user_id = uuid4()

    import asyncio

    result = asyncio.run(repository.list_publications(user_id, 20, 0, None))

    assert result == []
    assert "%s::text is null" in connection.query
    assert connection.parameters == (user_id, None, None, 21, 0)
