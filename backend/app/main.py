from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import UUID, uuid4

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.auth import AuthenticatedUser, get_current_user
from app.config import get_settings
from app.database import create_pool
from app.errors import ProductAPIError, error_body
from app.models import HistoryPage, TextVerificationRequest, VerificationEnvelope
from app.verification_service import get_history_detail, list_history, verify_text


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

    @app.middleware("http")
    async def attach_request_id(request: Request, call_next: object) -> object:
        request.state.request_id = str(uuid4())
        response = await call_next(request)  # type: ignore[operator]
        response.headers["X-Request-ID"] = request.state.request_id
        return response

    @app.exception_handler(ProductAPIError)
    async def product_api_error_handler(request: Request, error: ProductAPIError) -> JSONResponse:
        return JSONResponse(
            status_code=error.status_code,
            content=error_body(error, _request_id(request)),
            headers=_retry_after_header(error.retry_after_seconds),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, _: RequestValidationError) -> JSONResponse:
        error = ProductAPIError(422, "VALIDATION_ERROR", "Input request tidak valid.")
        return JSONResponse(status_code=422, content=error_body(error, _request_id(request)))

    @app.exception_handler(HTTPException)
    async def http_error_handler(request: Request, error: HTTPException) -> JSONResponse:
        safe = _safe_http_error(error)
        return JSONResponse(
            status_code=safe.status_code, content=error_body(safe, _request_id(request))
        )

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

    @app.post(
        "/api/v1/verifications/text",
        tags=["Verification"],
        response_model=VerificationEnvelope,
        status_code=status.HTTP_200_OK,
    )
    async def verify_text_endpoint(
        payload: TextVerificationRequest,
        idempotency_key: UUID = Header(alias="Idempotency-Key"),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> VerificationEnvelope:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await verify_text(pool, app.state.settings, user.id, idempotency_key, payload)

    @app.get("/api/v1/history", tags=["History"], response_model=HistoryPage)
    async def list_history_endpoint(
        limit: int = Query(default=20, ge=1, le=100),
        cursor: str | None = Query(default=None, max_length=2048),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> HistoryPage:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await list_history(pool, app.state.settings, user.id, limit, cursor)

    @app.get("/api/v1/history/{case_id}", tags=["History"], response_model=VerificationEnvelope)
    async def get_history_detail_endpoint(
        case_id: UUID, user: AuthenticatedUser = Depends(get_current_user)
    ) -> VerificationEnvelope:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await get_history_detail(pool, app.state.settings, user.id, case_id)

    return app


app = create_app()


def _request_id(request: Request) -> str:
    return getattr(request.state, "request_id", str(uuid4()))


def _retry_after_header(retry_after_seconds: int | None) -> dict[str, str] | None:
    return {"Retry-After": str(retry_after_seconds)} if retry_after_seconds is not None else None


def _safe_http_error(error: HTTPException) -> ProductAPIError:
    if error.status_code == status.HTTP_401_UNAUTHORIZED:
        return ProductAPIError(401, "INVALID_ACCESS_TOKEN", "Sesi tidak valid atau sudah berakhir.")
    if error.status_code == status.HTTP_503_SERVICE_UNAVAILABLE:
        return ProductAPIError(
            503, "SERVICE_UNAVAILABLE", "Layanan autentikasi atau database tidak tersedia.", True
        )
    return ProductAPIError(error.status_code, "REQUEST_REJECTED", "Request tidak dapat diproses.")
