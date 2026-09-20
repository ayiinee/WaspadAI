from __future__ import annotations

import asyncio
import hashlib
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import uuid4

import pytest

import app.community_evidence_service as evidence_service
from app.models import TextVerificationRequest


class FakeCursor:
    def __init__(self, rows: list[dict[str, object]]) -> None:
        self.rows = rows

    async def fetchall(self) -> list[dict[str, object]]:
        return self.rows


class FakeConnection:
    def __init__(self, rows: list[dict[str, object]]) -> None:
        self.rows = rows
        self.parameters: tuple[object, ...] | None = None

    async def execute(self, _query: str, parameters: tuple[object, ...]) -> FakeCursor:
        self.parameters = parameters
        return FakeCursor(self.rows)


def test_build_text_community_evidence_returns_sanitized_payload(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_id = uuid4()
    redacted_text = "Pesan menawarkan bantuan tunai melalui tautan tidak resmi."
    content_hash = hashlib.sha256(redacted_text.encode("utf-8")).hexdigest()
    community_post_id = uuid4()
    case_id = uuid4()
    source_id = uuid4()
    connection = FakeConnection(
        [
            {
                "community_post_id": community_post_id,
                "case_id": case_id,
                "revision": 3,
                "content_hash": content_hash,
                "status": "VERIFIED_EVIDENCE",
                "title": "Klaim bantuan tunai melalui tautan tidak resmi",
                "redacted_text": redacted_text,
                "published_at": datetime(2026, 9, 15, 10, tzinfo=UTC),
                "verified_at": datetime(2026, 9, 15, 12, 30, tzinfo=UTC),
                "contribution_title": "Contribution title",
                "contribution_summary": "Contribution summary",
                "contribution_reasoning": "Contribution reasoning",
                "contribution_sanitized_content": redacted_text,
                "sanitized_snapshot": {
                    "verified_claim": "Tautan tersebut bukan kanal resmi.",
                    "stance": "REFUTES",
                    "evidence_summary": "Moderator memverifikasi sumber resmi.",
                },
                "evidence_ids": [str(source_id)],
                "source_id": source_id,
                "source_url": "https://example.go.id/klarifikasi-bantuan",
                "source_title": "Klarifikasi program bantuan",
                "source_publisher": "Instansi resmi",
                "source_created_at": datetime(2026, 9, 15, 9, tzinfo=UTC),
            }
        ]
    )

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(evidence_service, "user_transaction", transaction)
    request = TextVerificationRequest.model_validate(
        {"text": "Apakah pesan bantuan tunai dari tautan tidak resmi ini benar?"}
    )

    result = asyncio.run(
        evidence_service.build_text_community_evidence(
            object(),
            SimpleNamespace(db_statement_timeout_seconds=15),
            user_id,
            request,
        )
    )

    assert connection.parameters == (evidence_service.MAX_CANDIDATE_ROWS,)
    assert len(result) == 1
    record = result[0]
    assert record["record_type"] == "COMMUNITY_VERIFIED_EVIDENCE"
    assert record["community_post_id"] == str(community_post_id)
    assert record["case_id"] == str(case_id)
    assert record["stance"] == "REFUTES"
    assert record["redacted_text"] == redacted_text
    assert record["sources"] == [
        {
            "source_url": "https://example.go.id/klarifikasi-bantuan",
            "title": "Klarifikasi program bantuan",
            "publisher": "Instansi resmi",
            "published_at": None,
        }
    ]
    assert "owner_id" not in record
    assert "user_id" not in record
    assert "rag_consent_id" not in record
    assert "votes" not in record


def test_builder_excludes_records_without_required_moderation_projection() -> None:
    redacted_text = "Pesan sanitized."
    content_hash = hashlib.sha256(redacted_text.encode("utf-8")).hexdigest()
    row = {
        "community_post_id": uuid4(),
        "case_id": uuid4(),
        "revision": 1,
        "content_hash": content_hash,
        "status": "VERIFIED_EVIDENCE",
        "title": "Judul",
        "redacted_text": redacted_text,
        "published_at": datetime(2026, 9, 15, 10, tzinfo=UTC),
        "verified_at": datetime(2026, 9, 15, 12, tzinfo=UTC),
        "sanitized_snapshot": {
            "verified_claim": "Klaim",
            "evidence_summary": "Ringkasan",
        },
        "source_id": uuid4(),
        "source_url": "https://example.com/source",
        "source_title": "Source",
        "source_publisher": "Publisher",
    }

    assert evidence_service._records_from_rows([row], "pesan") == []


def test_builder_can_use_explicit_development_fixture_when_database_has_no_rows(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    connection = FakeConnection([])

    @asynccontextmanager
    async def transaction(*_args: object, **_kwargs: object) -> AsyncIterator[FakeConnection]:
        yield connection

    monkeypatch.setattr(evidence_service, "user_transaction", transaction)
    request = TextVerificationRequest.model_validate(
        {"text": "Apakah tautan bantuan tunai ini benar dan aman ditindaklanjuti?"}
    )

    result = asyncio.run(
        evidence_service.build_text_community_evidence(
            object(),
            SimpleNamespace(
                db_statement_timeout_seconds=15,
                community_evidence_fixture_enabled=True,
            ),
            uuid4(),
            request,
        )
    )

    assert len(result) == 1
    assert result[0]["record_type"] == "COMMUNITY_VERIFIED_EVIDENCE"
    assert result[0]["status"] == "VERIFIED_EVIDENCE"
    assert "owner_id" not in result[0]
    assert "moderator_id" not in result[0]
    assert "rag_consent_id" not in result[0]
