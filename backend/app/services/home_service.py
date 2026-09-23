from __future__ import annotations

from uuid import UUID

from app.repositories.interfaces.home_repository import HomeRepository
from app.schemas.home import (
    HomeCaseResponse,
    HomeLearningRecommendationResponse,
    HomeProfileResponse,
    HomeResponse,
)


async def get_home_dashboard(
    repository: HomeRepository,
    user_id: UUID,
    user_email: str | None,
) -> HomeResponse:
    snapshot = await repository.load(user_id, case_limit=3, learning_limit=2)
    display_name = (
        snapshot.profile.display_name
        if snapshot.profile is not None
        else _fallback_display_name(user_email)
    )
    return HomeResponse(
        profile=HomeProfileResponse(display_name=display_name),
        recent_cases=[
            HomeCaseResponse(
                case_id=item.case_id,
                title=item.title,
                summary=item.summary,
                verdict=item.verdict,
                risk_level=item.risk_level,
                requires_human_review=item.requires_human_review,
                created_at=item.created_at,
            )
            for item in snapshot.recent_cases
        ],
        learning_recommendations=[
            HomeLearningRecommendationResponse(
                module_id=item.module_id,
                title=item.title,
                summary=item.summary,
                image_url=item.image_url,
                progress_percent=item.progress_percent,
            )
            for item in snapshot.learning_recommendations
        ],
    )


def _fallback_display_name(email: str | None) -> str:
    if email:
        local_part = email.split("@", 1)[0].strip()
        if local_part:
            return local_part[:80]
    return "Pengguna WaspadAI"
