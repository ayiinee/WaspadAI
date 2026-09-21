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


class ImageVerificationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question: str | None = Field(default=None, max_length=500)

    @field_validator("question", mode="before")
    @classmethod
    def strip_question(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value


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
    input_text: str | None = None


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


class CommunityVoteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    vote: Literal["HOAKS", "WASPADA", "VALID"]


class CommunityVoteCounts(BaseModel):
    model_config = ConfigDict(extra="forbid")

    HOAKS: int = Field(default=0, ge=0)
    WASPADA: int = Field(default=0, ge=0)
    VALID: int = Field(default=0, ge=0)


class CommunityItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: UUID
    title: str
    redacted_text: str
    status: Literal["PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE"]
    published_at: str
    has_image: bool = False
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

    case_id: UUID
    title: str
    redacted_text: str
    status: Literal["PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE"]
    published_at: str
    has_image: bool = False
    counts: CommunityVoteCounts
    user_vote: Literal["HOAKS", "WASPADA", "VALID"] | None
    result: AIResult
    execution_mode: Literal["MOCK", "REMOTE"]
    responses: list["CommunityResponseItem"] = Field(default_factory=list)
    like_count: int = Field(default=0, ge=0)
    view_count: int = Field(default=0, ge=0)
    comment_count: int = Field(default=0, ge=0)
    share_count: int = Field(default=0, ge=0)
    user_liked: bool = False


class CommunityResponseItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    response_id: UUID
    author: str
    created_at: str
    vote: Literal["HOAKS", "WASPADA", "VALID"]
    reasoning: str
    has_image: bool = False


class CommunityVoteResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: UUID
    user_vote: Literal["HOAKS", "WASPADA", "VALID"] | None
    counts: CommunityVoteCounts


class CommunitySocialResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

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
    redactions: list[str]
    confirmation_required: Literal[True] = True


class CommunityPublishRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    preview_id: UUID
    publication_consent: Literal[True]
    rag_reuse_consent: bool = False


class CommunityStateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: UUID
    community_state: Literal["PRIVATE", "PUBLISHED_UNVERIFIED", "VERIFIED_EVIDENCE", "WITHDRAWN"]
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


class LearningModuleItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_id: UUID
    slug: str
    title: str
    summary: str
    difficulty: int = Field(ge=1, le=5)
    version: int = Field(ge=1)
    total_lessons: int = Field(ge=0)
    completed_lessons: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    topic: str | None = None
    cover_image_url: str | None = None


class LearningLesson(BaseModel):
    model_config = ConfigDict(extra="forbid")

    lesson_id: UUID
    title: str
    body_md: str
    duration_minutes: int = Field(ge=1)
    display_order: int = Field(ge=0)
    completed: bool


class LearningCase(BaseModel):
    model_config = ConfigDict(extra="forbid")

    case_id: UUID
    title: str
    description: str
    reference_url: str | None = None


class LearningMedia(BaseModel):
    model_config = ConfigDict(extra="forbid")

    media_id: UUID
    media_type: Literal["IMAGE", "YOUTUBE"]
    url: str
    title: str
    alt_text: str = ""


class LearningModuleDetail(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_id: UUID
    slug: str
    title: str
    summary: str
    difficulty: int = Field(ge=1, le=5)
    version: int = Field(ge=1)
    total_lessons: int = Field(ge=0)
    completed_lessons: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    lessons: list[LearningLesson]
    topic: str | None = None
    cover_image_url: str | None = None
    cases: list[LearningCase] = Field(default_factory=list)
    media: list[LearningMedia] = Field(default_factory=list)


class LessonCompleteResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    lesson_id: UUID
    module_id: UUID
    completed: Literal[True] = True
    completed_at: str
    progress_percent: float = Field(ge=0, le=100)


class QuizOption(BaseModel):
    model_config = ConfigDict(extra="forbid")

    option_id: UUID
    text: str


class QuizQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question_id: UUID
    text: str
    options: list[QuizOption]


class LearningQuiz(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_id: UUID
    module_version: int = Field(ge=1)
    questions: list[QuizQuestion]


class QuizAttemptAnswer(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question_id: UUID
    selected_option_id: UUID


class QuizAttemptRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_version: int = Field(ge=1)
    answers: list[QuizAttemptAnswer] = Field(min_length=1)

    @model_validator(mode="after")
    def reject_duplicate_questions(self) -> QuizAttemptRequest:
        question_ids = [answer.question_id for answer in self.answers]
        if len(set(question_ids)) != len(question_ids):
            raise ValueError("answers must contain each question at most once")
        return self


class QuizQuestionFeedback(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question_id: UUID
    selected_option_id: UUID
    correct: bool
    explanation: str


class QuizAttemptResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    attempt_id: UUID
    score: float = Field(ge=0, le=100)
    correct_answers: int = Field(ge=0)
    total_questions: int = Field(ge=1)
    feedback: list[QuizQuestionFeedback]


class LearningProgressItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_id: UUID
    completed_lessons: int = Field(ge=0)
    total_lessons: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    latest_score: float | None = Field(default=None, ge=0, le=100)
    best_score: float | None = Field(default=None, ge=0, le=100)
    updated_at: str
    first_opened_at: str | None = None
    last_opened_at: str | None = None


class LearningProgressResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[LearningProgressItem]
