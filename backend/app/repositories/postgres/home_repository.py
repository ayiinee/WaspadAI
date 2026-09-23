from __future__ import annotations

from uuid import UUID

from psycopg_pool import AsyncConnectionPool

from app.database import user_transaction
from app.domain.home.models import (
    HomeCase,
    HomeLearningRecommendation,
    HomeProfile,
    HomeSnapshot,
)


class PostgresHomeRepository:
    def __init__(self, pool: AsyncConnectionPool, statement_timeout_seconds: int) -> None:
        self._pool = pool
        self._statement_timeout_seconds = statement_timeout_seconds

    async def load(
        self,
        user_id: UUID,
        case_limit: int,
        learning_limit: int,
    ) -> HomeSnapshot:
        async with user_transaction(
            self._pool,
            user_id,
            self._statement_timeout_seconds,
        ) as connection:
            profile_query = await connection.execute(
                """select display_name
                     from public.profiles
                    where id = %s and is_active = true""",
                (user_id,),
            )
            profile_row = await profile_query.fetchone()

            cases_query = await connection.execute(
                """select p.id as community_id, p.case_id, p.title,
                          p.redacted_text as summary, c.verdict, c.risk_level,
                          c.requires_human_review, p.published_at as created_at
                     from public.community_posts p
                     join public.verification_cases c on c.id = p.case_id
                    where p.withdrawn_at is null
                      and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
                      and p.publication_consent_id is not null
                      and exists (
                          select 1
                            from private.consent_records consent
                           where consent.id = p.publication_consent_id
                             and consent.scope = 'COMMUNITY_PUBLICATION'
                             and consent.revoked_at is null
                             and (consent.expires_at is null or consent.expires_at > now())
                      )
                    order by p.published_at desc, p.case_id desc
                    limit %s""",
                (case_limit,),
            )
            case_rows = await cases_query.fetchall()

            learning_query = await connection.execute(
                """select m.id, m.title, m.summary, m.display_order,
                          count(l.id)::int as total_lessons,
                          count(lp.lesson_id)::int as completed_lessons,
                          (
                              select media.url
                                from public.learning_media media
                               where media.module_id = m.id
                                 and media.media_type = 'IMAGE'
                               order by media.display_order, media.id
                               limit 1
                          ) as image_url
                     from public.published_learning_modules m
                     left join public.published_learning_lessons l on l.module_id = m.id
                     left join public.lesson_progress lp
                       on lp.lesson_id = l.id and lp.user_id = %s
                    group by m.id, m.title, m.summary, m.display_order
                    order by (count(lp.lesson_id) < count(l.id)) desc,
                             m.display_order, m.id
                    limit %s""",
                (user_id, learning_limit),
            )
            learning_rows = await learning_query.fetchall()

        profile = (
            HomeProfile(display_name=profile_row["display_name"])
            if profile_row is not None
            else None
        )
        recent_cases = [
            HomeCase(
                community_id=row["community_id"],
                case_id=row["case_id"],
                title=row["title"],
                summary=row["summary"],
                verdict=row["verdict"],
                risk_level=row["risk_level"],
                requires_human_review=row["requires_human_review"],
                created_at=row["created_at"],
            )
            for row in case_rows
        ]
        learning_recommendations = [
            HomeLearningRecommendation(
                module_id=row["id"],
                title=row["title"],
                summary=row["summary"],
                image_url=row["image_url"],
                progress_percent=_progress_percent(
                    row["completed_lessons"],
                    row["total_lessons"],
                ),
            )
            for row in learning_rows
        ]
        return HomeSnapshot(
            profile=profile,
            recent_cases=recent_cases,
            learning_recommendations=learning_recommendations,
        )


def _progress_percent(completed_lessons: int, total_lessons: int) -> float:
    if total_lessons <= 0:
        return 0.0
    return round(completed_lessons / total_lessons * 100, 2)
