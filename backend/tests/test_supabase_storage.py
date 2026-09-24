from __future__ import annotations

from uuid import uuid4

import anyio
import httpx

from app.config import Settings
from app.supabase_storage import (
    delete_verification_input,
    upload_verification_input,
    verification_input_path,
)


def test_verification_input_path_uses_private_user_prefix() -> None:
    user_id = uuid4()
    idempotency_key = uuid4()
    path = verification_input_path(
        user_id=user_id,
        idempotency_key=idempotency_key,
        digest="a" * 64,
        content_type="image/png",
    )

    assert path.startswith(f"{user_id}/")
    assert path.endswith(f"{idempotency_key}-{'a' * 16}.png")


def test_upload_verification_input_targets_verification_bucket() -> None:
    async def check() -> None:
        user_id = uuid4()
        idempotency_key = uuid4()

        def handler(request: httpx.Request) -> httpx.Response:
            assert str(request.url).startswith(
                "https://example.supabase.co/storage/v1/object/verification-inputs/"
            )
            assert f"/{user_id}/" in str(request.url)
            assert request.headers["Authorization"] == "Bearer service-role"
            assert request.headers["apikey"] == "service-role"
            assert request.headers["Content-Type"] == "image/png"
            assert request.headers["x-upsert"] == "true"
            assert request.read() == b"image-bytes"
            return httpx.Response(200, json={"Key": "ok"})

        settings = Settings(
            _env_file=None,
            store_screenshots_enabled=True,
            supabase_url="https://example.supabase.co",
            supabase_service_role_key="service-role",
        )
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            object_path = await upload_verification_input(
                client,
                settings,
                user_id=user_id,
                idempotency_key=idempotency_key,
                digest="b" * 64,
                image_bytes=b"image-bytes",
                content_type="image/png",
            )

        assert object_path is not None
        assert object_path.startswith(f"{user_id}/")

    anyio.run(check)


def test_delete_verification_input_uses_private_object_endpoint() -> None:
    async def check() -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            assert request.method == "DELETE"
            assert str(request.url) == (
                "https://example.supabase.co/storage/v1/object/verification-inputs/user/case.png"
            )
            assert request.headers["Authorization"] == "Bearer service-role"
            assert request.headers["apikey"] == "service-role"
            return httpx.Response(200)

        settings = Settings(
            _env_file=None,
            supabase_url="https://example.supabase.co",
            supabase_service_role_key="service-role",
        )
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            await delete_verification_input(
                client,
                settings,
                bucket="verification-inputs",
                object_path="user/case.png",
            )

    anyio.run(check)
