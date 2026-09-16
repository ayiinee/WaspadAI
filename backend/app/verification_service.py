from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from psycopg.types.json import Jsonb
from psycopg_pool import AsyncConnectionPool

from app.config import Settings
from app.database import user_transaction
from app.errors import ProductAPIError
from app.history_cursor import HistoryCursor, decode_cursor, encode_cursor
from app.mock_ai import build_review_required_result
from app.models import (
    AIResult,
    HistoryItem,
    HistoryMeta,
    HistoryPage,
    TextVerificationRequest,
    VerificationEnvelope,
)

VERIFY_TEXT_ROUTE = "POST /api/v1/verifications/text"


def canonical_payload(request: TextVerificationRequest) -> dict[str, object]:
    return {
        "text": request.text,
        "question": request.question,
        "source_url": request.source_url,
        "sender_context": request.sender_context.value,
        "page_context": request.page_context.model_dump(mode="json")
        if request.page_context
        else None,
        "output_mode": "BOTH",
    }


def payload_hash(request: TextVerificationRequest) -> str:
    encoded = json.dumps(
        canonical_payload(request), ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def requires_history(result: AIResult, settings: Settings) -> bool:
    return (
        settings.history_policy == "ALL"
        or result.verdict == "UNVERIFIED"
        or result.requires_human_review
    )


def save_reason(result: AIResult, settings: Settings) -> str:
    if result.verdict == "UNVERIFIED":
        return "UNVERIFIED"
    if result.requires_human_review:
        return "HUMAN_REVIEW"
    if settings.history_policy == "ALL":
        return "ALL_POLICY"
    raise ValueError("not eligible for history")


async def verify_text(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    idempotency_key: UUID,
    request: TextVerificationRequest,
) -> VerificationEnvelope:
    digest = payload_hash(request)
    operation = await _claim_or_replay(pool, settings, user_id, idempotency_key, digest)
    cached_response = operation.get("response_json")
    if operation["state"] == "COMPLETED" and cached_response is not None:
        return VerificationEnvelope.model_validate(cached_response)

    cached_result = operation.get("ai_result_cache")
    result = AIResult.model_validate(cached_result) if cached_result is not None else None
    if result is None:
        # The first vertical slice deliberately has no remote AI call. The fixture is
        # deterministic from the canonical payload and always labelled MOCK.
        result = build_review_required_result(request, digest)

    try:
        return await _persist_terminal_result(
            pool=pool,
            settings=settings,
            user_id=user_id,
            operation_id=operation["id"],
            request=request,
            digest=digest,
            result=result,
        )
    except ProductAPIError:
        raise
    except Exception as error:
        await _record_persistence_failure(pool, settings, user_id, operation["id"], result)
        raise ProductAPIError(
            503,
            "PERSISTENCE_UNAVAILABLE",
            "Hasil pemeriksaan belum dapat disimpan. Coba lagi dengan Idempotency-Key yang sama.",
            retryable=True,
        ) from error


async def list_history(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    limit: int,
    cursor_value: str | None,
) -> HistoryPage:
    secret = _cursor_secret(settings)
    cursor = decode_cursor(cursor_value, secret, user_id) if cursor_value else None
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """select id, input_type, headline, verdict, requires_human_review,
                      community_state, created_at
                 from public.verification_cases
                 where deleted_at is null
                   and (%s::timestamptz is null or (created_at, id) < (%s::timestamptz, %s::uuid))
                 order by created_at desc, id desc
                 limit %s""",
            (
                cursor.created_at if cursor else None,
                cursor.created_at if cursor else None,
                cursor.case_id if cursor else None,
                limit + 1,
            ),
        )
        rows = await query.fetchall()

    has_next = len(rows) > limit
    page_rows = rows[:limit]
    items = [
        HistoryItem(
            case_id=row["id"],
            input_type=row["input_type"],
            headline=row["headline"],
            verdict=row["verdict"],
            requires_human_review=row["requires_human_review"],
            community_state=row["community_state"],
            created_at=_iso8601(row["created_at"]),
        )
        for row in page_rows
    ]
    next_cursor = None
    if has_next and page_rows:
        final_row = page_rows[-1]
        next_cursor = encode_cursor(
            HistoryCursor(
                user_id=user_id, created_at=final_row["created_at"], case_id=final_row["id"]
            ),
            secret,
        )
    return HistoryPage(items=items, next_cursor=next_cursor)


async def get_history_detail(
    pool: AsyncConnectionPool, settings: Settings, user_id: UUID, case_id: UUID
) -> VerificationEnvelope:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """select c.id, c.product_request_id, c.save_reason, c.community_state,
                      r.result_json, r.execution_mode
                 from public.verification_cases c
                 join public.verification_results r on r.case_id = c.id
                 where c.id = %s and c.deleted_at is null""",
            (case_id,),
        )
        row = await query.fetchone()
    if row is None:
        raise ProductAPIError(404, "CASE_NOT_FOUND", "History tidak ditemukan.")
    result = AIResult.model_validate(row["result_json"])
    return _envelope(
        request_id=row["product_request_id"],
        case_id=row["id"],
        reason=row["save_reason"],
        community_state=row["community_state"],
        result=result,
        execution_mode=row["execution_mode"],
    )


async def _claim_or_replay(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    idempotency_key: UUID,
    digest: str,
) -> dict[str, Any]:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        inserted = await connection.execute(
            """insert into private.request_operations
                   (user_id, route_key, idempotency_key, payload_hash, state,
                    lease_until, persistence_state, expires_at)
               values (%s, %s, %s, %s, 'PROCESSING',
                       now() + make_interval(secs => %s), 'NOT_REQUIRED',
                       now() + make_interval(secs => %s))
               on conflict (user_id, route_key, idempotency_key) do nothing
               returning id, payload_hash, state, lease_until, response_json, ai_result_cache""",
            (
                user_id,
                VERIFY_TEXT_ROUTE,
                idempotency_key,
                digest,
                int(settings.product_request_deadline_seconds),
                settings.idempotency_cache_ttl_seconds,
            ),
        )
        row = await inserted.fetchone()
        if row is not None:
            return row

        existing = await connection.execute(
            """select id, payload_hash, state, lease_until, response_json, ai_result_cache
                 from private.request_operations
                 where user_id = %s and route_key = %s and idempotency_key = %s
                 for update""",
            (user_id, VERIFY_TEXT_ROUTE, idempotency_key),
        )
        row = await existing.fetchone()
        if row is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Operasi tidak dapat dibaca.", True
            )
        if row["payload_hash"] != digest:
            raise ProductAPIError(
                409,
                "IDEMPOTENCY_CONFLICT",
                "Idempotency-Key sudah digunakan untuk payload yang berbeda.",
            )
        if row["state"] == "COMPLETED" and row["response_json"] is not None:
            return row
        if row["state"] == "PROCESSING":
            lease_until = row["lease_until"]
            if lease_until is not None and lease_until > datetime.now(UTC):
                raise ProductAPIError(
                    409,
                    "REQUEST_IN_PROGRESS",
                    "Pemeriksaan dengan Idempotency-Key ini masih diproses.",
                    retryable=True,
                    retry_after_seconds=3,
                )
            await connection.execute(
                """update private.request_operations
                   set state = 'UNKNOWN_OUTCOME', error_code = 'LEASE_EXPIRED', lease_until = null
                   where id = %s""",
                (row["id"],),
            )
            raise ProductAPIError(
                409,
                "UNKNOWN_OUTCOME",
                "Status pemeriksaan sebelumnya tidak dapat dipastikan. Jangan mengulang otomatis.",
            )
        if row["state"] == "FAILED" and row["ai_result_cache"] is not None:
            return row
        raise ProductAPIError(
            409,
            "UNKNOWN_OUTCOME",
            "Status pemeriksaan sebelumnya tidak dapat dipastikan. Jangan mengulang otomatis.",
        )


async def _persist_terminal_result(
    *,
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    operation_id: UUID,
    request: TextVerificationRequest,
    digest: str,
    result: AIResult,
) -> VerificationEnvelope:
    eligible = requires_history(result, settings)
    reason = save_reason(result, settings) if eligible else "NOT_REQUIRED"
    case_id = uuid4() if eligible else None
    community_state = "PRIVATE" if eligible else "NOT_AVAILABLE"
    envelope = _envelope(
        request_id=operation_id,
        case_id=case_id,
        reason=reason,
        community_state=community_state,
        result=result,
        execution_mode="MOCK",
    )
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        if eligible:
            await connection.execute(
                """insert into public.verification_cases
                       (id, user_id, operation_id, product_request_id, input_type, input_source,
                        sanitized_text, input_hash, headline, verdict, risk_level,
                        requires_human_review, save_reason, community_state, retention_expires_at)
                   values (%s, %s, %s, %s, 'TEXT', 'MANUAL', null, %s, %s, %s, %s,
                           %s, %s, %s, now() + make_interval(days => %s))""",
                (
                    case_id,
                    user_id,
                    operation_id,
                    operation_id,
                    digest,
                    result.headline,
                    result.verdict,
                    result.risk_level,
                    result.requires_human_review,
                    reason,
                    community_state,
                    settings.history_retention_days,
                ),
            )
            dimensions = result.dimensions
            await connection.execute(
                """insert into public.verification_results
                       (case_id, factual_status, source_authenticity, sender_identity,
                        channel_status, scam_risk, content_authenticity, evidence_sufficiency,
                        uncertainty, result_json, execution_mode)
                   values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 'MOCK')""",
                (
                    case_id,
                    dimensions["factual_status"],
                    dimensions["source_authenticity"],
                    dimensions["sender_identity"],
                    dimensions["channel_status"],
                    dimensions["scam_risk"],
                    dimensions["content_authenticity"],
                    result.evidence_sufficiency,
                    result.uncertainty,
                    Jsonb(result.model_dump(mode="json")),
                ),
            )
        await connection.execute(
            """update private.request_operations
               set state = 'COMPLETED', lease_until = null, upstream_started_at = now(),
                   response_json = %s, ai_result_cache = %s, http_status = 200,
                   persistence_state = %s, error_code = null
               where id = %s""",
            (
                Jsonb(envelope.model_dump(mode="json")),
                Jsonb(result.model_dump(mode="json")),
                "SAVED" if eligible else "NOT_REQUIRED",
                operation_id,
            ),
        )
    return envelope


async def _record_persistence_failure(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    operation_id: UUID,
    result: AIResult,
) -> None:
    try:
        async with user_transaction(
            pool, user_id, settings.db_statement_timeout_seconds
        ) as connection:
            await connection.execute(
                """update private.request_operations
                   set state = 'FAILED', lease_until = null, ai_result_cache = %s,
                       persistence_state = 'FAILED', error_code = 'PERSISTENCE_FAILED'
                   where id = %s""",
                (Jsonb(result.model_dump(mode="json")), operation_id),
            )
    except Exception:
        # The caller receives an honest 503 even if the operation record itself is unavailable.
        return


def _envelope(
    *,
    request_id: UUID,
    case_id: UUID | None,
    reason: str,
    community_state: str,
    result: AIResult,
    execution_mode: str,
) -> VerificationEnvelope:
    return VerificationEnvelope(
        request_id=request_id,
        status="COMPLETED",
        history=HistoryMeta(
            saved=case_id is not None,
            case_id=case_id,
            save_reason=reason,
            community_eligible=case_id is not None
            and result.community_status == "ELIGIBLE_WITH_CONSENT",
            community_state=community_state,
        ),
        result=result,
        execution_mode=execution_mode,
    )


def _cursor_secret(settings: Settings) -> str:
    if settings.history_cursor_signing_key is None:
        raise ProductAPIError(503, "SERVICE_UNAVAILABLE", "History belum dikonfigurasi.")
    return settings.history_cursor_signing_key.get_secret_value()


def _iso8601(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")
