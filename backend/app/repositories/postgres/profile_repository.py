# Long SQL projection lines stay intact so aliases remain easy to audit.
# ruff: noqa: E501
from __future__ import annotations

from typing import Any
from uuid import UUID

from psycopg_pool import AsyncConnectionPool

from app.database import user_transaction


class PostgresProfileRepository:
    def __init__(self, pool: AsyncConnectionPool, statement_timeout_seconds: int) -> None:
        self._pool = pool
        self._statement_timeout_seconds = statement_timeout_seconds

    async def get_profile(self, user_id: UUID) -> dict[str, Any] | None:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select p.id, p.display_name, p.bio, p.avatar_asset_id, p.created_at,
                          a.bucket as avatar_bucket, a.object_path as avatar_object_path,
                          a.mime_type as avatar_mime_type
                     from public.profiles p
                     left join private.stored_assets a
                       on a.id = p.avatar_asset_id and a.deleted_at is null
                    where p.id = %s and p.is_active = true""",
                (user_id,),
            )
            return await result.fetchone()

    async def update_profile(
        self, user_id: UUID, display_name: str | None, bio: str | None, update_bio: bool
    ) -> dict[str, Any] | None:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """update public.profiles
                      set display_name = coalesce(%s, display_name),
                          bio = case when %s then %s else bio end
                    where id = %s and is_active = true
                returning id, display_name, bio, avatar_asset_id, created_at""",
                (display_name, update_bio, bio, user_id),
            )
            return await result.fetchone()

    async def overview(self, user_id: UUID) -> dict[str, Any]:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select
                    (select count(*) from public.verification_cases c where c.user_id=%s and c.deleted_at is null)::int verification_total,
                    (select count(*) from public.verification_cases c where c.user_id=%s and c.deleted_at is null and c.community_state='PRIVATE')::int verification_private,
                    (select count(*) from public.verification_cases c where c.user_id=%s and c.deleted_at is null and c.community_state='PUBLISHED_UNVERIFIED')::int verification_published,
                    (select count(*) from public.verification_cases c where c.user_id=%s and c.deleted_at is null and c.community_state='VERIFIED_EVIDENCE')::int verification_verified,
                    (select count(*) from public.verification_cases c where c.user_id=%s and c.deleted_at is null and c.community_state='WITHDRAWN')::int verification_withdrawn,
                    (select count(*) from public.community_posts p where p.owner_id=%s)::int publication_total,
                    (select count(*) from public.community_posts p where p.owner_id=%s and p.status='PUBLISHED_UNVERIFIED')::int publication_published,
                    (select count(*) from public.community_posts p where p.owner_id=%s and p.status='VERIFIED_EVIDENCE')::int publication_verified,
                    (select count(*) from public.community_posts p where p.owner_id=%s and p.status='WITHDRAWN')::int publication_withdrawn,
                    (select count(*) from public.community_votes v where v.user_id=%s)::int assessments,
                    (select count(*) from public.contributions c where c.user_id=%s and c.status<>'RETRACTED')::int evidence_added,
                    (select count(*) from public.contributions c where c.user_id=%s and c.status='VERIFIED')::int resolved_cases,
                    (select count(*) from public.published_learning_modules)::int total_modules,
                    (select count(*) from public.published_learning_modules m where exists (
                        select 1 from public.published_learning_lessons present where present.module_id=m.id
                    ) and not exists (
                        select 1 from public.published_learning_lessons l where l.module_id=m.id and not exists (
                            select 1 from public.lesson_progress lp where lp.lesson_id=l.id and lp.user_id=%s)))::int completed_modules,
                    (select coalesce(count(lp.lesson_id)::numeric / nullif(count(l.id),0) * 100,0)
                       from public.published_learning_lessons l left join public.lesson_progress lp
                         on lp.lesson_id=l.id and lp.user_id=%s)::float progress_percent,
                    (select q.score::float from public.quiz_attempts q where q.user_id=%s order by q.completed_at desc, q.id desc limit 1) latest_score,
                    (select max(q.score)::float from public.quiz_attempts q where q.user_id=%s) best_score,
                    (select max(activity_at) from (
                        select max(lp.completed_at) activity_at from public.lesson_progress lp where lp.user_id=%s
                        union all select max(q.completed_at) from public.quiz_attempts q where q.user_id=%s
                    ) activities) last_activity_at""",
                (user_id,) * 18,
            )
            return await result.fetchone()

    async def list_verifications(
        self, user_id: UUID, limit: int, offset: int, status: str | None
    ) -> list[dict[str, Any]]:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select c.id as case_id, p.id as community_id, c.headline, c.verdict,
                          c.community_state, c.requires_human_review, c.created_at
                     from public.verification_cases c
                     left join public.community_posts p on p.case_id=c.id
                    where c.user_id=%s and c.deleted_at is null
                      and (%s::text is null or c.community_state=%s::text)
                    order by c.created_at desc, c.id desc limit %s offset %s""",
                (user_id, status, status, limit + 1, offset),
            )
            return list(await result.fetchall())

    async def list_publications(
        self, user_id: UUID, limit: int, offset: int, status: str | None
    ) -> list[dict[str, Any]]:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select id as community_id, case_id, title, status, published_at
                     from public.community_posts
                    where owner_id=%s and (%s::text is null or status=%s::text)
                    order by published_at desc, id desc limit %s offset %s""",
                (user_id, status, status, limit + 1, offset),
            )
            return list(await result.fetchall())

    async def list_community_activity(
        self, user_id: UUID, limit: int, offset: int
    ) -> list[dict[str, Any]]:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select * from (
                    select (v.post_id::text || ':assessment') id, 'ASSESSMENT' kind,
                           p.title, v.vote status, v.updated_at created_at
                      from public.community_votes v join public.community_posts p on p.id=v.post_id
                     where v.user_id=%s
                    union all
                    select c.id::text id, 'CONTRIBUTION' kind, c.title, c.status, c.updated_at created_at
                      from public.contributions c where c.user_id=%s
                ) activity order by created_at desc, id desc limit %s offset %s""",
                (user_id, user_id, limit + 1, offset),
            )
            return list(await result.fetchall())

    async def list_learning(self, user_id: UUID) -> list[dict[str, Any]]:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select m.id module_id, m.title, count(l.id)::int total_lessons,
                          count(lp.lesson_id)::int completed_lessons,
                          coalesce(count(lp.lesson_id)::numeric/nullif(count(l.id),0)*100,0)::float progress_percent,
                          latest.score::float latest_score, best.score::float best_score,
                          greatest(max(lp.completed_at), latest.completed_at) updated_at
                     from public.published_learning_modules m
                     left join public.published_learning_lessons l on l.module_id=m.id
                     left join public.lesson_progress lp on lp.lesson_id=l.id and lp.user_id=%s
                     left join lateral (select score, completed_at from public.quiz_attempts q where q.user_id=%s and q.module_id=m.id order by completed_at desc,id desc limit 1) latest on true
                     left join lateral (select max(score) score from public.quiz_attempts q where q.user_id=%s and q.module_id=m.id) best on true
                    group by m.id,m.title,m.display_order,latest.score,latest.completed_at,best.score
                    order by m.display_order,m.id""",
                (user_id, user_id, user_id),
            )
            return list(await result.fetchall())

    async def set_avatar(self, user_id: UUID, asset_id: UUID | None) -> None:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            await connection.execute(
                "update public.profiles set avatar_asset_id=%s where id=%s", (asset_id, user_id)
            )

    async def replace_avatar(self, user_id: UUID, asset_id: UUID) -> dict[str, Any] | None:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select a.id, a.bucket, a.object_path
                     from public.profiles p
                     left join private.stored_assets a on a.id=p.avatar_asset_id
                    where p.id=%s""",
                (user_id,),
            )
            old = await result.fetchone()
            await connection.execute(
                "update public.profiles set avatar_asset_id=%s where id=%s",
                (asset_id, user_id),
            )
            if old is not None and old.get("id") is not None:
                await connection.execute(
                    "update private.stored_assets set deleted_at=now() where id=%s",
                    (old["id"],),
                )
            return old

    async def clear_avatar(self, user_id: UUID) -> dict[str, Any] | None:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """select a.id, a.bucket, a.object_path
                     from public.profiles p join private.stored_assets a on a.id=p.avatar_asset_id
                    where p.id=%s""",
                (user_id,),
            )
            old = await result.fetchone()
            await connection.execute(
                "update public.profiles set avatar_asset_id=null where id=%s", (user_id,)
            )
            if old is not None:
                await connection.execute(
                    "update private.stored_assets set deleted_at=now() where id=%s", (old["id"],)
                )
            return old

    async def insert_avatar_asset(
        self, user_id: UUID, object_path: str, mime_type: str, size: int, digest: str
    ) -> UUID:
        async with user_transaction(
            self._pool, user_id, self._statement_timeout_seconds
        ) as connection:
            result = await connection.execute(
                """insert into private.stored_assets(user_id,bucket,object_path,purpose,mime_type,size_bytes,sha256)
                     values(%s,'profile-assets',%s,'AVATAR',%s,%s,%s) returning id""",
                (user_id, object_path, mime_type, size, digest),
            )
            row = await result.fetchone()
            return row["id"]
