from __future__ import annotations

from datetime import UTC, datetime
from hashlib import sha256
from uuid import UUID, uuid4

from psycopg.errors import UniqueViolation
from psycopg.rows import DictRow
from psycopg_pool import AsyncConnectionPool

from app.config import Settings
from app.database import user_transaction
from app.errors import ProductAPIError
from app.history_cursor import HistoryCursor, decode_cursor, encode_cursor
from app.models import (
    AIResult,
    CommunityDetail,
    CommunityItem,
    CommunityPage,
    CommunityPreviewResponse,
    CommunityPublishRequest,
    CommunityStateResponse,
    CommunityVoteCounts,
    CommunityVoteRequest,
    CommunityVoteResult,
)

COMMUNITY_PUBLIC_STATUSES = ("PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE")


async def list_community(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    limit: int,
    cursor_value: str | None,
) -> CommunityPage:
    secret = _cursor_secret(settings)
    cursor = decode_cursor(cursor_value, secret, user_id) if cursor_value else None
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select p.case_id, p.title, p.redacted_text, p.status, p.published_at,
                   coalesce(counts.hoaks, 0)::int as hoaks,
                   coalesce(counts.waspada, 0)::int as waspada,
                   coalesce(counts.valid, 0)::int as valid
              from public.community_posts p
              left join lateral (
                  select
                      count(*) filter (where v.vote = 'HOAKS') as hoaks,
                      count(*) filter (where v.vote = 'WASPADA') as waspada,
                      count(*) filter (where v.vote = 'VALID') as valid
                    from public.community_votes v
                   where v.post_id = p.id
              ) counts on true
             where p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and p.publication_consent_id is not null
               and exists (
                   select 1
                     from private.consent_records c
                    where c.id = p.publication_consent_id
                      and c.scope = 'COMMUNITY_PUBLICATION'
                      and c.revoked_at is null
                      and (c.expires_at is null or c.expires_at > now())
               )
               and (%s::timestamptz is null or (p.published_at, p.case_id) <
                   (%s::timestamptz, %s::uuid))
             order by p.published_at desc, p.case_id desc
             limit %s
            """,
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
    items = [_community_item(row) for row in page_rows]
    next_cursor = None
    if has_next and page_rows:
        final_row = page_rows[-1]
        next_cursor = encode_cursor(
            HistoryCursor(
                user_id=user_id,
                created_at=_as_datetime(final_row["published_at"]),
                case_id=final_row["case_id"],
            ),
            secret,
        )
    return CommunityPage(items=items, next_cursor=next_cursor)


async def get_community_detail(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityDetail:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        row = await _fetch_detail_row(connection, user_id, case_id)
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return _community_detail(row)


async def cast_community_vote(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    request: CommunityVoteRequest,
) -> CommunityVoteResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_vote_target(connection, case_id)
        _require_vote_target(post, user_id)
        await connection.execute(
            """
            insert into public.community_votes (post_id, user_id, vote)
            values (%s, %s, %s)
            on conflict (post_id, user_id) do update
                set vote = excluded.vote, updated_at = now()
            """,
            (post["post_id"], user_id, request.vote),
        )
        row = await _fetch_vote_result_row(connection, case_id, user_id)
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return _vote_result(row)


async def remove_community_vote(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityVoteResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_vote_target(connection, case_id)
        _require_vote_target(post, user_id)
        await connection.execute(
            "delete from public.community_votes where post_id = %s and user_id = %s",
            (post["post_id"], user_id),
        )
        row = await _fetch_vote_result_row(connection, case_id, user_id)
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return _vote_result(row)


async def create_community_preview(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityPreviewResponse:
    preview_id = uuid4()
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select id, revision, headline, sanitized_text
              from public.verification_cases
             where id = %s and user_id = %s and deleted_at is null
               and community_state = 'PRIVATE'
            """,
            (case_id, user_id),
        )
        case = await query.fetchone()
        if case is None:
            raise ProductAPIError(404, "CASE_NOT_FOUND", "History tidak ditemukan.")

        redacted_text = (case["sanitized_text"] or "").strip()
        if not redacted_text:
            raise ProductAPIError(
                422,
                "COMMUNITY_CONTENT_UNAVAILABLE",
                "Kasus belum memiliki konten aman untuk dipublikasikan.",
            )
        content_hash = sha256(redacted_text.encode("utf-8")).hexdigest()
        expires_at = datetime.now(UTC)
        inserted = await connection.execute(
            """
            insert into public.community_previews
                (id, case_id, user_id, case_revision, redacted_text,
                    content_hash, redaction_version, redactions, state, expires_at)
            values (%s, %s, %s, %s, %s, %s, 'server-v1', '[]'::jsonb, 'READY',
                    %s + make_interval(secs => %s))
            returning expires_at
            """,
            (
                preview_id,
                case_id,
                user_id,
                case["revision"],
                redacted_text,
                content_hash,
                expires_at,
                settings.preview_ttl_seconds,
            ),
        )
        row = await inserted.fetchone()
    return CommunityPreviewResponse(
        preview_id=preview_id,
        expires_at=_iso8601(row["expires_at"]),
        redacted_text=redacted_text,
        redacted_image_url=None,
        redactions=[],
        confirmation_required=True,
    )


async def publish_community_case(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    request: CommunityPublishRequest,
) -> CommunityStateResponse:
    content_hash: str
    try:
        async with user_transaction(
            pool, user_id, settings.db_statement_timeout_seconds
        ) as connection:
            query = await connection.execute(
                """
                select p.id as preview_id, p.redacted_text, p.content_hash,
                       p.case_revision, p.expires_at, p.state,
                       c.headline, c.revision
                  from public.community_previews p
                  join public.verification_cases c on c.id = p.case_id
                 where p.id = %s and p.case_id = %s and p.user_id = %s
                   and c.user_id = %s and c.deleted_at is null
                   and c.community_state = 'PRIVATE'
                for update of p, c
                """,
                (request.preview_id, case_id, user_id, user_id),
            )
            preview = await query.fetchone()
            if preview is None:
                raise ProductAPIError(
                    404, "COMMUNITY_PREVIEW_NOT_FOUND", "Preview komunitas tidak ditemukan."
                )
            if preview["state"] != "READY" or preview["expires_at"] <= datetime.now(UTC):
                raise ProductAPIError(
                    409, "PREVIEW_EXPIRED", "Preview komunitas sudah kedaluwarsa."
                )

            content_hash = preview["content_hash"]
            publication = await connection.execute(
                """
                insert into private.consent_records
                    (user_id, scope, case_id, preview_id, content_hash, policy_version)
                values (%s, 'COMMUNITY_PUBLICATION', %s, %s, %s, 'community-v1')
                returning id
                """,
                (user_id, case_id, request.preview_id, content_hash),
            )
            publication_row = await publication.fetchone()
            rag_consent_id = None
            if request.rag_reuse_consent:
                rag = await connection.execute(
                    """
                    insert into private.consent_records
                        (user_id, scope, case_id, preview_id, content_hash, policy_version)
                    values (%s, 'RAG_REUSE', %s, %s, %s, 'community-v1')
                    returning id
                    """,
                    (user_id, case_id, request.preview_id, content_hash),
                )
                rag_row = await rag.fetchone()
                rag_consent_id = rag_row["id"]

            await connection.execute(
                """
                insert into public.community_posts
                    (case_id, owner_id, preview_id, title, redacted_text, status,
                     publication_consent_id, rag_consent_id, content_hash, revision)
                values (%s, %s, %s, %s, %s, 'PUBLISHED_UNVERIFIED', %s, %s, %s, %s)
                """,
                (
                    case_id,
                    user_id,
                    request.preview_id,
                    preview["headline"],
                    preview["redacted_text"],
                    publication_row["id"],
                    rag_consent_id,
                    content_hash,
                    preview["case_revision"],
                ),
            )
            updated = await connection.execute(
                """
                update public.verification_cases
                   set community_state = 'PUBLISHED_UNVERIFIED', revision = revision + 1
                 where id = %s
                 returning revision
                """,
                (case_id,),
            )
            updated_row = await updated.fetchone()
            await connection.execute(
                """
                update public.community_previews
                   set state = 'CONSUMED', consumed_at = now()
                 where id = %s
                """,
                (request.preview_id,),
            )
    except UniqueViolation as error:
        raise ProductAPIError(
            409, "COMMUNITY_ALREADY_PUBLISHED", "Kasus sudah dipublikasikan ke komunitas."
        ) from error

    return CommunityStateResponse(
        case_id=case_id,
        community_state="PUBLISHED_UNVERIFIED",
        revision=updated_row["revision"],
    )


async def withdraw_community_case(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityStateResponse:
    """Withdraw an unverified community post and revoke its publication consents."""

    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select p.id as post_id, p.status as post_status, p.publication_consent_id,
                   p.rag_consent_id, c.community_state, c.revision
              from public.community_posts p
              join public.verification_cases c on c.id = p.case_id
             where p.case_id = %s and p.owner_id = %s and c.user_id = %s
               and c.deleted_at is null
             for update of p, c
            """,
            (case_id, user_id, user_id),
        )
        post = await query.fetchone()
        if post is None:
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
        if (
            post["post_status"] == "VERIFIED_EVIDENCE"
            or post["community_state"] == "VERIFIED_EVIDENCE"
        ):
            raise ProductAPIError(
                409,
                "COMMUNITY_WITHDRAWAL_FORBIDDEN",
                "Kasus community yang sudah menjadi evidence terverifikasi tidak dapat ditarik.",
            )
        if post["post_status"] == "WITHDRAWN":
            return CommunityStateResponse(
                case_id=case_id,
                community_state="WITHDRAWN",
                revision=post["revision"],
            )
        if post["post_status"] != "PUBLISHED_UNVERIFIED":
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")

        await connection.execute(
            """
            update public.community_posts
               set status = 'WITHDRAWN', withdrawn_at = now(), revision = revision + 1
             where id = %s
            """,
            (post["post_id"],),
        )
        updated = await connection.execute(
            """
            update public.verification_cases
               set community_state = 'WITHDRAWN', revision = revision + 1
             where id = %s
             returning revision
            """,
            (case_id,),
        )
        updated_case = await updated.fetchone()
        await connection.execute(
            """
            update private.consent_records
               set revoked_at = now()
             where user_id = %s
               and id in (%s, %s)
               and scope in ('COMMUNITY_PUBLICATION', 'RAG_REUSE')
               and revoked_at is null
            """,
            (user_id, post["publication_consent_id"], post["rag_consent_id"]),
        )

    return CommunityStateResponse(
        case_id=case_id,
        community_state="WITHDRAWN",
        revision=updated_case["revision"],
    )


async def _fetch_detail_row(connection: object, user_id: UUID, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.case_id, p.title, p.redacted_text, p.status, p.published_at,
               r.result_json, r.execution_mode,
               coalesce(counts.hoaks, 0)::int as hoaks,
               coalesce(counts.waspada, 0)::int as waspada,
               coalesce(counts.valid, 0)::int as valid,
               own.vote as user_vote
          from public.community_posts p
          join public.verification_cases c on c.id = p.case_id and c.deleted_at is null
          join public.verification_results r on r.case_id = p.case_id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
          left join lateral (
              select
                  count(*) filter (where v.vote = 'HOAKS') as hoaks,
                  count(*) filter (where v.vote = 'WASPADA') as waspada,
                  count(*) filter (where v.vote = 'VALID') as valid
                from public.community_votes v
               where v.post_id = p.id
          ) counts on true
         where p.case_id = %s
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
           and exists (
               select 1 from private.consent_records consent
                where consent.id = p.publication_consent_id
                  and consent.scope = 'COMMUNITY_PUBLICATION'
                  and consent.revoked_at is null
                  and (consent.expires_at is null or consent.expires_at > now())
           )
        """,
        (user_id, case_id),
    )
    return await query.fetchone()


async def _fetch_vote_target(connection: object, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.id as post_id, p.owner_id
          from public.community_posts p
         where p.case_id = %s
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
           and exists (
               select 1 from private.consent_records consent
                where consent.id = p.publication_consent_id
                  and consent.scope = 'COMMUNITY_PUBLICATION'
                  and consent.revoked_at is null
                  and (consent.expires_at is null or consent.expires_at > now())
           )
        """,
        (case_id,),
    )
    return await query.fetchone()


async def _fetch_vote_result_row(
    connection: object, case_id: UUID, user_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.case_id, own.vote as user_vote,
               count(*) filter (where v.vote = 'HOAKS')::int as hoaks,
               count(*) filter (where v.vote = 'WASPADA')::int as waspada,
               count(*) filter (where v.vote = 'VALID')::int as valid
          from public.community_posts p
          left join public.community_votes v on v.post_id = p.id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
         where p.case_id = %s
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
         group by p.case_id, own.vote
        """,
        (user_id, case_id),
    )
    return await query.fetchone()


def _require_vote_target(row: DictRow | None, user_id: UUID) -> None:
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    if row["owner_id"] == user_id:
        raise ProductAPIError(
            403,
            "COMMUNITY_OWNER_VOTE_FORBIDDEN",
            "Pemilik kasus tidak dapat memberi vote.",
        )


def _community_item(row: DictRow) -> CommunityItem:
    return CommunityItem(
        case_id=row["case_id"],
        title=row["title"],
        redacted_text=row["redacted_text"],
        status=row["status"],
        published_at=_iso8601(row["published_at"]),
        counts=_counts(row),
    )


def _community_detail(row: DictRow) -> CommunityDetail:
    return CommunityDetail(
        case_id=row["case_id"],
        title=row["title"],
        redacted_text=row["redacted_text"],
        status=row["status"],
        published_at=_iso8601(row["published_at"]),
        counts=_counts(row),
        user_vote=row["user_vote"],
        result=AIResult.model_validate(row["result_json"]),
        execution_mode=row["execution_mode"],
    )


def _vote_result(row: DictRow) -> CommunityVoteResult:
    return CommunityVoteResult(
        case_id=row["case_id"],
        user_vote=row["user_vote"],
        counts=_counts(row),
    )


def _counts(row: DictRow) -> CommunityVoteCounts:
    return CommunityVoteCounts(
        HOAKS=row["hoaks"],
        WASPADA=row["waspada"],
        VALID=row["valid"],
    )


def _cursor_secret(settings: Settings) -> str:
    if settings.history_cursor_signing_key is None:
        raise ProductAPIError(503, "SERVICE_UNAVAILABLE", "Community belum dikonfigurasi.")
    return settings.history_cursor_signing_key.get_secret_value()


def _as_datetime(value: datetime) -> datetime:
    return value if value.tzinfo is not None else value.replace(tzinfo=UTC)


def _iso8601(value: datetime) -> str:
    return _as_datetime(value).astimezone(UTC).isoformat().replace("+00:00", "Z")
