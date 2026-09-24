from __future__ import annotations

import asyncio
import sys
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import UUID, uuid4

import httpx
from fastapi import (
    Depends,
    FastAPI,
    File,
    Form,
    Header,
    HTTPException,
    Query,
    Request,
    UploadFile,
    status,
)
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response

from app.api.router import api_router
from app.auth import AuthenticatedUser, get_current_user
from app.community_realtime import CommunityConnectionManager
from app.config import get_settings
from app.database import create_pool
from app.errors import ProductAPIError, error_body
from app.learning_service import (
    complete_lesson,
    get_learning_media_asset,
    get_learning_module_detail,
    get_learning_progress,
    get_module_cases,
    get_module_quiz,
    list_learning_modules,
    open_learning_module,
    submit_quiz_attempt,
)
from app.models import (
    HistoryPage,
    ImageVerificationRequest,
    LearningCase,
    LearningModuleDetail,
    LearningModuleItem,
    LearningProgressResponse,
    LearningQuiz,
    LessonCompleteResponse,
    QuizAttemptRequest,
    QuizAttemptResult,
    TextVerificationRequest,
    VerificationEnvelope,
)
from app.privacy.image_validation import (
    matches_image_signature,
    validate_image_dimensions,
)
from app.verification_service import (
    get_history_detail,
    list_history,
    verify_image,
    verify_text,
)

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

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
    app.state.auth_client = httpx.AsyncClient(timeout=httpx.Timeout(15.0))
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
    app.state.community_connections = CommunityConnectionManager()
    app.include_router(api_router)

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
    @app.post(
        "/api/v1/verify/text",
        tags=["Verification"],
        response_model=VerificationEnvelope,
        status_code=status.HTTP_200_OK,
        include_in_schema=False,
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
        return await verify_text(
            pool,
            app.state.settings,
            user.id,
            idempotency_key,
            payload,
            app.state.http_client,
        )

    @app.post(
        "/api/v1/verifications/image",
        tags=["Verification"],
        response_model=VerificationEnvelope,
        status_code=status.HTTP_200_OK,
    )
    @app.post(
        "/api/v1/verify/image",
        tags=["Verification"],
        response_model=VerificationEnvelope,
        status_code=status.HTTP_200_OK,
        include_in_schema=False,
    )
    async def verify_image_endpoint(
        image: UploadFile = File(...),
        question: str | None = Form(default=None, max_length=500),
        idempotency_key: UUID = Header(alias="Idempotency-Key"),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> VerificationEnvelope:
        if image.content_type not in {"image/jpeg", "image/png", "image/webp"}:
            raise ProductAPIError(
                415, "UNSUPPORTED_MEDIA_TYPE", "Gunakan gambar JPG, PNG, atau WEBP."
            )
        image_bytes = await image.read(app.state.settings.max_image_bytes + 1)
        if len(image_bytes) > app.state.settings.max_image_bytes:
            raise ProductAPIError(
                413, "PAYLOAD_TOO_LARGE", "Ukuran gambar melebihi batas yang diizinkan."
            )
        if not image_bytes:
            raise ProductAPIError(422, "VALIDATION_ERROR", "File gambar tidak boleh kosong.")
        if not matches_image_signature(image_bytes, image.content_type):
            raise ProductAPIError(
                415, "UNSUPPORTED_MEDIA_TYPE", "Isi file tidak cocok dengan format gambar."
            )
        # GAP-7: validate pixel dimensions per contract §5.2
        validate_image_dimensions(image_bytes, image.content_type)
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        payload = ImageVerificationRequest(question=question)
        return await verify_image(
            pool,
            app.state.settings,
            user.id,
            idempotency_key,
            image_bytes,
            image.content_type,
            payload,
            app.state.http_client,
        )

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

    @app.get(
        "/api/v1/learning/modules",
        tags=["Learning"],
        response_model=list[LearningModuleItem],
    )
    async def list_learning_modules_endpoint(
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> list[LearningModuleItem]:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await list_learning_modules(pool, app.state.settings, user.id)

    @app.get(
        "/api/v1/learning/modules/{module_id}",
        tags=["Learning"],
        response_model=LearningModuleDetail,
    )
    async def get_learning_module_detail_endpoint(
        module_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> LearningModuleDetail:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await get_learning_module_detail(pool, app.state.settings, user.id, module_id)

    @app.post(
        "/api/v1/learning/lessons/{lesson_id}/complete",
        tags=["Learning"],
        response_model=LessonCompleteResponse,
    )
    async def complete_lesson_endpoint(
        lesson_id: UUID,
        idempotency_key: UUID = Header(alias="Idempotency-Key"),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> LessonCompleteResponse:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await complete_lesson(pool, app.state.settings, user.id, lesson_id, idempotency_key)

    @app.get(
        "/api/v1/learning/modules/{module_id}/quiz",
        tags=["Learning"],
        response_model=LearningQuiz,
    )
    async def get_module_quiz_endpoint(
        module_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> LearningQuiz:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await get_module_quiz(pool, app.state.settings, user.id, module_id)

    @app.get("/api/v1/learning/modules/{module_id}/cases", tags=["Learning"], response_model=list[LearningCase])
    async def get_module_cases_endpoint(
        module_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> list[LearningCase]:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        return await get_module_cases(pool, app.state.settings, user.id, module_id)

    @app.get("/api/v1/learning/media/{object_path:path}", tags=["Learning"])
    async def get_learning_media_endpoint(
        object_path: str,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> Response:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        content, content_type = await get_learning_media_asset(
            pool, app.state.settings, user.id, object_path, app.state.http_client
        )
        return Response(
            content=content,
            media_type=content_type,
            headers={"Cache-Control": "private, max-age=86400"},
        )

    @app.post("/api/v1/learning/modules/{module_id}/open", tags=["Learning"], status_code=status.HTTP_204_NO_CONTENT)
    async def open_learning_module_endpoint(
        module_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> None:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        await open_learning_module(pool, app.state.settings, user.id, module_id)

    @app.post(
        "/api/v1/learning/modules/{module_id}/quiz-attempts",
        tags=["Learning"],
        response_model=QuizAttemptResult,
        status_code=status.HTTP_201_CREATED,
    )
    async def submit_quiz_attempt_endpoint(
        module_id: UUID,
        payload: QuizAttemptRequest,
        idempotency_key: UUID = Header(alias="Idempotency-Key"),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> QuizAttemptResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await submit_quiz_attempt(
            pool, app.state.settings, user.id, module_id, idempotency_key, payload
        )

    @app.get(
        "/api/v1/learning/progress",
        tags=["Learning"],
        response_model=LearningProgressResponse,
    )
    async def get_learning_progress_endpoint(
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> LearningProgressResponse:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await get_learning_progress(pool, app.state.settings, user.id)

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
