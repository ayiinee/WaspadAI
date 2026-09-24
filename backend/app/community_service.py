from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from hashlib import sha256
from uuid import UUID, uuid4

import httpx
from psycopg.errors import UniqueViolation
from psycopg.rows import DictRow
from psycopg.types.json import Jsonb
from psycopg_pool import AsyncConnectionPool

from app.config import Settings
from app.errors import ProductAPIError
from app.history_cursor import HistoryCursor, decode_cursor, encode_cursor
from app.repositories.postgres import community_repository as community_sql
from app.repositories.postgres.community_repository import (
    community_transaction as user_transaction,
)
from app.schemas.community import (
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
from app.schemas.verification import AIResult
from app.supabase_storage import upload_verification_input

COMMUNITY_PUBLIC_STATUSES = ("PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE")


async def record_community_refresh(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    request_id: UUID,
    item_count: int,
) -> None:
    """Persist a successful, user-triggered connection-feed refresh."""
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        await connection.execute(
            community_sql.INSERT_REFRESH_AUDIT,
            (
                user_id,
                request_id,
                Jsonb({"source": "pull_to_refresh", "item_count": max(0, item_count)}),
            ),
        )


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
            community_sql.SELECT_COMMUNITY_FEED,
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
            community_sql.SELECT_USER_SUMMARY,
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
            community_sql.SELECT_RESPONSE_EVIDENCE_ASSET,
            (post["post_id"], user_id),
        )
        old_row = await old.fetchone()
        evidence_asset_id = None
        if object_path is not None and digest is not None:
            asset = await connection.execute(
                community_sql.INSERT_RESPONSE_ASSET,
                (
                    user_id,
                    post["case_id"],
                    object_path,
                    evidence_content_type,
                    len(evidence_bytes or b""),
                    digest,
                    settings.screenshot_retention_hours,
                ),
            )
            evidence_asset_id = (await asset.fetchone())["id"]
        await connection.execute(
            community_sql.UPSERT_COMMUNITY_RESPONSE,
            (post["post_id"], user_id, request.vote, normalized_reasoning, evidence_asset_id),
        )
        if old_row and evidence_asset_id is not None and old_row["evidence_asset_id"]:
            await connection.execute(
                community_sql.SOFT_DELETE_ASSET,
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
            community_sql.SELECT_LEGACY_COMMUNITY_IMAGE,
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
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar preview belum dapat dimuat.",
            True,
        )
    async with user_transaction(
        pool,
        user_id,
        settings.db_statement_timeout_seconds,
    ) as connection:
        query = await connection.execute(
            community_sql.SELECT_PREVIEW_MEDIA_ASSET,
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
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar komunitas belum dapat dimuat.",
            True,
        )
    async with user_transaction(
        pool,
        user_id,
        settings.db_statement_timeout_seconds,
    ) as connection:
        query = await connection.execute(
            community_sql.SELECT_COMMUNITY_MEDIA_ASSET,
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
    storage_url = (
        f"{settings.supabase_url.rstrip('/')}/storage/v1/object/"  # type: ignore[union-attr]
        f"{asset['bucket']}/{asset['object_path']}"
    )
    try:
        response = await http_client.get(
            storage_url,
            headers={"Authorization": f"Bearer {service_role_key}", "apikey": service_role_key},
        )
    except httpx.RequestError as error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            f"{label.capitalize()} belum dapat dimuat.",
            True,
        ) from error
    if response.is_error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            f"Storage menolak pembacaan {label}.",
            True,
        )
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
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar tanggapan belum dapat dimuat.",
            True,
        )
    async with user_transaction(
        pool,
        viewer_id,
        settings.db_statement_timeout_seconds,
    ) as connection:
        query = await connection.execute(
            community_sql.SELECT_RESPONSE_IMAGE_ASSET,
            (case_id, case_id, response_user_id),
        )
        asset = await query.fetchone()
    if asset is None:
        raise ProductAPIError(
            404,
            "COMMUNITY_RESPONSE_IMAGE_NOT_FOUND",
            "Gambar tanggapan tidak ditemukan.",
        )
    service_role_key = settings.supabase_service_role_key.get_secret_value()
    storage_url = (
        f"{settings.supabase_url.rstrip('/')}/storage/v1/object/"
        f"{asset['bucket']}/{asset['object_path']}"
    )
    try:
        response = await http_client.get(
            storage_url,
            headers={"Authorization": f"Bearer {service_role_key}", "apikey": service_role_key},
        )
    except httpx.RequestError as error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar tanggapan belum dapat dimuat.",
            True,
        ) from error
    if response.is_error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Storage menolak pembacaan gambar tanggapan.",
        )
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
            community_sql.UPSERT_COMMUNITY_VOTE,
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
            community_sql.DELETE_COMMUNITY_VOTE,
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
            community_sql.SELECT_CASE_FOR_PREVIEW,
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
            community_sql.SELECT_CASE_IMAGE_ASSET,
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
            community_sql.INSERT_COMMUNITY_PREVIEW,
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
                community_sql.INSERT_COMMUNITY_PREVIEW_MEDIA,
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
                community_sql.SELECT_PREVIEW_FOR_PUBLICATION,
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
                community_sql.INSERT_PUBLICATION_CONSENT,
                (user_id, case_id, request.preview_id, content_hash),
            )
            publication_row = await publication.fetchone()
            rag_consent_id = None
            if request.rag_reuse_consent:
                rag = await connection.execute(
                    community_sql.INSERT_RAG_CONSENT,
                    (user_id, case_id, request.preview_id, content_hash),
                )
                rag_row = await rag.fetchone()
                rag_consent_id = rag_row["id"]

            inserted_post = await connection.execute(
                community_sql.INSERT_COMMUNITY_POST,
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
                community_sql.INSERT_COMMUNITY_MEDIA_FROM_PREVIEW,
                (post_row["id"], request.preview_id),
            )
            if preview["redacted_asset_id"] is not None:
                await connection.execute(
                    community_sql.INSERT_LEGACY_COMMUNITY_MEDIA,
                    (post_row["id"], preview["redacted_asset_id"]),
                )
            updated = await connection.execute(
                community_sql.UPDATE_CASE_PUBLISHED,
                (case_id,),
            )
            await updated.fetchone()
            await connection.execute(
                community_sql.UPDATE_PREVIEW_PUBLISHED,
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
            community_sql.SELECT_POST_FOR_UPDATE,
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
            community_sql.UPDATE_COMMUNITY_POST,
            (caption, content_hash, community_id),
        )
        await connection.execute(
            community_sql.UPDATE_CONSENT_HASH,
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
            community_sql.SELECT_POST_FOR_WITHDRAWAL,
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
            community_sql.WITHDRAW_COMMUNITY_POST,
            (post["post_id"],),
        )
        updated = await connection.execute(
            community_sql.UPDATE_CASE_WITHDRAWN,
            (case_id,),
        )
        updated_case = await updated.fetchone()
        await connection.execute(
            community_sql.REVOKE_COMMUNITY_CONSENTS,
            (user_id, post["publication_consent_id"], post["rag_consent_id"]),
        )

    return CommunityStateResponse(
        case_id=case_id,
        community_state="WITHDRAWN",
        revision=updated_case["revision"],
    )


async def _fetch_detail_row(
    connection: object,
    user_id: UUID,
    community_id: UUID,
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_COMMUNITY_DETAIL,
        (user_id, user_id, user_id, community_id, community_id),
    )
    return await query.fetchone()


async def _fetch_response_rows(connection: object, community_id: UUID) -> list[DictRow]:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_COMMUNITY_RESPONSES,
        (community_id, community_id),
    )
    return await query.fetchall()


async def _fetch_response_row(connection: object, post_id: UUID, user_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_COMMUNITY_RESPONSE,
        (post_id, user_id),
    )
    return await query.fetchone()


async def _fetch_vote_target(connection: object, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_VOTE_TARGET,
        (case_id, case_id),
    )
    return await query.fetchone()


async def _fetch_vote_result_row(
    connection: object, case_id: UUID, user_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_VOTE_RESULT,
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
            community_sql.INSERT_COMMUNITY_LIKE,
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
            community_sql.DELETE_COMMUNITY_LIKE,
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
            community_sql.INSERT_COMMUNITY_VIEW,
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
            community_sql.INSERT_COMMUNITY_SHARE,
            (post["post_id"], user_id),
        )
        row = await _fetch_social_row(connection, user_id, case_id)
    share_url = f"{settings.public_base_url.rstrip('/')}/community/{row['community_id']}"
    return _social_result(row, case_id, share_url=share_url)


async def _fetch_social_target(connection: object, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_SOCIAL_TARGET,
        (case_id, case_id),
    )
    return await query.fetchone()


async def _fetch_social_row(connection: object, user_id: UUID, case_id: UUID) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        community_sql.SELECT_SOCIAL_RESULT,
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
