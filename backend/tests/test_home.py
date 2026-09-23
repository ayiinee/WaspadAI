from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

import app.repositories.postgres.home_repository as home_repository_module
from app.auth import AuthenticatedUser, get_current_user
from app.domain.home.models import (
    HomeCase,
    HomeLearningRecommendation,
    HomeProfile,
    HomeSnapshot,
)
from app.main import create_app
from app.repositories.postgres.home_repository import PostgresHomeRepository
from app.schemas.home import HomeResponse
from app.services.home_service import get_home_dashboard


class FakeCursor:
    def __init__(self, rows: list[dict[str, Any]]) -> None:
        self._rows = rows

    async def fetchone(self) -> dict[str, Any] | None:
        return self._rows[0] if self._rows else None

    async def fetchall(self) -> list[dict[str, Any]]:
        return self._rows


class FakeConnection:
    def __init__(self, responses: list[list[dict[str, Any]]]) -> None:
        self._responses = iter(responses)
        self.executed: list[tuple[str, tuple[Any, ...]]] = []

    async def execute(self, query: str, parameters: tuple[Any, ...]) -> FakeCursor:
        self.executed.append((query, parameters))
        return FakeCursor(next(self._responses))


def test_home_requires_bearer_token() -> None:
    with TestClient(create_app()) as client:
        response = client.get("/api/v1/home")

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_ACCESS_TOKEN"


def test_home_route_delegates_to_service(monkeypatch: pytest.MonkeyPatch) -> None:
    user_id = uuid4()
    now = datetime.now(UTC)

    async def fake_dashboard(*_args: Any, **_kwargs: Any) -> HomeResponse:
        return HomeResponse(
            profile={"display_name": "Putu Alvin"},
            recent_cases=[
                {
                    "case_id": uuid4(),
                    "title": "Undangan APK berbahaya",
                    "summary": "File APK tidak berasal dari kanal resmi.",
                    "verdict": "UNVERIFIED",
                    "risk_level": "HIGH",
                    "requires_human_review": True,
                    "created_at": now,
                }
            ],
            learning_recommendations=[],
        )

    monkeypatch.setattr("app.api.v1.routes.home.get_home_dashboard", fake_dashboard)
    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(
        user_id,
        "putu@example.com",
    )

    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.get("/api/v1/home")
        app.state.db_pool = None

    assert response.status_code == 200
    assert response.json()["profile"]["display_name"] == "Putu Alvin"
    assert response.json()["recent_cases"][0]["risk_level"] == "HIGH"


def test_home_service_maps_domain_snapshot() -> None:
    user_id = uuid4()
    case_id = uuid4()
    module_id = uuid4()
    now = datetime.now(UTC)

    class FakeRepository:
        async def load(self, *_args: Any, **_kwargs: Any) -> HomeSnapshot:
            return HomeSnapshot(
                profile=HomeProfile("Putu Alvin"),
                recent_cases=[
                    HomeCase(
                        case_id=case_id,
                        title="Kasus uji",
                        summary="Ringkasan aman untuk halaman beranda.",
                        verdict="SUPPORTED",
                        risk_level="LOW",
                        requires_human_review=False,
                        created_at=now,
                    )
                ],
                learning_recommendations=[
                    HomeLearningRecommendation(
                        module_id=module_id,
                        title="Kenali Link Palsu",
                        summary="Pelajari ciri tautan mencurigakan.",
                        image_url="/api/v1/learning/media/learning/phishing.jpg",
                        progress_percent=50.0,
                    )
                ],
            )

    result = asyncio.run(get_home_dashboard(FakeRepository(), user_id, "putu@example.com"))

    assert result.profile.display_name == "Putu Alvin"
    assert result.recent_cases[0].case_id == case_id
    assert result.learning_recommendations[0].module_id == module_id
    assert result.learning_recommendations[0].progress_percent == 50.0


def test_postgres_home_repository_uses_scoped_queries(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_id = uuid4()
    case_id = uuid4()
    module_id = uuid4()
    now = datetime.now(UTC)
    connection = FakeConnection(
        responses=[
            [{"display_name": "Putu Alvin"}],
            [
                {
                    "id": case_id,
                    "headline": "Kasus terbaru",
                    "summary": "Alasan utama pemeriksaan.",
                    "verdict": "UNVERIFIED",
                    "risk_level": "HIGH",
                    "requires_human_review": True,
                    "created_at": now,
                }
            ],
            [
                {
                    "id": module_id,
                    "title": "Kenali Modus Penipuan",
                    "summary": "Materi rekomendasi.",
                    "image_url": None,
                    "completed_lessons": 1,
                    "total_lessons": 2,
                }
            ],
        ]
    )

    @asynccontextmanager
    async def transaction(*_args: Any, **_kwargs: Any) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(home_repository_module, "user_transaction", transaction)
    repository = PostgresHomeRepository(
        pool=object(),  # type: ignore[arg-type]
        statement_timeout_seconds=15,
    )

    result = asyncio.run(repository.load(user_id, case_limit=3, learning_limit=2))

    assert result.profile == HomeProfile("Putu Alvin")
    assert result.recent_cases[0].case_id == case_id
    assert result.learning_recommendations[0].progress_percent == 50.0
    assert connection.executed[1][1] == (user_id, 3)
    assert connection.executed[2][1] == (user_id, 2)
