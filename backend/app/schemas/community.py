from __future__ import annotations

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.verification import AIResult


class CommunityVoteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    vote: Literal["HOAKS", "WASPADA", "VALID"]


class CommunityVoteCounts(BaseModel):
    model_config = ConfigDict(extra="forbid")

    HOAKS: int = Field(default=0, ge=0)
    WASPADA: int = Field(default=0, ge=0)
    VALID: int = Field(default=0, ge=0)


class CommunityMediaItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: UUID
    media_type: Literal["IMAGE"] = "IMAGE"
    url: str
    thumbnail_url: str | None = None
    width: int | None = Field(default=None, gt=0)
    height: int | None = Field(default=None, gt=0)
    position: int = Field(ge=0, le=3)


class CommunityCreator(BaseModel):
    model_config = ConfigDict(extra="forbid")

    display_name: str
    is_current_user: bool = False


class CommunityResponseItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    response_id: UUID
    author: str
    created_at: str
    vote: Literal["HOAKS", "WASPADA", "VALID"]
    reasoning: str
    has_image: bool = False


class CommunityItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: UUID
    case_id: UUID
    creator: CommunityCreator
    title: str
    redacted_text: str
    status: Literal["PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE"]
    published_at: str
    has_image: bool = False
    image_url: str | None = None
    media: list[CommunityMediaItem] = Field(default_factory=list, max_length=4)
    counts: CommunityVoteCounts
    user_vote: Literal["HOAKS", "WASPADA", "VALID"] | None = None
    like_count: int = Field(default=0, ge=0)
    view_count: int = Field(default=0, ge=0)
    comment_count: int = Field(default=0, ge=0)
    share_count: int = Field(default=0, ge=0)
    user_liked: bool = False


class CommunityPage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[CommunityItem]
    next_cursor: str | None


class CommunityUserSummary(BaseModel):
    model_config = ConfigDict(extra="forbid")

    assessments_count: int = Field(ge=0)
    evidence_added_count: int = Field(ge=0)
    resolved_cases_count: int = Field(ge=0)


class CommunityBootstrap(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: CommunityUserSummary
    feed: CommunityPage


class CommunityDetail(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: UUID
    case_id: UUID
    creator: CommunityCreator
    title: str
    redacted_text: str
    status: Literal["PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE"]
    published_at: str
    has_image: bool = False
    image_url: str | None = None
    media: list[CommunityMediaItem] = Field(default_factory=list, max_length=4)
    counts: CommunityVoteCounts
    user_vote: Literal["HOAKS", "WASPADA", "VALID"] | None
    result: AIResult
    execution_mode: Literal["MOCK", "REMOTE"]
    responses: list[CommunityResponseItem] = Field(default_factory=list)
    like_count: int = Field(default=0, ge=0)
    view_count: int = Field(default=0, ge=0)
    comment_count: int = Field(default=0, ge=0)
    share_count: int = Field(default=0, ge=0)
    user_liked: bool = False


class CommunityVoteResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    community_id: UUID
    case_id: UUID
    user_vote: Literal["HOAKS", "WASPADA", "VALID"] | None
    counts: CommunityVoteCounts


class CommunityResponseResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    community_id: UUID
    case_id: UUID
    user_vote: Literal["HOAKS", "WASPADA", "VALID"]
    counts: CommunityVoteCounts
    response: CommunityResponseItem


class CommunitySocialResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    community_id: UUID
    case_id: UUID
    liked: bool
    like_count: int = Field(ge=0)
    view_count: int = Field(ge=0)
    comment_count: int = Field(ge=0)
    share_count: int = Field(ge=0)
    share_url: str | None = None


class CommunityPreviewResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    preview_id: UUID
    expires_at: str
    redacted_text: str
    redacted_image_url: str | None
    media: list[CommunityMediaItem] = Field(default_factory=list, max_length=4)
    redactions: list[str]
    confirmation_required: Literal[True] = True


class CommunityPublishRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    preview_id: UUID
    publication_consent: Literal[True]
    rag_reuse_consent: bool = False
    caption: str = Field(min_length=1, max_length=5000)


class CommunityUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    caption: str = Field(min_length=1, max_length=5000)


class CommunityStateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: UUID
    community_state: Literal[
        "PRIVATE",
        "PUBLISHED_UNVERIFIED",
        "VERIFIED_EVIDENCE",
        "WITHDRAWN",
    ]
    revision: int = Field(ge=1)


class CommunityEvidenceSource(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_url: str = Field(min_length=1, max_length=2048)
    title: str = Field(min_length=1, max_length=300)
    publisher: str = Field(min_length=1, max_length=200)
    published_at: str | None = None


class CommunityEvidenceRecord(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: Literal["1.0"] = "1.0"
    record_type: Literal["COMMUNITY_VERIFIED_EVIDENCE"] = "COMMUNITY_VERIFIED_EVIDENCE"
    community_post_id: UUID
    case_id: UUID
    revision: int = Field(ge=1)
    content_hash: str = Field(pattern=r"^[a-f0-9]{64}$")
    status: Literal["VERIFIED_EVIDENCE"] = "VERIFIED_EVIDENCE"
    title: str = Field(min_length=1, max_length=200)
    verified_claim: str = Field(min_length=1, max_length=500)
    stance: Literal["SUPPORTS", "REFUTES", "CONTEXT"]
    evidence_summary: str = Field(min_length=1, max_length=800)
    redacted_text: str = Field(min_length=1, max_length=4_000)
    published_at: str
    verified_at: str
    sources: list[CommunityEvidenceSource] = Field(min_length=1, max_length=3)
