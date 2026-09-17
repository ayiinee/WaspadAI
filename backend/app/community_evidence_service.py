from __future__ import annotations

import json
import re
from collections import defaultdict
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

from psycopg.rows import DictRow
from psycopg_pool import AsyncConnectionPool
from pydantic import ValidationError

from app.config import Settings
from app.database import user_transaction
from app.models import CommunityEvidenceRecord, CommunityEvidenceSource, TextVerificationRequest

MAX_COMMUNITY_EVIDENCE_RECORDS = 5
MAX_COMMUNITY_EVIDENCE_SOURCES = 3
MAX_COMMUNITY_EVIDENCE_BYTES = 30_000
MAX_CANDIDATE_ROWS = 50


async def build_text_community_evidence(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    request: TextVerificationRequest,
) -> list[dict[str, Any]]:
    return await _build_community_evidence(pool, settings, user_id, request.text)


async def build_image_community_evidence(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    question: str | None,
) -> list[dict[str, Any]]:
    if not question or not question.strip():
        return []
    return await _build_community_evidence(pool, settings, user_id, question)


async def _build_community_evidence(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    relevance_text: str,
) -> list[dict[str, Any]]:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select p.id as community_post_id,
                   p.case_id,
                   p.revision,
                   p.content_hash,
                   p.status,
                   p.title,
                   p.redacted_text,
                   p.published_at,
                   p.verified_at,
                   c.title as contribution_title,
                   c.summary as contribution_summary,
                   c.reasoning as contribution_reasoning,
                   c.sanitized_content as contribution_sanitized_content,
                   md.sanitized_snapshot,
                   md.evidence_ids,
                   s.id as source_id,
                   s.source_url,
                   s.title as source_title,
                   s.publisher as source_publisher,
                   s.created_at as source_created_at
              from public.community_posts p
              join public.verification_cases vc on vc.id = p.case_id
              join public.contributions c on c.id = p.verification_contribution_id
              join lateral (
                  select m.sanitized_snapshot, m.evidence_ids, m.expected_revision
                    from public.moderation_decisions m
                   where m.contribution_id = c.id
                     and m.action = 'VERIFY'
                     and m.new_status = 'VERIFIED'
                     and m.allow_rag = true
                     and m.expected_revision = c.revision
                   order by m.created_at desc, m.id desc
                   limit 1
              ) md on true
              join private.consent_records publication_consent
                on publication_consent.id = p.publication_consent_id
               and publication_consent.scope = 'COMMUNITY_PUBLICATION'
               and publication_consent.user_id = p.owner_id
               and publication_consent.case_id = p.case_id
               and publication_consent.preview_id = p.preview_id
               and publication_consent.content_hash = p.content_hash
               and publication_consent.revoked_at is null
               and (publication_consent.expires_at is null or publication_consent.expires_at > now())
              join private.consent_records rag_consent
                on rag_consent.id = p.rag_consent_id
               and rag_consent.scope = 'RAG_REUSE'
               and rag_consent.user_id = p.owner_id
               and rag_consent.case_id = p.case_id
               and rag_consent.preview_id = p.preview_id
               and rag_consent.content_hash = p.content_hash
               and rag_consent.revoked_at is null
               and (rag_consent.expires_at is null or rag_consent.expires_at > now())
              join public.contribution_sources s on s.contribution_id = c.id
             where p.status = 'VERIFIED_EVIDENCE'
               and p.withdrawn_at is null
               and p.verified_at is not null
               and p.verification_contribution_id is not null
               and vc.community_state = 'VERIFIED_EVIDENCE'
               and vc.deleted_at is null
               and vc.retention_expires_at > now()
               and c.status = 'VERIFIED'
               and c.verified_at is not null
               and c.retracted_at is null
               and c.content_hash = p.content_hash
               and c.revision = p.revision
               and md.evidence_ids ? s.id::text
               and char_length(btrim(coalesce(s.title, ''))) between 1 and 300
               and char_length(btrim(coalesce(s.publisher, ''))) between 1 and 200
             order by p.verified_at desc, p.id, s.created_at desc
             limit %s
            """,
            (MAX_CANDIDATE_ROWS,),
        )
        rows = await query.fetchall()

    records = _records_from_rows(rows, relevance_text)
    return _trim_to_payload_budget(records)


def _records_from_rows(rows: list[DictRow], input_text: str) -> list[dict[str, Any]]:
    grouped: dict[UUID, list[DictRow]] = defaultdict(list)
    for row in rows:
        grouped[row["community_post_id"]].append(row)

    records: list[CommunityEvidenceRecord] = []
    for group in grouped.values():
        record = _record_from_group(group)
        if record is not None:
            records.append(record)

    records.sort(
        key=lambda record: (
            _relevance_score(input_text, record),
            _parse_datetime(record.verified_at),
        ),
        reverse=True,
    )
    return [
        record.model_dump(mode="json")
        for record in records[:MAX_COMMUNITY_EVIDENCE_RECORDS]
    ]


def _record_from_group(rows: list[DictRow]) -> CommunityEvidenceRecord | None:
    row = rows[0]
    snapshot = _as_dict(row["sanitized_snapshot"])
    verified_claim = _first_text(snapshot, "verified_claim", "claim", "factual_claim")
    stance = _first_text(snapshot, "stance", "moderation_stance")
    evidence_summary = _first_text(
        snapshot,
        "evidence_summary",
        "summary",
        "moderation_summary",
    )
    if stance not in {"SUPPORTS", "REFUTES", "CONTEXT"}:
        return None

    sources = _sources_from_rows(rows)
    if not sources:
        return None

    redacted_text = str(row["redacted_text"]).strip()
    if not redacted_text or len(redacted_text) > 4_000:
        return None
    content_hash = _sha256(redacted_text)
    if content_hash != row["content_hash"]:
        return None

    try:
        return CommunityEvidenceRecord(
            community_post_id=row["community_post_id"],
            case_id=row["case_id"],
            revision=int(row["revision"]),
            content_hash=row["content_hash"],
            title=str(row["title"]).strip(),
            verified_claim=verified_claim,
            stance=stance,
            evidence_summary=evidence_summary,
            redacted_text=redacted_text,
            published_at=_iso8601(row["published_at"]),
            verified_at=_iso8601(row["verified_at"]),
            sources=sources,
        )
    except (TypeError, ValueError, ValidationError):
        return None


def _sources_from_rows(rows: list[DictRow]) -> list[CommunityEvidenceSource]:
    sources: list[CommunityEvidenceSource] = []
    seen: set[str] = set()
    for row in rows:
        source_url = str(row["source_url"]).strip()
        if source_url in seen:
            continue
        try:
            sources.append(
                CommunityEvidenceSource(
                    source_url=source_url,
                    title=str(row["source_title"]).strip(),
                    publisher=str(row["source_publisher"]).strip(),
                    published_at=None,
                )
            )
            seen.add(source_url)
        except (TypeError, ValueError, ValidationError):
            continue
        if len(sources) >= MAX_COMMUNITY_EVIDENCE_SOURCES:
            break
    return sources


def _trim_to_payload_budget(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    trimmed: list[dict[str, Any]] = []
    for record in records:
        candidate = [*trimmed, record]
        size = len(json.dumps(candidate, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        if size <= MAX_COMMUNITY_EVIDENCE_BYTES:
            trimmed.append(record)
    return trimmed


def _relevance_score(input_text: str, record: CommunityEvidenceRecord) -> int:
    query_terms = _terms(input_text)
    if not query_terms:
        return 0
    haystack = " ".join(
        [
            record.title,
            record.verified_claim,
            record.evidence_summary,
            record.redacted_text,
        ]
    )
    record_terms = _terms(haystack)
    return len(query_terms & record_terms)


def _terms(value: str) -> set[str]:
    return {term for term in re.findall(r"[0-9A-Za-zÀ-ÿ_]{3,}", value.lower())}


def _first_text(payload: dict[str, Any], *keys: str) -> str:
    for key in keys:
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def _as_dict(value: object) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _sha256(value: str) -> str:
    import hashlib

    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _parse_datetime(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _iso8601(value: datetime) -> str:
    if value.tzinfo is None:
        value = value.replace(tzinfo=UTC)
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")
