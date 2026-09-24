from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict


class AIResult(BaseModel):
    """Complete validated result returned by the upstream verification service."""

    model_config = ConfigDict(extra="forbid")

    request_id: str
    trace_id: str
    status: Literal["COMPLETED"]
    mode: Literal["LIVE"]
    mode_notice: str
    input_summary: dict[str, Any]
    verdict: str
    risk_level: str
    dimensions: dict[str, str]
    headline: str
    evidence_sufficiency: float
    evidence_sufficiency_label: str
    what_checked: list[str]
    why: list[str]
    evidence: list[dict[str, Any]]
    recommended_actions: list[dict[str, Any]]
    sources: list[dict[str, Any]]
    uncertainty: str
    requires_human_review: bool
    community_status: str
    privacy_notice: str
    rulebook: dict[str, Any]
    pipeline: list[dict[str, Any]]
    presentation: dict[str, Any]
    disclaimer: str
