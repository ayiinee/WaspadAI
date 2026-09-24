from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID


@dataclass(frozen=True)
class HomeProfile:
    display_name: str


@dataclass(frozen=True)
class HomeCase:
    community_id: UUID
    case_id: UUID
    creator_name: str
    title: str
    summary: str
    verdict: str
    risk_level: str
    requires_human_review: bool
    created_at: datetime
    image_url: str | None


@dataclass(frozen=True)
class HomeLearningRecommendation:
    module_id: UUID
    title: str
    summary: str
    image_url: str | None
    progress_percent: float


@dataclass(frozen=True)
class HomeSnapshot:
    profile: HomeProfile | None
    recent_cases: list[HomeCase]
    learning_recommendations: list[HomeLearningRecommendation]
