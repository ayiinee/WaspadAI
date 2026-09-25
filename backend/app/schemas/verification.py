from __future__ import annotations

from datetime import date
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class OfficialReferralRoute(BaseModel):
    route_type: str
    priority: Literal["PRIMARY", "SECONDARY"]
    reason: str


class OfficialReferral(BaseModel):
    status: Literal["NOT_REQUIRED", "RECOMMENDED", "URGENT"] = "NOT_REQUIRED"
    mode: Literal["PREVENTION", "RECOVERY"] | None = None
    reason_codes: list[str] = Field(default_factory=list)
    summary: str | None = None
    routes: list[OfficialReferralRoute] = Field(default_factory=list)


class OfficialChannel(BaseModel):
    id: str
    route_type: str
    organization_name: str
    channel_name: str
    description: str
    destination_url: str
    region_code: str | None = None
    is_active: bool = True
    verified_at: date


class ResolvedRoute(BaseModel):
    route_type: str
    priority: Literal["PRIMARY", "SECONDARY"]
    reason: str
    action_type: Literal["EXTERNAL_URL", "GUIDANCE_ONLY"]
    title: str
    guidance: str | None = None
    channel: OfficialChannel | None = None


class OfficialReportingOption(BaseModel):
    subject: Literal["SUSPICIOUS_NUMBER", "SUSPICIOUS_CONTENT"]
    title: str
    description: str
    channel: OfficialChannel


class ResolvedOfficialReferral(BaseModel):
    status: Literal["NOT_REQUIRED", "RECOMMENDED", "URGENT"] = "NOT_REQUIRED"
    mode: Literal["PREVENTION", "RECOVERY"] | None = None
    summary: str | None = None
    routes: list[ResolvedRoute] = Field(default_factory=list)
    government_reporting_options: list[OfficialReportingOption] = Field(default_factory=list)


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
    official_referral: OfficialReferral = Field(default_factory=OfficialReferral)
    resolved_official_referral: ResolvedOfficialReferral | None = None
