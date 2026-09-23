from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import uuid4

import pytest

import app.community_service as community_service
from app.errors import ProductAPIError


class _Cursor:
    def __init__(
        self,
        row: dict[str, object] | None = None,
        rows: list[dict[str, object]] | None = None,
    ) -> None:
        self.row = row
        self.rows = rows or []

    async def fetchone(self) -> dict[str, object] | None:
        return self.row

    async def fetchall(self) -> list[dict[str, object]]:
        return self.rows


class _PreviewConnection:
    def __init__(self, case_id: object, risk_level: str = "UNKNOWN") -> None:
        self.case_id = case_id
        self.risk_level = risk_level

    async def execute(self, query: str, parameters: tuple[object, ...]) -> _Cursor:
        assert query.count("%s") == len(parameters), (
            f"SQL placeholder mismatch: {query.count('%s')} placeholders, "
            f"{len(parameters)} parameters"
        )
        if "from public.verification_cases" in query:
            return _Cursor(
                row={
                    "id": self.case_id,
                    "revision": 1,
                    "headline": "Judul aman",
                    "sanitized_text": "Konten aman untuk komunitas",
                    "risk_level": self.risk_level,
                }
            )
        if "from private.stored_assets" in query:
            return _Cursor(rows=[])
        if "insert into public.community_previews" in query:
            return _Cursor(row={"expires_at": datetime.now(UTC)})
        raise AssertionError(query)


def test_create_community_preview_binds_one_case_id_for_asset_query(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case_id = uuid4()
    user_id = uuid4()
    connection = _PreviewConnection(case_id)

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[_PreviewConnection]:
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)

    result = asyncio.run(
        community_service.create_community_preview(
            object(),
            SimpleNamespace(db_statement_timeout_seconds=15, preview_ttl_seconds=900),
            user_id,
            case_id,
        )
    )

    assert result.redacted_text == "Konten aman untuk komunitas"
    assert result.media == []


def test_create_community_preview_rejects_known_verification_result(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    case_id = uuid4()
    connection = _PreviewConnection(case_id, risk_level="HIGH")

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[_PreviewConnection]:
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)

    with pytest.raises(ProductAPIError) as raised:
        asyncio.run(
            community_service.create_community_preview(
                object(),
                SimpleNamespace(db_statement_timeout_seconds=15, preview_ttl_seconds=900),
                uuid4(),
                case_id,
            )
        )

    assert raised.value.status_code == 409
    assert raised.value.code == "COMMUNITY_REQUIRES_UNKNOWN_RESULT"
