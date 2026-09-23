from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from hashlib import sha256
from uuid import UUID, uuid4

import httpx
from psycopg.errors import UniqueViolation
from psycopg.rows import DictRow
from psycopg_pool import AsyncConnectionPool

from app.config import Settings
from app.database import user_transaction
from app.errors import ProductAPIError
from app.history_cursor import HistoryCursor, decode_cursor, encode_cursor
from app.models import (
    AIResult,
    CommunityBootstrap,
    CommunityCreator,
    CommunityDetail,
    CommunityItem,
    CommunityMediaItem,
    CommunityPage,
    CommunityPreviewResponse,
    CommunityPublishRequest,
    CommunityResponseItem,
    CommunityResponseResult,
    CommunitySocialResult,
    CommunityStateResponse,
    CommunityUpdateRequest,
    CommunityUserSummary,
    CommunityVoteCounts,
    CommunityVoteRequest,
    CommunityVoteResult,
)
from app.supabase_storage import upload_verification_input

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
            select p.id as community_id, p.case_id, p.title, p.redacted_text, p.status, p.published_at,
                   (p.owner_id = %s) as is_owner,
                   private.community_display_name(p.owner_id) as creator_display_name,
                   (p.redacted_asset_id is not null or coalesce(jsonb_array_length(media.items), 0) > 0) as has_image,
                   coalesce(media.items, '[]'::jsonb) as media,
                   coalesce(counts.hoaks, 0)::int as hoaks,
                   coalesce(counts.waspada, 0)::int as waspada,
                   coalesce(counts.valid, 0)::int as valid,
                   own.vote as user_vote,
                   coalesce(social.like_count, 0)::int as like_count,
                   coalesce(social.view_count, 0)::int as view_count,
                   coalesce(social.comment_count, 0)::int as comment_count,
                   coalesce(social.share_count, 0)::int as share_count,
                   coalesce(social.user_liked, false) as user_liked
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              left join public.community_votes own
                on own.post_id = p.id and own.user_id = %s
              left join lateral (
                  select jsonb_agg(
                      jsonb_build_object(
                          'id', m.id, 'media_type', m.media_type,
                          'position', m.sort_order, 'width', m.width, 'height', m.height
                      ) order by m.sort_order
                  ) as items
                    from public.community_media m
                   where m.community_id = p.id
              ) media on true
              left join lateral (
                  select
                      count(*) filter (where v.vote = 'HOAKS') as hoaks,
                      count(*) filter (where v.vote = 'WASPADA') as waspada,
                      count(*) filter (where v.vote = 'VALID') as valid
                    from public.community_votes v
                   where v.post_id = p.id
             ) counts on true
             left join lateral (
                 select (select count(*) from public.community_likes l
                          where l.post_id = p.id) as like_count,
                        (select count(*) from public.community_views v
                          where v.post_id = p.id) as view_count,
                        (select count(*) from public.community_votes comment
                          where comment.post_id = p.id) as comment_count,
                        (select count(*) from public.community_shares share
                          where share.post_id = p.id) as share_count,
                        exists (select 1 from public.community_likes mine
                                where mine.post_id = p.id and mine.user_id = %s) as user_liked
             ) social on true
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
                user_id,
                user_id,
                user_id,
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


async def get_community_bootstrap(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    limit: int,
    cursor_value: str | None,
) -> CommunityBootstrap:
    summary, feed = await asyncio.gather(
        get_community_user_summary(pool, settings, user_id),
        list_community(pool, settings, user_id, limit, cursor_value),
    )
    return CommunityBootstrap(summary=summary, feed=feed)


async def get_community_user_summary(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
) -> CommunityUserSummary:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select
                coalesce((
                    select count(*)
                      from public.community_votes v
                     where v.user_id = %s
                ), 0)::int as assessments_count,
                coalesce((
                    select count(*)
                      from public.contributions c
                     where c.user_id = %s
                       and c.status <> 'RETRACTED'
                       and c.retracted_at is null
                ), 0)::int as evidence_added_count,
                coalesce((
                    select count(*)
                      from public.contributions c
                     where c.user_id = %s
                       and c.status = 'VERIFIED'
                       and c.retracted_at is null
                ), 0)::int as resolved_cases_count
            """,
            (user_id, user_id, user_id),
        )
        row = await query.fetchone()
    return CommunityUserSummary(
        assessments_count=row["assessments_count"],
        evidence_added_count=row["evidence_added_count"],
        resolved_cases_count=row["resolved_cases_count"],
    )


async def get_community_detail(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityDetail:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        row = await _fetch_detail_row(connection, user_id, case_id)
        response_rows = await _fetch_response_rows(connection, case_id) if row else []
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return _community_detail(row, response_rows)


async def submit_community_response(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    vote: str,
    reasoning: str,
    evidence_bytes: bytes | None,
    evidence_content_type: str | None,
    http_client: httpx.AsyncClient,
) -> CommunityResponseResult:
    normalized_reasoning = reasoning.strip()
    if len(normalized_reasoning) < 10:
        raise ProductAPIError(422, "VALIDATION_ERROR", "Alasan penilaian minimal 10 karakter.")
    if vote not in {"HOAKS", "WASPADA", "VALID"}:
        raise ProductAPIError(422, "VALIDATION_ERROR", "Kategori penilaian tidak valid.")
    request = CommunityVoteRequest(vote=vote)
    # Authorize before uploading optional evidence. A rejected self-response
    # must not leave an orphaned object in Storage.
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        target = await _fetch_vote_target(connection, case_id)
        _require_response_target(target, user_id)
    digest = sha256(evidence_bytes).hexdigest() if evidence_bytes else None
    object_path = None
    if evidence_bytes is not None and evidence_content_type is not None:
        object_path = await upload_verification_input(
            http_client,
            settings,
            user_id=user_id,
            idempotency_key=uuid4(),
            digest=digest or sha256(evidence_bytes).hexdigest(),
            image_bytes=evidence_bytes,
            content_type=evidence_content_type,
        )
        if object_path is None:
            raise ProductAPIError(
                503,
                "STORAGE_UNAVAILABLE",
                "Gambar bukti belum dapat disimpan.",
                retryable=True,
            )

    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_vote_target(connection, case_id)
        _require_response_target(post, user_id)
        old = await connection.execute(
            "select evidence_asset_id from public.community_votes where post_id = %s and user_id = %s",
            (post["post_id"], user_id),
        )
        old_row = await old.fetchone()
        evidence_asset_id = None
        if object_path is not None and digest is not None:
            asset = await connection.execute(
                """
                insert into private.stored_assets
                    (user_id, case_id, bucket, object_path, purpose, mime_type,
                     size_bytes, sha256, expires_at)
                values (%s, %s, 'verification-inputs', %s, 'CONTRIBUTION_EVIDENCE',
                        %s, %s, %s, now() + make_interval(hours => %s))
                returning id
                """,
                (
                    user_id,
                    case_id,
                    object_path,
                    evidence_content_type,
                    len(evidence_bytes or b""),
                    digest,
                    settings.screenshot_retention_hours,
                ),
            )
            evidence_asset_id = (await asset.fetchone())["id"]
        await connection.execute(
            """
            insert into public.community_votes
                (post_id, user_id, vote, reasoning, evidence_asset_id)
            values (%s, %s, %s, %s, %s)
            on conflict (post_id, user_id) do update
                set vote = excluded.vote,
                    reasoning = excluded.reasoning,
                    evidence_asset_id = coalesce(excluded.evidence_asset_id,
                                                 public.community_votes.evidence_asset_id),
                    updated_at = now()
            """,
            (post["post_id"], user_id, request.vote, normalized_reasoning, evidence_asset_id),
        )
        if old_row and evidence_asset_id is not None and old_row["evidence_asset_id"]:
            await connection.execute(
                "update private.stored_assets set deleted_at = now() where id = %s",
                (old_row["evidence_asset_id"],),
            )
        row = await _fetch_vote_result_row(connection, case_id, user_id)
        response_row = await _fetch_response_row(connection, post["post_id"], user_id)
    if row is None or response_row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return CommunityResponseResult(
        community_id=post["post_id"],
        case_id=post["case_id"],
        user_vote=request.vote,
        counts=_counts(row),
        response=_response_item(response_row),
    )


async def get_community_image(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    http_client: httpx.AsyncClient,
) -> tuple[bytes, str]:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar komunitas belum dapat dimuat.",
            retryable=True,
        )
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              join private.stored_assets asset on asset.id = p.redacted_asset_id
             where (p.id = %s or p.case_id = %s)
               and p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and p.publication_consent_id is not null
               and asset.deleted_at is null
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
        asset = await query.fetchone()
    if asset is None:
        raise ProductAPIError(404, "COMMUNITY_IMAGE_NOT_FOUND", "Gambar komunitas tidak ditemukan.")

    service_role_key = settings.supabase_service_role_key.get_secret_value()
    storage_url = (
        f"{settings.supabase_url.rstrip('/')}/storage/v1/object/"
        f"{asset['bucket']}/{asset['object_path']}"
    )
    try:
        response = await http_client.get(
            storage_url,
            headers={
                "Authorization": f"Bearer {service_role_key}",
                "apikey": service_role_key,
            },
        )
    except httpx.RequestError as error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar komunitas belum dapat dimuat.",
            retryable=True,
        ) from error
    if response.is_error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Storage menolak pembacaan gambar komunitas.",
            retryable=True,
        )
    return response.content, asset["mime_type"]


async def get_community_preview_media(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    preview_id: UUID,
    media_id: UUID,
    http_client: httpx.AsyncClient,
) -> tuple[bytes, str]:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Gambar preview belum dapat dimuat.", True)
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_previews preview
              join public.community_preview_media media on media.preview_id = preview.id
              join private.stored_assets asset on asset.id = media.asset_id
             where preview.id = %s and preview.case_id = %s and preview.user_id = %s
               and media.id = %s and preview.state = 'READY'
               and preview.expires_at > now() and asset.deleted_at is null
            """,
            (preview_id, case_id, user_id, media_id),
        )
        asset = await query.fetchone()
    if asset is None:
        raise ProductAPIError(404, "COMMUNITY_IMAGE_NOT_FOUND", "Gambar preview tidak ditemukan.")
    return await _download_asset(settings, asset, http_client, "gambar preview")


async def get_community_media(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    media_id: UUID,
    http_client: httpx.AsyncClient,
) -> tuple[bytes, str]:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Gambar komunitas belum dapat dimuat.", True)
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_posts post
              join public.community_media media on media.community_id = post.id
              join private.stored_assets asset on asset.id = media.asset_id
             where (post.id = %s or post.case_id = %s) and media.id = %s
               and post.withdrawn_at is null
               and post.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and post.publication_consent_id is not null
               and asset.deleted_at is null
            """,
            (case_id, case_id, media_id),
        )
        asset = await query.fetchone()
    if asset is None:
        raise ProductAPIError(404, "COMMUNITY_IMAGE_NOT_FOUND", "Gambar komunitas tidak ditemukan.")

    return await _download_asset(settings, asset, http_client, "gambar komunitas")


async def _download_asset(
    settings: Settings,
    asset: DictRow,
    http_client: httpx.AsyncClient,
    label: str,
) -> tuple[bytes, str]:
    service_role_key = settings.supabase_service_role_key.get_secret_value()  # type: ignore[union-attr]
    storage_url = f"{settings.supabase_url.rstrip('/')}/storage/v1/object/{asset['bucket']}/{asset['object_path']}"  # type: ignore[union-attr]
    try:
        response = await http_client.get(
            storage_url,
            headers={"Authorization": f"Bearer {service_role_key}", "apikey": service_role_key},
        )
    except httpx.RequestError as error:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", f"{label.capitalize()} belum dapat dimuat.", True) from error
    if response.is_error:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", f"Storage menolak pembacaan {label}.", True)
    return response.content, asset["mime_type"]


async def get_community_response_image(
    pool: AsyncConnectionPool,
    settings: Settings,
    viewer_id: UUID,
    case_id: UUID,
    response_user_id: UUID,
    http_client: httpx.AsyncClient,
) -> tuple[bytes, str]:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Gambar tanggapan belum dapat dimuat.", True)
    async with user_transaction(pool, viewer_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_votes v
              join public.community_posts p on p.id = v.post_id
              join private.stored_assets asset on asset.id = v.evidence_asset_id
             where (p.id = %s or p.case_id = %s) and v.user_id = %s
               and p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and asset.deleted_at is null
            """,
            (case_id, case_id, response_user_id),
        )
        asset = await query.fetchone()
    if asset is None:
        raise ProductAPIError(404, "COMMUNITY_RESPONSE_IMAGE_NOT_FOUND", "Gambar tanggapan tidak ditemukan.")
    service_role_key = settings.supabase_service_role_key.get_secret_value()
    storage_url = f"{settings.supabase_url.rstrip('/')}/storage/v1/object/{asset['bucket']}/{asset['object_path']}"
    try:
        response = await http_client.get(
            storage_url,
            headers={"Authorization": f"Bearer {service_role_key}", "apikey": service_role_key},
        )
    except httpx.RequestError as error:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Gambar tanggapan belum dapat dimuat.", True) from error
    if response.is_error:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Storage menolak pembacaan gambar tanggapan.")
    return response.content, asset["mime_type"]


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
            select c.id, c.revision, c.headline, c.sanitized_text, c.risk_level
              from public.verification_cases c
             where c.id = %s and c.user_id = %s and c.deleted_at is null
               and c.community_state = 'PRIVATE'
            """,
            (case_id, user_id),
        )
        case = await query.fetchone()
        if case is None:
            raise ProductAPIError(404, "CASE_NOT_FOUND", "History tidak ditemukan.")
        if str(case["risk_level"]).upper() != "UNKNOWN":
            raise ProductAPIError(
                409,
                "COMMUNITY_REQUIRES_UNKNOWN_RESULT",
                "Hanya hasil verifikasi yang belum diketahui yang dapat dibagikan ke Koneksi.",
            )
        assets_query = await connection.execute(
            """
            select asset.id
              from private.stored_assets asset
             where asset.case_id = %s
               and asset.purpose = 'SCREENSHOT_OPT_IN'
               and asset.deleted_at is null
             order by asset.created_at, asset.id
             limit 4
            """,
            (case_id,),
        )
        assets = await assets_query.fetchall()

        redacted_text = (case["sanitized_text"] or case["headline"] or "").strip()
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
                    redacted_asset_id, content_hash, redaction_version,
                    redactions, state, expires_at)
            values (%s, %s, %s, %s, %s, %s, %s, 'server-v1', '[]'::jsonb, 'READY',
                    %s + make_interval(secs => %s))
            returning expires_at
            """,
            (
                preview_id,
                case_id,
                user_id,
                case["revision"],
                redacted_text,
                assets[0]["id"] if assets else None,
                content_hash,
                expires_at,
                settings.preview_ttl_seconds,
            ),
        )
        row = await inserted.fetchone()
        preview_media: list[CommunityMediaItem] = []
        for position, asset in enumerate(assets):
            media_insert = await connection.execute(
                """
                insert into public.community_preview_media(preview_id, asset_id, position)
                values (%s, %s, %s)
                returning id
                """,
                (preview_id, asset["id"], position),
            )
            media_row = await media_insert.fetchone()
            preview_media.append(
                CommunityMediaItem(
                    id=media_row["id"],
                    url=f"/api/v1/history/{case_id}/community-preview/{preview_id}/media/{media_row['id']}",
                    position=position,
                )
            )
    return CommunityPreviewResponse(
        preview_id=preview_id,
        expires_at=_iso8601(row["expires_at"]),
        redacted_text=redacted_text,
        redacted_image_url=None,
        media=preview_media,
        redactions=[],
        confirmation_required=True,
    )


async def publish_community_case(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
    request: CommunityPublishRequest,
) -> CommunityItem:
    content_hash: str
    try:
        async with user_transaction(
            pool, user_id, settings.db_statement_timeout_seconds
        ) as connection:
            query = await connection.execute(
                """
                select p.id as preview_id, p.redacted_text, p.redacted_asset_id, p.content_hash,
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

            caption = request.caption.strip()
            if not caption:
                raise ProductAPIError(
                    422, "COMMUNITY_CAPTION_REQUIRED", "Caption wajib diisi sebelum publikasi."
                )
            content_hash = sha256(caption.encode("utf-8")).hexdigest()
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

            inserted_post = await connection.execute(
                """
                insert into public.community_posts
                    (case_id, owner_id, preview_id, title, redacted_text, redacted_asset_id, status,
                     publication_consent_id, rag_consent_id, content_hash, revision)
                values (%s, %s, %s, %s, %s, %s, 'PUBLISHED_UNVERIFIED', %s, %s, %s, %s)
                returning id
                """,
                (
                    case_id,
                    user_id,
                    request.preview_id,
                    preview["headline"],
                    caption,
                    preview["redacted_asset_id"],
                    publication_row["id"],
                    rag_consent_id,
                    content_hash,
                    preview["case_revision"],
                ),
            )
            post_row = await inserted_post.fetchone()
            await connection.execute(
                """
                insert into public.community_media
                    (community_id, asset_id, media_type, sort_order, width, height)
                select %s, preview_media.asset_id, preview_media.media_type,
                       preview_media.position, preview_media.width, preview_media.height
                  from public.community_preview_media preview_media
                 where preview_media.preview_id = %s
                 order by preview_media.position
                 limit 4
                on conflict do nothing
                """,
                (post_row["id"], request.preview_id),
            )
            if preview["redacted_asset_id"] is not None:
                await connection.execute(
                    """
                    insert into public.community_media(community_id, asset_id, sort_order)
                    values (%s, %s, 0)
                    on conflict do nothing
                    """,
                    (post_row["id"], preview["redacted_asset_id"]),
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
            await updated.fetchone()
            await connection.execute(
                """
                update public.community_previews
                   set state = 'CONSUMED', consumed_at = now()
                 where id = %s
                """,
                (request.preview_id,),
            )
            created_row = await _fetch_detail_row(connection, user_id, post_row["id"])
            if created_row is None:
                raise ProductAPIError(
                    500,
                    "COMMUNITY_PUBLISH_INCONSISTENT",
                    "Postingan berhasil dibuat tetapi belum dapat dibaca kembali.",
                )
    except UniqueViolation as error:
        raise ProductAPIError(
            409, "COMMUNITY_ALREADY_PUBLISHED", "Kasus sudah dipublikasikan ke komunitas."
        ) from error

    return _community_item(created_row)


async def update_community_case(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    community_id: UUID,
    request: CommunityUpdateRequest,
) -> CommunityItem:
    """Update an owner's editable community caption without changing its media."""

    caption = request.caption.strip()
    if not caption:
        raise ProductAPIError(422, "COMMUNITY_CAPTION_REQUIRED", "Caption wajib diisi.")
    content_hash = sha256(caption.encode("utf-8")).hexdigest()
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select p.id, p.status, p.publication_consent_id, p.rag_consent_id
              from public.community_posts p
             where p.id = %s and p.owner_id = %s and p.withdrawn_at is null
             for update
            """,
            (community_id, user_id),
        )
        post = await query.fetchone()
        if post is None:
            raise ProductAPIError(
                404, "COMMUNITY_NOT_FOUND", "Postingan komunitas tidak ditemukan."
            )
        if post["status"] not in COMMUNITY_PUBLIC_STATUSES:
            raise ProductAPIError(
                404, "COMMUNITY_NOT_FOUND", "Postingan komunitas tidak ditemukan."
            )

        await connection.execute(
            """
            update public.community_posts
               set redacted_text = %s, content_hash = %s, revision = revision + 1
             where id = %s
            """,
            (caption, content_hash, community_id),
        )
        await connection.execute(
            """
            update private.consent_records
               set content_hash = %s
             where user_id = %s and id in (%s, %s) and revoked_at is null
            """,
            (content_hash, user_id, post["publication_consent_id"], post["rag_consent_id"]),
        )
        updated = await _fetch_detail_row(connection, user_id, community_id)
        if updated is None:
            raise ProductAPIError(
                500,
                "COMMUNITY_UPDATE_INCONSISTENT",
                "Postingan belum dapat dibaca kembali.",
            )
    return _community_item(updated)


async def withdraw_community_case(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    case_id: UUID,
) -> CommunityStateResponse:
    """Soft-withdraw an owner's public post and revoke its publication consents."""

    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select p.id as post_id, p.status as post_status, p.publication_consent_id,
                   p.rag_consent_id, c.community_state, c.revision
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
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
        if post["post_status"] == "WITHDRAWN":
            return CommunityStateResponse(
                case_id=case_id,
                community_state="WITHDRAWN",
                revision=post["revision"],
            )
        if post["post_status"] not in COMMUNITY_PUBLIC_STATUSES:
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


async def _fetch_detail_row(connection: object, user_id: UUID, community_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.id as community_id, p.case_id, p.title, p.redacted_text, p.status, p.published_at,
               (p.owner_id = %s) as is_owner,
               private.community_display_name(p.owner_id) as creator_display_name,
               (p.redacted_asset_id is not null or coalesce(jsonb_array_length(media.items), 0) > 0) as has_image,
               coalesce(media.items, '[]'::jsonb) as media,
               r.result_json, r.execution_mode,
               coalesce(counts.hoaks, 0)::int as hoaks,
               coalesce(counts.waspada, 0)::int as waspada,
               coalesce(counts.valid, 0)::int as valid,
               own.vote as user_vote,
               coalesce(social.like_count, 0)::int as like_count,
               coalesce(social.view_count, 0)::int as view_count,
               coalesce(social.comment_count, 0)::int as comment_count,
               coalesce(social.share_count, 0)::int as share_count,
               coalesce(social.user_liked, false) as user_liked
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
          join public.verification_cases c on c.id = p.case_id and c.deleted_at is null
          join public.verification_results r on r.case_id = p.case_id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
          left join lateral (
              select jsonb_agg(
                  jsonb_build_object(
                      'id', m.id, 'media_type', m.media_type,
                      'position', m.sort_order, 'width', m.width, 'height', m.height
                  ) order by m.sort_order
              ) as items
                from public.community_media m
               where m.community_id = p.id
          ) media on true
          left join lateral (
              select
                  count(*) filter (where v.vote = 'HOAKS') as hoaks,
                  count(*) filter (where v.vote = 'WASPADA') as waspada,
                  count(*) filter (where v.vote = 'VALID') as valid
                from public.community_votes v
               where v.post_id = p.id
         ) counts on true
         left join lateral (
             select (select count(*) from public.community_likes l
                      where l.post_id = p.id) as like_count,
                    (select count(*) from public.community_views v
                      where v.post_id = p.id) as view_count,
                    (select count(*) from public.community_votes comment
                      where comment.post_id = p.id) as comment_count,
                    (select count(*) from public.community_shares share
                      where share.post_id = p.id) as share_count,
                    exists (select 1 from public.community_likes mine
                            where mine.post_id = p.id and mine.user_id = %s) as user_liked
         ) social on true
         where (p.id = %s or p.case_id = %s)
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
        (user_id, user_id, user_id, community_id, community_id),
    )
    return await query.fetchone()


async def _fetch_response_rows(connection: object, community_id: UUID) -> list[DictRow]:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select v.user_id as response_id,
               coalesce(nullif(btrim(profile.display_name), ''), 'Pengguna WaspadAI') as author,
               v.created_at, v.vote, v.reasoning,
               (v.evidence_asset_id is not null) as has_image
          from public.community_votes v
          join public.community_posts p on p.id = v.post_id
          left join public.profiles profile on profile.id = v.user_id
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
         order by v.created_at desc
        """,
        (community_id, community_id),
    )
    return await query.fetchall()


async def _fetch_response_row(
    connection: object, post_id: UUID, user_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select v.user_id as response_id,
               coalesce(nullif(btrim(profile.display_name), ''), 'Pengguna WaspadAI') as author,
               v.created_at, v.vote, v.reasoning,
               (v.evidence_asset_id is not null) as has_image
          from public.community_votes v
          left join public.profiles profile on profile.id = v.user_id
         where v.post_id = %s and v.user_id = %s
        """,
        (post_id, user_id),
    )
    return await query.fetchone()


async def _fetch_vote_target(connection: object, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.id as post_id, p.case_id, p.owner_id
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
         where (p.id = %s or p.case_id = %s)
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
        (case_id, case_id),
    )
    return await query.fetchone()


async def _fetch_vote_result_row(
    connection: object, case_id: UUID, user_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select p.id as community_id, p.case_id, own.vote as user_vote,
               count(*) filter (where v.vote = 'HOAKS')::int as hoaks,
               count(*) filter (where v.vote = 'WASPADA')::int as waspada,
               count(*) filter (where v.vote = 'VALID')::int as valid
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
          left join public.community_votes v on v.post_id = p.id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
         group by p.id, p.case_id, own.vote
        """,
        (user_id, case_id, case_id),
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
    media = _community_media(row["community_id"], row)
    return CommunityItem(
        id=row["community_id"],
        case_id=row["case_id"],
        creator=CommunityCreator(
            display_name=row["creator_display_name"],
            is_current_user=bool(row["is_owner"]),
        ),
        title=row["title"],
        redacted_text=row["redacted_text"],
        status=row["status"],
        published_at=_iso8601(row["published_at"]),
        has_image=bool(row["has_image"]),
        image_url=media[0].url if media else None,
        media=media,
        counts=_counts(row),
        user_vote=row["user_vote"],
        like_count=row["like_count"],
        view_count=row["view_count"],
        comment_count=row["comment_count"],
        share_count=row["share_count"],
        user_liked=bool(row["user_liked"]),
    )


def _community_detail(row: DictRow, response_rows: list[DictRow]) -> CommunityDetail:
    media = _community_media(row["community_id"], row)
    return CommunityDetail(
        id=row["community_id"],
        case_id=row["case_id"],
        creator=CommunityCreator(
            display_name=row["creator_display_name"],
            is_current_user=bool(row["is_owner"]),
        ),
        title=row["title"],
        redacted_text=row["redacted_text"],
        status=row["status"],
        published_at=_iso8601(row["published_at"]),
        has_image=bool(row["has_image"]),
        image_url=media[0].url if media else None,
        media=media,
        counts=_counts(row),
        user_vote=row["user_vote"],
        result=AIResult.model_validate(row["result_json"]),
        execution_mode=row["execution_mode"],
        like_count=row["like_count"],
        view_count=row["view_count"],
        comment_count=row["comment_count"],
        share_count=row["share_count"],
        user_liked=bool(row["user_liked"]),
        responses=[_response_item(response) for response in response_rows],
    )


def _response_item(row: DictRow) -> CommunityResponseItem:
    return CommunityResponseItem(
        response_id=row["response_id"],
        author=row["author"],
        created_at=_iso8601(row["created_at"]),
        vote=row["vote"],
        reasoning=row["reasoning"] or "Penilaian komunitas tersimpan.",
        has_image=bool(row["has_image"]),
    )


def _community_media(community_id: UUID, row: DictRow) -> list[CommunityMediaItem]:
    raw_media = row.get("media") or []
    items = [
        CommunityMediaItem(
            id=item["id"],
            media_type=item.get("media_type", "IMAGE"),
            url=f"/api/v1/community/{community_id}/media/{item['id']}",
            thumbnail_url=None,
            width=item.get("width"),
            height=item.get("height"),
            position=item.get("position", index),
        )
        for index, item in enumerate(raw_media[:4])
    ]
    return sorted(items, key=lambda item: item.position)


def _require_response_target(row: DictRow | None, user_id: UUID) -> None:
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    if row["owner_id"] == user_id:
        raise ProductAPIError(
            403,
            "CANNOT_RESPOND_OWN_POST",
            "User cannot respond to their own community case",
        )


async def like_community(
    pool: AsyncConnectionPool, settings: Settings, user_id: UUID, case_id: UUID
) -> CommunitySocialResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_social_target(connection, case_id)
        if post is None:
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
        await connection.execute(
            "insert into public.community_likes(post_id, user_id) values (%s, %s) on conflict do nothing",
            (post["post_id"], user_id),
        )
        row = await _fetch_social_row(connection, user_id, case_id)
    return _social_result(row, case_id, liked=True)


async def unlike_community(
    pool: AsyncConnectionPool, settings: Settings, user_id: UUID, case_id: UUID
) -> CommunitySocialResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_social_target(connection, case_id)
        if post is None:
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
        await connection.execute(
            "delete from public.community_likes where post_id = %s and user_id = %s",
            (post["post_id"], user_id),
        )
        row = await _fetch_social_row(connection, user_id, case_id)
    return _social_result(row, case_id, liked=False)


async def record_community_view(
    pool: AsyncConnectionPool, settings: Settings, user_id: UUID, case_id: UUID
) -> CommunitySocialResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_social_target(connection, case_id)
        if post is None:
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
        await connection.execute(
            """insert into public.community_views(post_id, user_id) values (%s, %s)
               on conflict (post_id, user_id) do update set last_seen_at = now()""",
            (post["post_id"], user_id),
        )
        row = await _fetch_social_row(connection, user_id, case_id)
    return _social_result(row, case_id)


async def record_community_share(
    pool: AsyncConnectionPool, settings: Settings, user_id: UUID, case_id: UUID
) -> CommunitySocialResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        post = await _fetch_social_target(connection, case_id)
        if post is None:
            raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
        await connection.execute(
            "insert into public.community_shares(post_id, user_id) values (%s, %s)",
            (post["post_id"], user_id),
        )
        row = await _fetch_social_row(connection, user_id, case_id)
    share_url = f"{settings.public_base_url.rstrip('/')}/community/{row['community_id']}"
    return _social_result(row, case_id, share_url=share_url)


async def _fetch_social_target(connection: object, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """select p.id as post_id, p.case_id
             from public.community_posts p
             join public.verification_results result on result.case_id = p.case_id
            where (p.id = %s or p.case_id = %s)
              and p.withdrawn_at is null
              and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
              and p.publication_consent_id is not null
              and exists (
                  select 1 from private.consent_records c
                   where c.id = p.publication_consent_id
                     and c.scope = 'COMMUNITY_PUBLICATION'
                     and c.revoked_at is null
                     and (c.expires_at is null or c.expires_at > now())
              )""",
        (case_id, case_id),
    )
    return await query.fetchone()


async def _fetch_social_row(
    connection: object, user_id: UUID, case_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """select p.id as community_id, p.case_id,
                    exists (select 1 from public.community_likes l
                            where l.post_id = p.id and l.user_id = %s) as liked,
                    (select count(*) from public.community_likes l where l.post_id = p.id)::int as like_count,
                    (select count(*) from public.community_views v where v.post_id = p.id)::int as view_count,
                    (select count(*) from public.community_votes v where v.post_id = p.id)::int as comment_count,
                    (select count(*) from public.community_shares s where s.post_id = p.id)::int as share_count
               from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              where (p.id = %s or p.case_id = %s)
                and p.withdrawn_at is null
                and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
                and p.publication_consent_id is not null""",
        (user_id, case_id, case_id),
    )
    return await query.fetchone()


def _social_result(
    row: DictRow | None,
    case_id: UUID,
    liked: bool | None = None,
    share_url: str | None = None,
) -> CommunitySocialResult:
    if row is None:
        raise ProductAPIError(404, "COMMUNITY_NOT_FOUND", "Kasus komunitas tidak ditemukan.")
    return CommunitySocialResult(
        community_id=row["community_id"],
        case_id=row["case_id"],
        liked=bool(row["liked"] if liked is None else liked),
        like_count=row["like_count"],
        view_count=row["view_count"],
        comment_count=row["comment_count"],
        share_count=row["share_count"],
        share_url=share_url,
    )


def _vote_result(row: DictRow) -> CommunityVoteResult:
    return CommunityVoteResult(
        community_id=row["community_id"],
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
