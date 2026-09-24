from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from hashlib import sha256
from types import SimpleNamespace
from uuid import uuid4

import pytest

from app import community_service
from app.errors import ProductAPIError
from app.schemas.community import CommunityUpdateRequest


class Cursor:
    def __init__(self, row=None):
        self.row = row

    async def fetchone(self):
        return self.row


class Connection:
    def __init__(self, post):
        self.post = post
        self.executed = []

    async def execute(self, query, parameters=()):
        self.executed.append((query, parameters))
        if "select p.id, p.status" in query:
            return Cursor(self.post)
        return Cursor()


def install_transaction(monkeypatch, connection):
    @asynccontextmanager
    async def transaction(*_args, **_kwargs):
        yield connection

    monkeypatch.setattr(community_service, "user_transaction", transaction)


@pytest.mark.parametrize("post_status", ["PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE"])
def test_owner_can_edit_caption_and_consent_hash(
    monkeypatch: pytest.MonkeyPatch, post_status: str
) -> None:
    user_id = uuid4()
    community_id = uuid4()
    publication_id = uuid4()
    rag_id = uuid4()
    connection = Connection(
        {
            "id": community_id,
            "status": post_status,
            "publication_consent_id": publication_id,
            "rag_consent_id": rag_id,
        }
    )
    install_transaction(monkeypatch, connection)

    async def detail(*_args):
        return {
            "community_id": community_id,
            "case_id": uuid4(),
            "creator_display_name": "Olivia",
            "is_owner": True,
            "title": "Kasus",
            "redacted_text": "Caption baru",
            "status": post_status,
            "published_at": datetime(2026, 9, 23, tzinfo=UTC),
            "has_image": False,
            "media": [],
            "hoaks": 0,
            "waspada": 0,
            "valid": 0,
            "user_vote": None,
            "like_count": 0,
            "view_count": 0,
            "comment_count": 0,
            "share_count": 0,
            "user_liked": False,
        }

    monkeypatch.setattr(community_service, "_fetch_detail_row", detail)
    result = asyncio.run(
        community_service.update_community_case(
            object(),
            SimpleNamespace(db_statement_timeout_seconds=15),
            user_id,
            community_id,
            CommunityUpdateRequest(caption="  Caption baru  "),
        )
    )

    expected_hash = sha256(b"Caption baru").hexdigest()
    assert result.redacted_text == "Caption baru"
    assert result.creator.display_name == "Olivia"
    assert any(
        parameters == ("Caption baru", expected_hash, community_id)
        for _, parameters in connection.executed
    )
    assert any(
        parameters == (expected_hash, user_id, publication_id, rag_id)
        for _, parameters in connection.executed
    )


def test_missing_post_cannot_be_edited(monkeypatch: pytest.MonkeyPatch) -> None:
    connection = Connection(None)
    install_transaction(monkeypatch, connection)

    with pytest.raises(ProductAPIError) as raised:
        asyncio.run(
            community_service.update_community_case(
                object(),
                SimpleNamespace(db_statement_timeout_seconds=15),
                uuid4(),
                uuid4(),
                CommunityUpdateRequest(caption="Caption baru"),
            )
        )

    assert raised.value.status_code == 404
    assert raised.value.code == "COMMUNITY_NOT_FOUND"
