from __future__ import annotations

from typing import Any

# Synthetic, non-user data for local contract testing only.
COMMUNITY_EVIDENCE_FIXTURE: list[dict[str, Any]] = [
    {
        "schema_version": "1.0",
        "record_type": "COMMUNITY_VERIFIED_EVIDENCE",
        "community_post_id": "00000000-0000-0000-0000-000000000122",
        "case_id": "00000000-0000-0000-0000-000000000102",
        "revision": 1,
        "content_hash": "59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f",
        "status": "VERIFIED_EVIDENCE",
        "title": "Klaim bantuan tunai melalui tautan tidak resmi",
        "verified_claim": (
            "Tautan pada pesan bantuan tunai tersebut bukan kanal resmi program pemerintah."
        ),
        "stance": "REFUTES",
        "evidence_summary": (
            "Moderator memverifikasi sumber resmi yang menyatakan program bantuan "
            "diumumkan melalui kanal pemerintah."
        ),
        "redacted_text": "Pesan menawarkan bantuan tunai melalui tautan tidak resmi.",
        "published_at": "2026-09-15T10:00:00Z",
        "verified_at": "2026-09-15T12:30:00Z",
        "sources": [
            {
                "source_url": "https://example.go.id/klarifikasi-bantuan",
                "title": "Klarifikasi program bantuan",
                "publisher": "Instansi resmi",
                "published_at": None,
            }
        ],
    }
]


def hardcoded_community_evidence() -> list[dict[str, Any]]:
    """Return a copy so callers cannot mutate the shared fixture."""
    return [
        dict(record, sources=[dict(source) for source in record["sources"]])
        for record in COMMUNITY_EVIDENCE_FIXTURE
    ]
