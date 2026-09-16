from __future__ import annotations

import ipaddress
from enum import StrEnum
from typing import Any, Literal
from urllib.parse import urlsplit
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class SenderContext(StrEnum):
    NOT_APPLICABLE = "NOT_APPLICABLE"
    UNKNOWN_NUMBER = "UNKNOWN_NUMBER"
    KNOWN_CONTACT = "KNOWN_CONTACT"
    FORWARDED = "FORWARDED"
    SOCIAL_MEDIA = "SOCIAL_MEDIA"
    UNKNOWN = "UNKNOWN"


class PageContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str | None = Field(default=None, max_length=300)
    before: str | None = Field(default=None, max_length=500)
    after: str | None = Field(default=None, max_length=500)

    @field_validator("title", "before", "after", mode="before")
    @classmethod
    def strip_optional_text(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value


class TextVerificationRequest(BaseModel):
    """Client payload. output_mode is intentionally absent and therefore forbidden."""

    model_config = ConfigDict(extra="forbid")

    text: str = Field(min_length=10, max_length=25_000)
    question: str | None = Field(default=None, max_length=500)
    source_url: str | None = Field(default=None, max_length=2048)
    sender_context: SenderContext = SenderContext.UNKNOWN
    page_context: PageContext | None = None

    @field_validator("text", mode="before")
    @classmethod
    def strip_text(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value

    @field_validator("question", mode="before")
    @classmethod
    def strip_question(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value

    @field_validator("source_url")
    @classmethod
    def require_public_http_url(cls, value: str | None) -> str | None:
        if value is None:
            return None
        parsed = urlsplit(value)
        hostname = parsed.hostname
        if (
            parsed.scheme.lower() not in {"http", "https"}
            or hostname is None
            or parsed.username is not None
            or parsed.password is not None
        ):
            raise ValueError("source_url must be a public HTTP(S) URL")
        normalized_host = hostname.rstrip(".").lower()
        if normalized_host == "localhost" or normalized_host.endswith(".local"):
            raise ValueError("source_url must not target a local host")
        try:
            address = ipaddress.ip_address(normalized_host)
        except ValueError:
            return value
        if (
            address.is_private
            or address.is_loopback
            or address.is_link_local
            or address.is_multicast
            or address.is_reserved
            or address.is_unspecified
        ):
            raise ValueError("source_url must not target a private address")
        return value

    @model_validator(mode="after")
    def reject_blank_text(self) -> TextVerificationRequest:
        if not self.text:
            raise ValueError("text must not be blank")
        return self


class AIResult(BaseModel):
    """The Product stores the complete, validated upstream-shaped result."""

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


class HistoryMeta(BaseModel):
    saved: bool
    case_id: UUID | None
    save_reason: str
    community_eligible: bool
    community_state: str


class VerificationEnvelope(BaseModel):
    request_id: UUID
    status: Literal["COMPLETED"]
    history: HistoryMeta
    result: AIResult
    execution_mode: Literal["MOCK", "REMOTE"]


class HistoryItem(BaseModel):
    case_id: UUID
    input_type: Literal["TEXT", "IMAGE"]
    headline: str
    verdict: str
    requires_human_review: bool
    community_state: str
    created_at: str


class HistoryPage(BaseModel):
    items: list[HistoryItem]
    next_cursor: str | None
