"""Smoke-test the protected Product Backend preview in staging.

The script intentionally logs only status, request IDs, case IDs, and duration.
Credentials and request text are never printed. Supply only short-lived test-user
tokens through the environment variables documented in backend/README.md.
"""

from __future__ import annotations

import os
import sys
import time
from uuid import uuid4

import httpx


def required_environment(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"missing required environment variable: {name}")
    return value


def require_status(response: httpx.Response, expected: int, step: str) -> dict[str, object]:
    if response.status_code != expected:
        raise RuntimeError(f"{step} returned HTTP {response.status_code}, expected {expected}")
    try:
        payload = response.json()
    except ValueError as error:
        raise RuntimeError(f"{step} returned a non-JSON response") from error
    if not isinstance(payload, dict):
        raise RuntimeError(f"{step} returned an unexpected JSON envelope")
    return payload


def main() -> None:
    base_url = required_environment("STAGING_BASE_URL").rstrip("/")
    owner_token = required_environment("STAGING_BEARER_USER_A")
    other_token = required_environment("STAGING_BEARER_USER_B")
    headers = {
        "Authorization": f"Bearer {owner_token}",
        "Idempotency-Key": str(uuid4()),
    }
    payload = {
        "text": "Pesan sintetis untuk smoke test preview backend staging.",
        "sender_context": "UNKNOWN",
    }
    started = time.monotonic()

    with httpx.Client(base_url=base_url, timeout=30.0) as client:
        ready = require_status(client.get("/api/ready"), 200, "readiness")
        if ready.get("status") != "ready":
            raise RuntimeError("readiness did not report ready")

        first = require_status(
            client.post("/api/v1/verifications/text", headers=headers, json=payload),
            200,
            "first verification",
        )
        second = require_status(
            client.post("/api/v1/verifications/text", headers=headers, json=payload),
            200,
            "idempotency replay",
        )

        history = first.get("history")
        if (
            first.get("status") != "COMPLETED"
            or first.get("execution_mode") != "MOCK"
            or not isinstance(history, dict)
            or history.get("saved") is not True
            or not isinstance(history.get("case_id"), str)
        ):
            raise RuntimeError("first verification did not produce a saved MOCK history case")
        case_id = history["case_id"]
        if second.get("history", {}).get("case_id") != case_id:
            raise RuntimeError("idempotency replay returned a different case")

        owner_history = require_status(
            client.get("/api/v1/history", headers=headers), 200, "history list"
        )
        items = owner_history.get("items")
        if not isinstance(items, list) or case_id not in [item.get("case_id") for item in items]:
            raise RuntimeError("owner history does not contain the saved case")

        detail = require_status(
            client.get(f"/api/v1/history/{case_id}", headers=headers), 200, "history detail"
        )
        if detail.get("history", {}).get("case_id") != case_id:
            raise RuntimeError("history detail did not return the requested case")

        non_owner = client.get(
            f"/api/v1/history/{case_id}",
            headers={"Authorization": f"Bearer {other_token}"},
        )
        require_status(non_owner, 404, "non-owner history detail")

        preview = require_status(
            client.post(f"/api/v1/history/{case_id}/community-preview", headers=headers),
            200,
            "community preview",
        )
        preview_id = preview.get("preview_id")
        if not isinstance(preview_id, str):
            raise RuntimeError("community preview did not return preview_id")

        published = require_status(
            client.post(
                f"/api/v1/history/{case_id}/community",
                headers={"Authorization": f"Bearer {owner_token}"},
                json={
                    "preview_id": preview_id,
                    "publication_consent": True,
                    "rag_reuse_consent": True,
                },
            ),
            200,
            "community publish",
        )
        if published.get("community_state") != "PUBLISHED_UNVERIFIED":
            raise RuntimeError("community publish did not return PUBLISHED_UNVERIFIED")

        other_headers = {"Authorization": f"Bearer {other_token}"}
        community_feed = require_status(
            client.get("/api/v1/community", headers=other_headers), 200, "community feed"
        )
        community_items = community_feed.get("items")
        if not isinstance(community_items, list) or case_id not in [
            item.get("case_id") for item in community_items if isinstance(item, dict)
        ]:
            raise RuntimeError("published community case is missing from feed")

        require_status(
            client.get(f"/api/v1/community/{case_id}", headers=other_headers),
            200,
            "community detail",
        )
        vote = require_status(
            client.post(
                f"/api/v1/community/{case_id}/vote",
                headers=other_headers,
                json={"vote": "WASPADA"},
            ),
            200,
            "community vote",
        )
        if vote.get("user_vote") != "WASPADA":
            raise RuntimeError("community vote was not persisted")

        withdrawal = require_status(
            client.delete(f"/api/v1/history/{case_id}/community", headers=headers),
            200,
            "community withdrawal",
        )
        if withdrawal.get("community_state") != "WITHDRAWN":
            raise RuntimeError("community withdrawal did not return WITHDRAWN")
        repeated_withdrawal = require_status(
            client.delete(f"/api/v1/history/{case_id}/community", headers=headers),
            200,
            "repeated community withdrawal",
        )
        if repeated_withdrawal != withdrawal:
            raise RuntimeError("repeated community withdrawal did not return the original state")

        withdrawn_feed = require_status(
            client.get("/api/v1/community", headers=other_headers), 200, "withdrawn community feed"
        )
        withdrawn_items = withdrawn_feed.get("items")
        if not isinstance(withdrawn_items, list) or case_id in [
            item.get("case_id") for item in withdrawn_items if isinstance(item, dict)
        ]:
            raise RuntimeError("withdrawn community case is still visible in feed")
        require_status(
            client.get(f"/api/v1/community/{case_id}", headers=other_headers),
            404,
            "withdrawn community detail",
        )
        require_status(
            client.post(
                f"/api/v1/community/{case_id}/vote",
                headers=other_headers,
                json={"vote": "VALID"},
            ),
            404,
            "withdrawn community vote",
        )

    duration_ms = round((time.monotonic() - started) * 1000)
    print(
        "PASS staging smoke "
        f"status=COMPLETED execution_mode=MOCK case_id={case_id} duration_ms={duration_ms}"
    )


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, httpx.HTTPError) as error:
        print(f"FAIL staging smoke: {error}", file=sys.stderr)
        raise SystemExit(1) from error
