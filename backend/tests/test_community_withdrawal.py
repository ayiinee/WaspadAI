from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from types import SimpleNamespace
from uuid import UUID, uuid4

import pytest

import app.community_service as community_service
from app.errors import ProductAPIError


class FakeCursor:
    def __init__(self, row: dict[str, object] | None = None) -> None:
        self.row = row

    async def fetchone(self) -> dict[str, object] | None:
        return self.row


class FakeConnection:
    def __init__(self, post: dict[str, object] | None) -> None:
        self.post = post
        self.executed: list[tuple[str, tuple[object, ...]]] = []

    async def execute(self, query: str, parameters: tuple[object, ...]) -> FakeCursor:
        self.executed.append((query, parameters))
        if "select p.id as post_id" in query:
            return FakeCursor(self.post)
        if "returning revision" in query:
            return FakeCursor({"revision": 8})
        return FakeCursor()


def install_fake_transaction(monkeypatch: pytest.MonkeyPatch, connection: FakeConnection) -> None:
    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)


def withdraw(
    monkeypatch: pytest.MonkeyPatch, post: dict[str, object] | None
) -> tuple[UUID, FakeConnection]:
    case_id = uuid4()
    connection = FakeConnection(post)
    install_fake_transaction(monkeypatch, connection)
    result = asyncio.run(
        community_service.withdraw_community_case(
            object(), SimpleNamespace(db_statement_timeout_seconds=15), uuid4(), case_id
        )
    )
    assert result.case_id == case_id
    return case_id, connection


def test_withdrawal_marks_case_and_post_and_revokes_consents(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_id = uuid4()
    post = {
        "post_id": uuid4(),
        "post_status": "PUBLISHED_UNVERIFIED",
        "publication_consent_id": uuid4(),
        "rag_consent_id": uuid4(),
        "community_state": "PUBLISHED_UNVERIFIED",
        "revision": 7,
    }
    connection = FakeConnection(post)
    install_fake_transaction(monkeypatch, connection)
    case_id = uuid4()

    result = asyncio.run(
        community_service.withdraw_community_case(
            object(), SimpleNamespace(db_statement_timeout_seconds=15), user_id, case_id
        )
    )

    assert result.community_state == "WITHDRAWN"
    assert result.revision == 8
    queries = "\n".join(query for query, _ in connection.executed)
    assert "set status = 'WITHDRAWN'" in queries
    assert "set community_state = 'WITHDRAWN'" in queries
    assert "set revoked_at = now()" in queries
    assert connection.executed[-1][1] == (
        user_id,
        post["publication_consent_id"],
        post["rag_consent_id"],
    )


def test_withdrawal_is_idempotent_for_already_withdrawn_post(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case_id, connection = withdraw(
        monkeypatch,
        {
            "post_id": uuid4(),
            "post_status": "WITHDRAWN",
            "publication_consent_id": uuid4(),
            "rag_consent_id": uuid4(),
            "community_state": "WITHDRAWN",
            "revision": 7,
        },
    )

    assert len(connection.executed) == 1
    assert connection.executed[0][1][0] == case_id


def test_verified_post_can_be_withdrawn(monkeypatch: pytest.MonkeyPatch) -> None:
    _, connection = withdraw(
        monkeypatch,
        {
            "post_id": uuid4(),
            "post_status": "VERIFIED_EVIDENCE",
            "publication_consent_id": uuid4(),
            "rag_consent_id": None,
            "community_state": "VERIFIED_EVIDENCE",
            "revision": 7,
        },
    )

    queries = "\n".join(query for query, _ in connection.executed)
    assert "set status = 'WITHDRAWN'" in queries


@pytest.mark.parametrize(
    ("post", "status_code", "code"),
    [
        (None, 404, "COMMUNITY_NOT_FOUND"),
    ],
)
def test_withdrawal_rejects_missing_post(
    monkeypatch: pytest.MonkeyPatch,
    post: dict[str, object] | None,
    status_code: int,
    code: str,
) -> None:
    connection = FakeConnection(post)
    install_fake_transaction(monkeypatch, connection)

    with pytest.raises(ProductAPIError) as raised:
        asyncio.run(
            community_service.withdraw_community_case(
                object(), SimpleNamespace(db_statement_timeout_seconds=15), uuid4(), uuid4()
            )
        )

    assert raised.value.status_code == status_code
    assert raised.value.code == code
