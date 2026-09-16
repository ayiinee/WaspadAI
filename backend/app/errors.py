from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class ProductAPIError(Exception):
    status_code: int
    code: str
    message: str
    retryable: bool = False
    retry_after_seconds: int | None = None


def error_body(error: ProductAPIError, request_id: str) -> dict[str, object]:
    return {
        "error": {
            "code": error.code,
            "message": error.message,
            "request_id": request_id,
            "retryable": error.retryable,
            "retry_after_seconds": error.retry_after_seconds,
        }
    }
