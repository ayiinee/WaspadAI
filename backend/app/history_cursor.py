from __future__ import annotations

import base64
import hashlib
import hmac
import json
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from app.errors import ProductAPIError


@dataclass(frozen=True)
class HistoryCursor:
    user_id: UUID
    created_at: datetime
    case_id: UUID


def encode_cursor(cursor: HistoryCursor, secret: str) -> str:
    payload = json.dumps(
        {"u": str(cursor.user_id), "t": cursor.created_at.isoformat(), "i": str(cursor.case_id)},
        sort_keys=True,
        separators=(",", ":"),
    ).encode()
    signature = hmac.new(secret.encode(), payload, hashlib.sha256).digest()
    return f"{_encode(payload)}.{_encode(signature)}"


def decode_cursor(value: str, secret: str, expected_user_id: UUID) -> HistoryCursor:
    try:
        payload_part, signature_part = value.split(".", 1)
        payload = _decode(payload_part)
        supplied_signature = _decode(signature_part)
        expected_signature = hmac.new(secret.encode(), payload, hashlib.sha256).digest()
        if not hmac.compare_digest(supplied_signature, expected_signature):
            raise ValueError("signature mismatch")
        decoded = json.loads(payload)
        cursor = HistoryCursor(
            user_id=UUID(decoded["u"]),
            created_at=datetime.fromisoformat(decoded["t"]),
            case_id=UUID(decoded["i"]),
        )
        if cursor.created_at.tzinfo is None or cursor.user_id != expected_user_id:
            raise ValueError("cursor scope mismatch")
        return cursor
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise ProductAPIError(400, "INVALID_CURSOR", "Cursor history tidak valid.") from error


def _encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode().rstrip("=")


def _decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
