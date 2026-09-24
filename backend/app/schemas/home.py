from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class HomeProfileResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    display_name: str = Field(min_length=1, max_length=80)


class HomeCaseResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    community_id: UUID
    case_id: UUID
    creator_name: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=1000)
    summary: str = Field(min_length=1, max_length=5000)
    verdict: str = Field(min_length=1, max_length=100)
    risk_level: str = Field(min_length=1, max_length=100)
    requires_human_review: bool
    created_at: datetime
    image_url: str | None = None


class HomeLearningRecommendationResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    module_id: UUID
    title: str = Field(min_length=1, max_length=200)
    summary: str = Field(min_length=1, max_length=2000)
    image_url: str | None = None
    progress_percent: float = Field(ge=0, le=100)


class HomeResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    profile: HomeProfileResponse
    recent_cases: list[HomeCaseResponse] = Field(max_length=3)
    learning_recommendations: list[HomeLearningRecommendationResponse] = Field(max_length=2)
