from __future__ import annotations

import json
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import UUID

from psycopg import AsyncConnection
from psycopg_pool import AsyncConnectionPool

from app.config import Settings


def create_pool(settings: Settings) -> AsyncConnectionPool | None:
    """Create, but do not open, the bounded runtime pool.

    Queries must open a short transaction and set trusted JWT claims with SET LOCAL.
    No request may hold a database transaction while waiting for the remote AI service.
    """

    if settings.database_url is None:
        return None
    return AsyncConnectionPool(
        conninfo=settings.database_url.get_secret_value(),
        min_size=1,
        max_size=settings.db_pool_size + settings.db_max_overflow,
        open=False,
        kwargs={"autocommit": False},
    )


@asynccontextmanager
async def user_transaction(
    pool: AsyncConnectionPool,
    user_id: UUID,
    statement_timeout_seconds: int,
) -> AsyncIterator[AsyncConnection]:
    """Attach a validated user to a short transaction using local claims only."""

    claims = json.dumps({"sub": str(user_id), "role": "authenticated"})
    async with pool.connection() as connection:
        async with connection.transaction():
            await connection.execute(
                """select set_config('request.jwt.claims', %s, true),
                          set_config('request.jwt.claim.sub', %s, true),
                          set_config('statement_timeout', %s, true)""",
                (claims, str(user_id), f"{statement_timeout_seconds * 1000}ms"),
            )
            yield connection
