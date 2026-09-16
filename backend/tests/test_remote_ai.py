from __future__ import annotations

import anyio
import httpx
import pytest

from app.config import Settings
from app.errors import ProductAPIError
from app.models import ImageVerificationRequest, TextVerificationRequest
from app.verification_service import verify_remote_image, verify_remote_text


def _request() -> TextVerificationRequest:
    return TextVerificationRequest.model_validate(
        {"text": "Pesan uji remote dengan panjang cukup untuk validasi."}
    )


def _result() -> dict[str, object]:
    return {
        "request_id": "req-test",
        "trace_id": "trace-test",
        "status": "COMPLETED",
        "mode": "LIVE",
        "mode_notice": "Pemeriksaan live.",
        "input_summary": {},
        "verdict": "UNVERIFIED",
        "risk_level": "UNKNOWN",
        "dimensions": {
            "factual_status": "UNVERIFIED",
            "source_authenticity": "UNVERIFIED",
            "sender_identity": "UNVERIFIED",
            "channel_status": "UNVERIFIED",
            "scam_risk": "UNKNOWN",
            "content_authenticity": "NOT_APPLICABLE",
        },
        "headline": "Bukti belum cukup.",
        "evidence_sufficiency": 0,
        "evidence_sufficiency_label": "Belum cukup",
        "what_checked": [],
        "why": [],
        "evidence": [],
        "recommended_actions": [],
        "sources": [],
        "uncertainty": "Masih perlu bukti.",
        "requires_human_review": True,
        "community_status": "ELIGIBLE_WITH_CONSENT",
        "privacy_notice": "Data aman.",
        "rulebook": {},
        "pipeline": [],
        "presentation": {"narrative": {"text": "Belum cukup."}},
        "disclaimer": "Dukungan keputusan.",
    }


def test_remote_text_sends_server_auth_and_forces_both() -> None:
    async def check() -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            assert str(request.url) == "https://ai.example/api/internal/v1/verify/text"
            assert request.headers["X-Waspadai-API-Key"] == "server-key"
            body = request.read().decode()
            assert '"output_mode":"BOTH"' in body
            return httpx.Response(200, json=_result())

        settings = Settings(
            ai_service_mode="remote",
            ai_service_base_url="https://ai.example",
            ai_service_api_key="server-key",
            _env_file=None,
        )
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await verify_remote_text(client, settings, _request())
        assert result.request_id == "req-test"

    anyio.run(check)


def test_remote_image_sends_multipart_and_forces_both() -> None:
    async def check() -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            body = request.read()
            assert str(request.url).endswith("/api/internal/v1/verify/image")
            assert request.headers["X-Waspadai-API-Key"] == "server-key"
            assert b"name=\"image\"" in body
            assert b"output_mode" in body
            return httpx.Response(200, json=_result())

        settings = Settings(
            ai_service_mode="remote",
            ai_service_base_url="https://ai.example",
            ai_service_api_key="server-key",
            _env_file=None,
        )
        request = ImageVerificationRequest(question="Apakah ini benar?")
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result = await verify_remote_image(
                client, settings, b"\x89PNG\r\n\x1a\nfixture", "image/png", request
            )
        assert result.request_id == "req-test"

    anyio.run(check)


@pytest.mark.parametrize("status_code", [401, 403, 500])
def test_remote_text_maps_upstream_errors(status_code: int) -> None:
    async def check() -> None:
        settings = Settings(
            ai_service_mode="remote",
            ai_service_base_url="https://ai.example",
            ai_service_api_key="server-key",
            _env_file=None,
        )
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(lambda _: httpx.Response(status_code))
        ) as client:
            with pytest.raises(ProductAPIError) as raised:
                await verify_remote_text(client, settings, _request())
        assert raised.value.status_code == (502 if status_code != 429 else 429)

    anyio.run(check)
