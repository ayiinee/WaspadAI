from __future__ import annotations

import asyncio
import sys
from pathlib import Path

from psycopg import AsyncConnection
from psycopg.rows import dict_row

from app.config import get_settings

SEED_FILE = Path(__file__).parents[2] / "supabase" / "seed.sql"


async def main() -> None:
    settings = get_settings()
    if settings.app_env == "production":
        raise RuntimeError("Seeder development tidak boleh dijalankan di production.")
    if settings.migration_database_url is None:
        raise RuntimeError(
            "MIGRATION_DATABASE_URL belum dikonfigurasi. Seeder memerlukan role migrator."
        )

    seed_sql = SEED_FILE.read_text(encoding="utf-8")
    async with await AsyncConnection.connect(
        settings.migration_database_url.get_secret_value(),
        row_factory=dict_row,
    ) as connection:
        async with connection.transaction():
            await connection.execute(seed_sql)
        result = await connection.execute(
            """select
                   count(*) filter (
                       where id between '10000000-0000-0000-0000-000000000711'::uuid
                                    and '10000000-0000-0000-0000-000000000718'::uuid
                   )::int as seeded_posts,
                   count(*) filter (
                       where status = 'VERIFIED_EVIDENCE' and withdrawn_at is null
                   )::int as verified_posts
                 from public.community_posts"""
        )
        counts = await result.fetchone()
        latest_result = await connection.execute(
            """select p.title,
                      private.community_display_name(p.owner_id) as creator_name,
                      c.verdict
                 from public.community_posts p
                 join public.verification_cases c on c.id = p.case_id
                where p.withdrawn_at is null
                  and p.status = 'VERIFIED_EVIDENCE'
                  and c.requires_human_review = false
                  and upper(c.verdict) in (
                      'HOAX', 'HOAKS', 'PALSU', 'FALSE',
                      'VALID', 'BENAR', 'TRUE', 'FAKTA'
                  )
                order by coalesce(p.verified_at, p.published_at) desc, p.case_id desc
                limit 3"""
        )
        latest = await latest_result.fetchall()

    print(
        "Development seed applied: "
        f"{counts['seeded_posts']} demo posts, "
        f"{counts['verified_posts']} total verified posts."
    )
    for position, post in enumerate(latest, start=1):
        print(f"  {position}. [{post['verdict']}] {post['title']} — {post['creator_name']}")


if __name__ == "__main__":
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
