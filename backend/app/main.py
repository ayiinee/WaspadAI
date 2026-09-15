from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException, status

from app.config import get_settings
from app.database import create_pool


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    app.state.settings = settings
    app.state.http_client = httpx.AsyncClient(
        timeout=httpx.Timeout(
            connect=settings.ai_service_connect_timeout_seconds,
            read=settings.ai_service_read_timeout_seconds,
            write=settings.ai_service_write_timeout_seconds,
            pool=settings.ai_service_pool_timeout_seconds,
        ),
        limits=httpx.Limits(
            max_connections=settings.ai_service_max_connections,
            max_keepalive_connections=settings.ai_service_max_keepalive_connections,
        ),
    )
    app.state.auth_client = httpx.AsyncClient(timeout=httpx.Timeout(5.0))
    app.state.db_pool = create_pool(settings)
    if app.state.db_pool is not None:
        await app.state.db_pool.open(wait=False)
    try:
        yield
    finally:
        if app.state.db_pool is not None:
            await app.state.db_pool.close()
        await app.state.auth_client.aclose()
        await app.state.http_client.aclose()


def create_app() -> FastAPI:
    app = FastAPI(title="WaspadAI Product API", version="0.1.0", lifespan=lifespan)

    @app.get("/api/health", tags=["Operations"])
    async def health() -> dict[str, str]:
        return {"status": "ok", "service": "waspadai-product"}

    @app.get("/api/ready", tags=["Operations"])
    async def readiness() -> dict[str, str]:
        pool = app.state.db_pool
        if pool is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="database is not configured",
            )
        try:
            async with pool.connection(timeout=2) as connection:
                await connection.execute("select 1")
        except Exception as error:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="database unavailable",
            ) from error
        return {"status": "ready"}

    return app


app = create_app()
