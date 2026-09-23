from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from types import SimpleNamespace
from uuid import uuid4

import pytest

import app.community_service as community_service


class FakeCursor:
    def __init__(self, row: dict[str, object]) -> None:
        self.row = row

    async def fetchone(self) -> dict[str, object]:
        return self.row


class FakeConnection:
    def __init__(self, row: dict[str, object]) -> None:
        self.row = row
        self.parameters: tuple[object, ...] | None = None

    async def execute(self, _query: str, parameters: tuple[object, ...]) -> FakeCursor:
        self.parameters = parameters
        return FakeCursor(self.row)


def test_community_user_summary_counts_user_activity(monkeypatch: pytest.MonkeyPatch) -> None:
    user_id = uuid4()
    connection = FakeConnection(
        {
            "assessments_count": 12,
            "evidence_added_count": 5,
            "resolved_cases_count": 2,
        }
    )

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)

    result = asyncio.run(
        community_service.get_community_user_summary(
            object(), SimpleNamespace(db_statement_timeout_seconds=15), user_id
        )
    )

    assert result.assessments_count == 12
    assert result.evidence_added_count == 5
    assert result.resolved_cases_count == 2
    assert connection.parameters == (user_id, user_id, user_id)


def test_successful_community_refresh_is_written_to_audit_log(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_id = uuid4()
    request_id = uuid4()
    connection = FakeConnection({})

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)

    asyncio.run(
        community_service.record_community_refresh(
            object(),
            SimpleNamespace(db_statement_timeout_seconds=15),
            user_id,
            request_id,
            7,
        )
    )

    assert connection.parameters is not None
    assert connection.parameters[:2] == (user_id, request_id)
    assert connection.parameters[2].obj == {"source": "pull_to_refresh", "item_count": 7}
