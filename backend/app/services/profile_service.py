from __future__ import annotations

import hashlib
from datetime import UTC, datetime
from uuid import UUID, uuid4

import httpx

from app.config import Settings
from app.errors import ProductAPIError
from app.privacy.image_validation import matches_image_signature, validate_image_dimensions
from app.repositories.interfaces.profile_repository import ProfileRepository
from app.schemas.profile import (
    CommunityActivityOverview,
    LearningOverview,
    ProfileCommunityActivityItem,
    ProfileCommunityActivityPage,
    ProfileLearningItem,
    ProfileLearningResponse,
    ProfileOverviewResponse,
    ProfilePublicationItem,
    ProfilePublicationPage,
    ProfileResponse,
    ProfileUpdateRequest,
    ProfileVerificationItem,
    ProfileVerificationPage,
    PublicationOverview,
    VerificationOverview,
)

ALLOWED_AVATAR_TYPES = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}
MAX_AVATAR_BYTES = 8 * 1024 * 1024


def _profile_response(row: dict, email: str | None, avatar_url: str | None) -> ProfileResponse:
    return ProfileResponse(
        user_id=row["id"],
        email=email,
        display_name=row["display_name"],
        bio=row["bio"],
        avatar_url=avatar_url if row.get("avatar_asset_id") else None,
        created_at=row["created_at"],
    )


async def get_profile(
    repository: ProfileRepository, user_id: UUID, email: str | None, avatar_url: str
) -> ProfileResponse:
    row = await repository.get_profile(user_id)
    if row is None:
        raise ProductAPIError(404, "PROFILE_NOT_FOUND", "Profil pengguna tidak ditemukan.")
    return _profile_response(row, email, avatar_url)


async def update_profile(
    repository: ProfileRepository,
    user_id: UUID,
    email: str | None,
    payload: ProfileUpdateRequest,
    avatar_url: str,
) -> ProfileResponse:
    row = await repository.update_profile(
        user_id, payload.display_name, payload.bio, "bio" in payload.model_fields_set
    )
    if row is None:
        raise ProductAPIError(404, "PROFILE_NOT_FOUND", "Profil pengguna tidak ditemukan.")
    return _profile_response(row, email, avatar_url)


async def get_overview(
    repository: ProfileRepository, user_id: UUID
) -> ProfileOverviewResponse:
    row = await repository.overview(user_id)
    return ProfileOverviewResponse(
        verification=VerificationOverview(
            total=row["verification_total"],
            private=row["verification_private"],
            published_unverified=row["verification_published"],
            verified_evidence=row["verification_verified"],
            withdrawn=row["verification_withdrawn"],
        ),
        publications=PublicationOverview(
            total=row["publication_total"],
            published_unverified=row["publication_published"],
            verified_evidence=row["publication_verified"],
            withdrawn=row["publication_withdrawn"],
        ),
        community_activity=CommunityActivityOverview(
            assessments=row["assessments"],
            evidence_added=row["evidence_added"],
            resolved_cases=row["resolved_cases"],
        ),
        learning=LearningOverview(
            total_modules=row["total_modules"],
            completed_modules=row["completed_modules"],
            progress_percent=round(row["progress_percent"] or 0, 2),
            latest_score=row["latest_score"],
            best_score=row["best_score"],
            last_activity_at=row["last_activity_at"],
        ),
    )


async def list_verifications(
    repository: ProfileRepository,
    user_id: UUID,
    limit: int,
    offset: int,
    status: str | None,
) -> ProfileVerificationPage:
    rows = await repository.list_verifications(user_id, limit, offset, status)
    return ProfileVerificationPage(
        items=[ProfileVerificationItem(**row) for row in rows[:limit]], has_more=len(rows) > limit
    )


async def list_publications(
    repository: ProfileRepository,
    user_id: UUID,
    limit: int,
    offset: int,
    status: str | None,
) -> ProfilePublicationPage:
    rows = await repository.list_publications(user_id, limit, offset, status)
    return ProfilePublicationPage(
        items=[ProfilePublicationItem(**row) for row in rows[:limit]], has_more=len(rows) > limit
    )


async def list_community_activity(
    repository: ProfileRepository, user_id: UUID, limit: int, offset: int
) -> ProfileCommunityActivityPage:
    rows = await repository.list_community_activity(user_id, limit, offset)
    return ProfileCommunityActivityPage(
        items=[ProfileCommunityActivityItem(**row) for row in rows[:limit]],
        has_more=len(rows) > limit,
    )


async def list_learning(
    repository: ProfileRepository, user_id: UUID
) -> ProfileLearningResponse:
    return ProfileLearningResponse(
        items=[ProfileLearningItem(**row) for row in await repository.list_learning(user_id)]
    )


def _storage_headers(settings: Settings, content_type: str | None = None) -> dict[str, str]:
    if settings.supabase_service_role_key is None:
        raise ProductAPIError(
            503, "STORAGE_UNAVAILABLE", "Storage profil belum dikonfigurasi.", True
        )
    key = settings.supabase_service_role_key.get_secret_value()
    headers = {"Authorization": f"Bearer {key}", "apikey": key}
    if content_type:
        headers["Content-Type"] = content_type
    return headers


async def upload_avatar(
    repository: ProfileRepository,
    client: httpx.AsyncClient,
    settings: Settings,
    user_id: UUID,
    data: bytes,
    content_type: str,
) -> None:
    if content_type not in ALLOWED_AVATAR_TYPES:
        raise ProductAPIError(
            415, "INVALID_AVATAR_TYPE", "Avatar harus berupa JPG, PNG, atau WEBP."
        )
    if not data or len(data) > MAX_AVATAR_BYTES:
        raise ProductAPIError(413, "AVATAR_TOO_LARGE", "Ukuran avatar maksimal 8 MB.")
    if not matches_image_signature(data, content_type):
        raise ProductAPIError(
            415,
            "INVALID_AVATAR_TYPE",
            "Isi file avatar tidak cocok dengan format gambar.",
        )
    validate_image_dimensions(data, content_type)
    if settings.supabase_url is None:
        raise ProductAPIError(
            503, "STORAGE_UNAVAILABLE", "Storage profil belum dikonfigurasi.", True
        )
    digest = hashlib.sha256(data).hexdigest()
    extension = ALLOWED_AVATAR_TYPES[content_type]
    path = f"{user_id}/{datetime.now(UTC):%Y/%m}/{uuid4()}-{digest[:12]}.{extension}"
    url = f"{settings.supabase_url.rstrip('/')}/storage/v1/object/profile-assets/{path}"
    try:
        response = await client.put(
            url,
            content=data,
            headers={**_storage_headers(settings, content_type), "x-upsert": "false"},
        )
    except httpx.RequestError as error:
        raise ProductAPIError(
            503, "STORAGE_UNAVAILABLE", "Avatar belum dapat diunggah.", True
        ) from error
    if response.is_error:
        raise ProductAPIError(503, "STORAGE_UNAVAILABLE", "Penyimpanan avatar ditolak.", True)
    asset_id = await repository.insert_avatar_asset(user_id, path, content_type, len(data), digest)
    old = await repository.replace_avatar(user_id, asset_id)
    if old and old.get("object_path"):
        await _delete_storage_object(client, settings, old["bucket"], old["object_path"])


async def delete_avatar(
    repository: ProfileRepository,
    client: httpx.AsyncClient,
    settings: Settings,
    user_id: UUID,
) -> None:
    old = await repository.clear_avatar(user_id)
    if old:
        await _delete_storage_object(client, settings, old["bucket"], old["object_path"])


async def fetch_avatar(
    repository: ProfileRepository,
    client: httpx.AsyncClient,
    settings: Settings,
    user_id: UUID,
) -> tuple[bytes, str]:
    row = await repository.get_profile(user_id)
    if not row or not row.get("avatar_object_path") or settings.supabase_url is None:
        raise ProductAPIError(404, "AVATAR_NOT_FOUND", "Avatar belum tersedia.")
    storage_root = f"{settings.supabase_url.rstrip('/')}/storage/v1/object"
    url = f"{storage_root}/{row['avatar_bucket']}/{row['avatar_object_path']}"
    try:
        response = await client.get(url, headers=_storage_headers(settings))
    except httpx.RequestError as error:
        raise ProductAPIError(
            503, "STORAGE_UNAVAILABLE", "Avatar belum dapat dimuat.", True
        ) from error
    if response.is_error:
        raise ProductAPIError(404, "AVATAR_NOT_FOUND", "Avatar belum tersedia.")
    return response.content, row["avatar_mime_type"]


async def _delete_storage_object(
    client: httpx.AsyncClient, settings: Settings, bucket: str, path: str
) -> None:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        return
    url = f"{settings.supabase_url.rstrip('/')}/storage/v1/object/{bucket}/{path}"
    try:
        await client.delete(url, headers=_storage_headers(settings))
    except httpx.RequestError:
        return
