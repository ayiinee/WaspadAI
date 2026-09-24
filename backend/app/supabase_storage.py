from __future__ import annotations

from datetime import UTC, datetime
from uuid import UUID

import httpx

from app.config import Settings
from app.errors import ProductAPIError

VERIFICATION_INPUTS_BUCKET = "verification-inputs"


def verification_input_path(
    *, user_id: UUID, idempotency_key: UUID, digest: str, content_type: str
) -> str:
    extension = _extension_for_content_type(content_type)
    date_path = datetime.now(UTC).strftime("%Y/%m/%d")
    return f"{user_id}/{date_path}/{idempotency_key}-{digest[:16]}.{extension}"


async def upload_verification_input(
    http_client: httpx.AsyncClient,
    settings: Settings,
    *,
    user_id: UUID,
    idempotency_key: UUID,
    digest: str,
    image_bytes: bytes,
    content_type: str,
) -> str | None:
    if not settings.store_screenshots_enabled:
        return None
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Storage Supabase belum dikonfigurasi untuk menyimpan gambar verifikasi.",
            retryable=True,
        )

    object_path = verification_input_path(
        user_id=user_id,
        idempotency_key=idempotency_key,
        digest=digest,
        content_type=content_type,
    )
    url = (
        f"{settings.supabase_url.rstrip('/')}/storage/v1/object/"
        f"{VERIFICATION_INPUTS_BUCKET}/{object_path}"
    )
    service_role_key = settings.supabase_service_role_key.get_secret_value()
    try:
        response = await http_client.put(
            url,
            content=image_bytes,
            headers={
                "Authorization": f"Bearer {service_role_key}",
                "apikey": service_role_key,
                "Content-Type": content_type,
                "Cache-Control": "private, max-age=0, no-store",
                "x-upsert": "true",
            },
        )
    except httpx.RequestError as error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Gambar verifikasi belum dapat disimpan ke Supabase Storage.",
            retryable=True,
        ) from error

    if response.is_error:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Supabase Storage menolak penyimpanan gambar verifikasi.",
            retryable=True,
        )
    return object_path


async def delete_verification_input(
    http_client: httpx.AsyncClient,
    settings: Settings,
    *,
    bucket: str,
    object_path: str,
) -> None:
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        return
    key = settings.supabase_service_role_key.get_secret_value()
    response = await http_client.delete(
        f"{settings.supabase_url.rstrip('/')}/storage/v1/object/{bucket}/{object_path}",
        headers={"Authorization": f"Bearer {key}", "apikey": key},
    )
    if response.is_error and response.status_code != 404:
        raise ProductAPIError(
            503,
            "STORAGE_UNAVAILABLE",
            "Lampiran percakapan belum dapat dibersihkan.",
            retryable=True,
        )


def _extension_for_content_type(content_type: str) -> str:
    return {
        "image/png": "png",
        "image/webp": "webp",
        "image/jpeg": "jpg",
    }.get(content_type, "bin")
