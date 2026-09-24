from __future__ import annotations

from fastapi import Request
from psycopg_pool import AsyncConnectionPool

from app.errors import ProductAPIError


def get_database_pool(request: Request) -> AsyncConnectionPool:
    pool: AsyncConnectionPool | None = request.app.state.db_pool
    if pool is None:
        raise ProductAPIError(
            503,
            "PERSISTENCE_UNAVAILABLE",
            "Database belum dikonfigurasi.",
            True,
        )
    return pool
