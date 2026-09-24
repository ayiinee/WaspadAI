from __future__ import annotations

from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ProfileResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    user_id: UUID
    email: str | None = None
    display_name: str
    bio: str | None = None
    avatar_url: str | None = None
    created_at: datetime


class ProfileUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    display_name: str | None = Field(default=None, min_length=1, max_length=80)
    bio: str | None = Field(default=None, max_length=500)

    @field_validator("display_name", mode="before")
    @classmethod
    def normalize_display_name(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.strip()
            if not value:
                raise ValueError("display_name tidak boleh kosong")
        return value

    @field_validator("bio", mode="before")
    @classmethod
    def normalize_bio(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip() or None
        return value


class VerificationOverview(BaseModel):
    total: int = Field(ge=0)
    private: int = Field(ge=0)
    published_unverified: int = Field(ge=0)
    verified_evidence: int = Field(ge=0)
    withdrawn: int = Field(ge=0)


class PublicationOverview(BaseModel):
    total: int = Field(ge=0)
    published_unverified: int = Field(ge=0)
    verified_evidence: int = Field(ge=0)
    withdrawn: int = Field(ge=0)


class CommunityActivityOverview(BaseModel):
    assessments: int = Field(ge=0)
    evidence_added: int = Field(ge=0)
    resolved_cases: int = Field(ge=0)


class LearningOverview(BaseModel):
    total_modules: int = Field(ge=0)
    completed_modules: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    latest_score: float | None = Field(default=None, ge=0, le=100)
    best_score: float | None = Field(default=None, ge=0, le=100)
    last_activity_at: datetime | None = None


class ProfileOverviewResponse(BaseModel):
    verification: VerificationOverview
    publications: PublicationOverview
    community_activity: CommunityActivityOverview
    learning: LearningOverview


class ProfileVerificationItem(BaseModel):
    case_id: UUID
    community_id: UUID | None = None
    headline: str
    verdict: str
    community_state: str
    requires_human_review: bool
    created_at: datetime


class ProfilePublicationItem(BaseModel):
    community_id: UUID
    case_id: UUID
    title: str
    status: str
    published_at: datetime


class ProfileCommunityActivityItem(BaseModel):
    id: str
    kind: Literal["ASSESSMENT", "CONTRIBUTION"]
    title: str
    status: str
    created_at: datetime


class ProfileLearningItem(BaseModel):
    module_id: UUID
    title: str
    completed_lessons: int = Field(ge=0)
    total_lessons: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    latest_score: float | None = Field(default=None, ge=0, le=100)
    best_score: float | None = Field(default=None, ge=0, le=100)
    updated_at: datetime | None = None


class ProfileVerificationPage(BaseModel):
    items: list[ProfileVerificationItem]
    has_more: bool


class ProfilePublicationPage(BaseModel):
    items: list[ProfilePublicationItem]
    has_more: bool


class ProfileCommunityActivityPage(BaseModel):
    items: list[ProfileCommunityActivityItem]
    has_more: bool


class ProfileLearningResponse(BaseModel):
    items: list[ProfileLearningItem]
