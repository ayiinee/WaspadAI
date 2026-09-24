from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from uuid import uuid4

import pytest

from app import community_service
from app.config import Settings
from app.errors import ProductAPIError


class _Cursor:
    def __init__(self, row: dict[str, object] | None = None) -> None:
        self.row = row

    async def fetchone(self) -> dict[str, object] | None:
        return self.row


class _LikeConnection:
    def __init__(self, community_id: object, case_id: object) -> None:
        self.community_id = community_id
        self.case_id = case_id
        self.inserted = False

    async def execute(self, query: str, _: object = None) -> _Cursor:
        if "select p.id as post_id" in query:
            return _Cursor({"post_id": self.community_id, "case_id": self.case_id})
        if "insert into public.community_likes" in query:
            self.inserted = True
            return _Cursor()
        if "select p.id as community_id" in query:
            return _Cursor(
                {
                    "community_id": self.community_id,
                    "case_id": self.case_id,
                    "liked": True,
                    "like_count": 1,
                    "view_count": 0,
                    "comment_count": 0,
                    "share_count": 0,
                }
            )
        raise AssertionError(query)


def test_owner_response_is_forbidden_with_stable_error() -> None:
    owner_id = uuid4()
    with pytest.raises(ProductAPIError) as raised:
        community_service._require_response_target(
            {"post_id": uuid4(), "case_id": uuid4(), "owner_id": owner_id},
            owner_id,
        )
    assert raised.value.status_code == 403
    assert raised.value.code == "CANNOT_RESPOND_OWN_POST"
    assert raised.value.message == "User cannot respond to their own community case"


@pytest.mark.parametrize(
    "guard",
    [
        community_service._require_vote_target,
        community_service._require_response_target,
    ],
)
def test_verified_case_rejects_new_assessments(guard: object) -> None:
    with pytest.raises(ProductAPIError) as raised:
        guard(  # type: ignore[operator]
            {
                "post_id": uuid4(),
                "case_id": uuid4(),
                "owner_id": uuid4(),
                "status": "VERIFIED_EVIDENCE",
            },
            uuid4(),
        )
    assert raised.value.status_code == 409
    assert raised.value.code == "COMMUNITY_ASSESSMENT_CLOSED"
    assert raised.value.message == "Kasus sudah terverifikasi dan tidak menerima penilaian baru."


def test_owner_like_is_allowed(monkeypatch: pytest.MonkeyPatch) -> None:
    async def check() -> None:
        user_id = uuid4()
        community_id = uuid4()
        case_id = uuid4()
        connection = _LikeConnection(community_id, case_id)

        @asynccontextmanager
        async def transaction(*_: object, **__: object):
            yield connection

        monkeypatch.setattr(community_service, "user_transaction", transaction)
        result = await community_service.like_community(
            object(),
            Settings(),
            user_id,
            community_id,
        )

        assert connection.inserted is True
        assert result.community_id == community_id
        assert result.liked is True

    asyncio.run(check())
