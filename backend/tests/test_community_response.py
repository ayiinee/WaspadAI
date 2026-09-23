from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from uuid import UUID, uuid4

import pytest

from app import community_service
from app.config import Settings
from app.repositories.postgres import community_repository as community_sql


class _Cursor:
    def __init__(self, row: dict[str, object] | None = None) -> None:
        self._row = row

    async def fetchone(self) -> dict[str, object] | None:
        return self._row


class _ResponseConnection:
    def __init__(self, post_id: UUID, verification_case_id: UUID, user_id: UUID) -> None:
        self.post_id = post_id
        self.verification_case_id = verification_case_id
        self.user_id = user_id
        self.asset_parameters: tuple[object, ...] | None = None

    async def execute(
        self,
        query: str,
        parameters: tuple[object, ...] = (),
    ) -> _Cursor:
        if query == community_sql.SELECT_VOTE_TARGET:
            return _Cursor(
                {
                    "post_id": self.post_id,
                    "case_id": self.verification_case_id,
                    "owner_id": uuid4(),
                }
            )
        if query == community_sql.SELECT_RESPONSE_EVIDENCE_ASSET:
            return _Cursor()
        if query == community_sql.INSERT_RESPONSE_ASSET:
            self.asset_parameters = parameters
            return _Cursor({"id": uuid4()})
        if query == community_sql.UPSERT_COMMUNITY_RESPONSE:
            return _Cursor()
        if query == community_sql.SELECT_VOTE_RESULT:
            return _Cursor(
                {
                    "community_id": self.post_id,
                    "case_id": self.verification_case_id,
                    "user_vote": "WASPADA",
                    "hoaks": 0,
                    "waspada": 1,
                    "valid": 0,
                }
            )
        if query == community_sql.SELECT_COMMUNITY_RESPONSE:
            return _Cursor(
                {
                    "response_id": self.user_id,
                    "author": "Pengguna WaspadAI",
                    "created_at": datetime.now(UTC),
                    "vote": "WASPADA",
                    "reasoning": "Alasan pengujian yang cukup panjang.",
                    "has_image": True,
                }
            )
        raise AssertionError(query)


def test_response_asset_uses_verification_case_id(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def check() -> None:
        user_id = uuid4()
        post_id = uuid4()
        verification_case_id = uuid4()
        connection = _ResponseConnection(post_id, verification_case_id, user_id)

        @asynccontextmanager
        async def transaction(*_: object, **__: object) -> AsyncIterator[_ResponseConnection]:
            yield connection

        async def upload(*_: object, **__: object) -> str:
            return f"{user_id}/evidence.png"

        monkeypatch.setattr(community_service, "user_transaction", transaction)
        monkeypatch.setattr(community_service, "upload_verification_input", upload)

        result = await community_service.submit_community_response(
            object(),
            Settings(),
            user_id,
            post_id,
            "WASPADA",
            "Alasan pengujian yang cukup panjang.",
            b"image",
            "image/png",
            object(),
        )

        assert result.community_id == post_id
        assert connection.asset_parameters is not None
        assert connection.asset_parameters[1] == verification_case_id
        assert connection.asset_parameters[1] != post_id

    asyncio.run(check())
