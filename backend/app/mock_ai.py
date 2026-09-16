from __future__ import annotations

from app.models import AIResult, TextVerificationRequest


def build_review_required_result(request: TextVerificationRequest, payload_hash: str) -> AIResult:
    """Return a deterministic, clearly labelled fixture without retaining raw input."""

    reference = payload_hash[:16]
    return AIResult.model_validate(
        {
            "request_id": f"mock-{reference}",
            "trace_id": f"mock-trace-{reference}",
            "status": "COMPLETED",
            "mode": "LIVE",
            "mode_notice": "Hasil fixture mode mock; bukan pemeriksaan AI live.",
            "input_summary": {
                "input_type": "TEXT",
                "content_type": "MESSAGE",
                "label": "Input mode mock",
                "media_type": None,
                "dimensions": None,
                "extraction_status": "NOT_APPLICABLE",
                "excerpt": "Input diproses tanpa menyimpan teks mentah pada fixture.",
                "source_url": request.source_url,
                "sender_context": request.sender_context.value,
                "character_count": len(request.text),
                "urls_detected": 0,
                "pii_types_redacted": [],
            },
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
            "headline": "Fixture: bukti belum cukup untuk memastikan klaim",
            "evidence_sufficiency": 0.0,
            "evidence_sufficiency_label": "Fixture, bukan pengukuran AI live",
            "what_checked": [],
            "why": ["Mode mock tidak melakukan inferensi ke layanan AI."],
            "evidence": [],
            "recommended_actions": [],
            "sources": [],
            "uncertainty": "Gunakan mode remote yang tervalidasi untuk hasil faktual.",
            "requires_human_review": True,
            "community_status": "ELIGIBLE_WITH_CONSENT",
            "privacy_notice": "Fixture tidak menyimpan teks mentah dalam hasil AI.",
            "rulebook": {
                "corpus_versions": [],
                "retrieval_mode": "MOCK_FIXTURE",
                "candidate_count": 0,
                "selected_count": 0,
                "forced_rule_ids": [],
                "cache_hit": False,
                "duration_ms": 0,
            },
            "pipeline": [],
            "presentation": {
                "requested_mode": "BOTH",
                "structured": True,
                "narrative": {
                    "text": "SIMULASI. Ini bukan hasil pemeriksaan AI live.",
                    "summary": "Fixture mode mock",
                    "paragraphs": ["SIMULASI. Ini bukan hasil pemeriksaan AI live."],
                },
            },
            "disclaimer": "Fact-check adalah dukungan keputusan, bukan jaminan.",
        }
    )


def build_not_required_result(request: TextVerificationRequest, payload_hash: str) -> AIResult:
    """Fixture non-persisten used by unit tests for the REVIEW_REQUIRED policy."""

    result = build_review_required_result(request, payload_hash).model_copy(deep=True)
    result.verdict = "SUPPORTED"
    result.risk_level = "LOW"
    result.requires_human_review = False
    result.community_status = "NOT_REQUIRED"
    result.headline = "Fixture: klaim didukung"
    return result
