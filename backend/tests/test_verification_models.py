from __future__ import annotations

from uuid import uuid4

import anyio
import pytest
from pydantic import ValidationError

from app.config import Settings
from app.history_cursor import HistoryCursor, decode_cursor, encode_cursor
from app.mock_ai import build_not_required_result, build_review_required_result
from app.models import TextVerificationRequest
from app.verification_service import (
    _persist_terminal_result,
    canonical_payload,
    payload_hash,
    requires_history,
    save_reason,
)


def request_payload(**overrides: object) -> dict[str, object]:
    return {
        "text": "  Pesan mengaku bank dan meminta kode OTP segera.  ",
        "question": "  Apakah aman?  ",
        "source_url": "https://example.com/source",
        "sender_context": "UNKNOWN_NUMBER",
        **overrides,
    }


def test_text_request_canonicalization_forces_server_output_mode() -> None:
    request = TextVerificationRequest.model_validate(request_payload())
    assert request.text == "Pesan mengaku bank dan meminta kode OTP segera."
    assert request.question == "Apakah aman?"
    assert canonical_payload(request)["output_mode"] == "BOTH"
    assert len(payload_hash(request)) == 64


@pytest.mark.parametrize(
    "source_url",
    ["ftp://example.com/file", "http://127.0.0.1", "http://localhost", "http://10.0.0.4"],
)
def test_text_request_rejects_non_public_source_url(source_url: str) -> None:
    with pytest.raises(ValidationError):
        TextVerificationRequest.model_validate(request_payload(source_url=source_url))


def test_text_request_rejects_client_output_mode() -> None:
    with pytest.raises(ValidationError):
        TextVerificationRequest.model_validate(request_payload(output_mode="NARRATIVE"))


def test_payload_hash_is_stable_for_equivalent_trimmed_input() -> None:
    left = TextVerificationRequest.model_validate(request_payload())
    right = TextVerificationRequest.model_validate(
        request_payload(text=left.text, question=left.question)
    )
    assert payload_hash(left) == payload_hash(right)


def test_mock_result_is_explicitly_mock_and_requires_history() -> None:
    request = TextVerificationRequest.model_validate(request_payload())
    digest = payload_hash(request)
    result = build_review_required_result(request, digest)
    settings = Settings(_env_file=None, history_cursor_signing_key="test-secret")

    assert "mock" in result.mode_notice.lower()
    assert result.verdict == "UNVERIFIED"
    assert requires_history(result, settings)
    assert save_reason(result, settings) == "UNVERIFIED"


def test_not_required_fixture_obeys_review_required_policy() -> None:
    request = TextVerificationRequest.model_validate(request_payload())
    result = build_not_required_result(request, payload_hash(request))
    settings = Settings(_env_file=None, history_cursor_signing_key="test-secret")

    assert not requires_history(result, settings)
    with pytest.raises(ValueError):
        save_reason(result, settings)


def test_history_cursor_is_signed_and_user_scoped() -> None:
    from datetime import UTC, datetime

    user_a = uuid4()
    cursor = HistoryCursor(user_id=user_a, created_at=datetime.now(UTC), case_id=uuid4())
    encoded = encode_cursor(cursor, "test-secret")
    assert decode_cursor(encoded, "test-secret", user_a) == cursor

    with pytest.raises(Exception):
        decode_cursor(encoded, "test-secret", uuid4())
    with pytest.raises(Exception):
        decode_cursor(encoded + "x", "test-secret", user_a)


def test_persist_terminal_result_stores_trimmed_text(monkeypatch: pytest.MonkeyPatch) -> None:
    class FakeTransaction:
        def __init__(self, connection: object) -> None:
            self.connection = connection

        async def __aenter__(self) -> object:
            return self.connection

        async def __aexit__(self, *_args: object) -> None:
            return None

    class FakeConnection:
        def __init__(self) -> None:
            self.calls: list[tuple[str, tuple[object, ...]]] = []

        async def execute(self, sql: str, params: tuple[object, ...]) -> None:
            self.calls.append((sql, params))

    async def check() -> None:
        request = TextVerificationRequest.model_validate(request_payload())
        result = build_review_required_result(request, payload_hash(request))
        settings = Settings(_env_file=None, history_cursor_signing_key="test-secret")
        connection = FakeConnection()

        monkeypatch.setattr(
            "app.verification_service.user_transaction",
            lambda *_args: FakeTransaction(connection),
        )

        await _persist_terminal_result(
            pool=object(),
            settings=settings,
            user_id=uuid4(),
            operation_id=uuid4(),
            request=request,
            digest=payload_hash(request),
            result=result,
            execution_mode="MOCK",
            input_type="TEXT",
        )

        case_insert = next(
            params
            for sql, params in connection.calls
            if "insert into public.verification_cases" in sql
        )
        assert case_insert[5] == "Pesan mengaku bank dan meminta kode OTP segera."

    anyio.run(check)
