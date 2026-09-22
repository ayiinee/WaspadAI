from __future__ import annotations

import asyncio
import struct
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
    WebSocket,
    WebSocketDisconnect,
    status,
)
from fastapi.exceptions import RequestValidationError
from fastapi.responses import HTMLResponse, JSONResponse, Response

from app.auth import AuthenticatedUser, authenticate_access_token, get_current_user
from app.community_realtime import CommunityConnectionManager
from app.community_service import (
    cast_community_vote,
    create_community_preview,
    get_community_bootstrap,
    get_community_detail,
    get_community_image,
    get_community_media,
    get_community_preview_media,
    get_community_response_image,
    get_community_user_summary,
    like_community,
    list_community,
    publish_community_case,
    remove_community_vote,
    submit_community_response,
    record_community_share,
    record_community_view,
    unlike_community,
    withdraw_community_case,
)
from app.config import get_settings
from app.database import create_pool
from app.errors import ProductAPIError, error_body
from app.learning_service import (
    complete_lesson,
    get_learning_module_detail,
    get_learning_progress,
    get_module_cases,
    open_learning_module,
    get_module_quiz,
    list_learning_modules,
    submit_quiz_attempt,
)
from app.models import (
    CommunityBootstrap,
    CommunityDetail,
    CommunityPage,
    CommunityPreviewResponse,
    CommunityPublishRequest,
    CommunityResponseResult,
    CommunitySocialResult,
    CommunityStateResponse,
    CommunityUserSummary,
    CommunityVoteRequest,
    CommunityVoteResult,
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
from app.verification_service import (
    get_history_detail,
    list_history,
    verify_image,
    verify_text,
)

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

# Contract §5.2: image dimension limits
_IMAGE_MIN_DIM = 64
_IMAGE_MAX_DIM = 6_000
_IMAGE_MAX_PIXELS = 30_000_000


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
    community_connections = CommunityConnectionManager()

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

    @app.get("/community/{case_id}", tags=["Community"], response_class=HTMLResponse)
    async def community_share_page(case_id: UUID) -> HTMLResponse:
        deep_link = f"waspadai://community/{case_id}"
        return HTMLResponse(
            "<!doctype html><html lang='id'><head><meta charset='utf-8'>"
            "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            "<title>Kasus Komunitas WaspadAI</title></head><body>"
            "<h1>Kasus Komunitas WaspadAI</h1>"
            f"<p><a href='{deep_link}'>Buka kasus di aplikasi WaspadAI</a></p>"
            f"<script>location.href='{deep_link}'</script></body></html>",
            headers={"Cache-Control": "public, max-age=300"},
        )

    @app.websocket("/api/v1/community/ws")
    async def community_websocket(websocket: WebSocket, access_token: str = Query(...)) -> None:
        try:
            await authenticate_access_token(
                app.state.settings,
                app.state.auth_client,
                access_token,
            )
        except HTTPException:
            await websocket.close(code=4401)
            return
        await community_connections.connect(websocket)
        try:
            while True:
                await websocket.receive_text()
        except WebSocketDisconnect:
            await community_connections.disconnect(websocket)

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
        if not _matches_image_signature(image_bytes, image.content_type):
            raise ProductAPIError(
                415, "UNSUPPORTED_MEDIA_TYPE", "Isi file tidak cocok dengan format gambar."
            )
        # GAP-7: validate pixel dimensions per contract §5.2
        _validate_image_dimensions(image_bytes, image.content_type)
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

    @app.get("/api/v1/community", tags=["Community"], response_model=CommunityPage)
    async def list_community_endpoint(
        response: Response,
        limit: int = Query(default=20, ge=1, le=100),
        cursor: str | None = Query(default=None, max_length=2048),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityPage:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        response.headers["Cache-Control"] = "no-store"
        return await list_community(pool, app.state.settings, user.id, limit, cursor)

    @app.get(
        "/api/v1/community/bootstrap",
        tags=["Community"],
        response_model=CommunityBootstrap,
    )
    async def get_community_bootstrap_endpoint(
        response: Response,
        limit: int = Query(default=20, ge=1, le=100),
        cursor: str | None = Query(default=None, max_length=2048),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityBootstrap:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        response.headers["Cache-Control"] = "no-store"
        return await get_community_bootstrap(pool, app.state.settings, user.id, limit, cursor)

    @app.get(
        "/api/v1/community/me/summary",
        tags=["Community"],
        response_model=CommunityUserSummary,
    )
    async def get_community_user_summary_endpoint(
        response: Response,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityUserSummary:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        response.headers["Cache-Control"] = "no-store"
        return await get_community_user_summary(pool, app.state.settings, user.id)

    @app.get(
        "/api/v1/community/{case_id}",
        tags=["Community"],
        response_model=CommunityDetail,
    )
    async def get_community_detail_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityDetail:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await get_community_detail(pool, app.state.settings, user.id, case_id)

    @app.get("/api/v1/community/{case_id}/image", tags=["Community"])
    async def get_community_image_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> Response:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        content, content_type = await get_community_image(
            pool,
            app.state.settings,
            user.id,
            case_id,
            app.state.http_client,
        )
        return Response(
            content=content,
            media_type=content_type,
            headers={"Cache-Control": "private, max-age=60"},
        )

    @app.get("/api/v1/community/{case_id}/media/{media_id}", tags=["Community"])
    async def get_community_media_endpoint(
        case_id: UUID,
        media_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> Response:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        content, content_type = await get_community_media(
            pool, app.state.settings, user.id, case_id, media_id, app.state.http_client
        )
        return Response(
            content=content,
            media_type=content_type,
            headers={"Cache-Control": "private, max-age=86400, immutable"},
        )

    @app.get(
        "/api/v1/history/{case_id}/community-preview/{preview_id}/media/{media_id}",
        tags=["Community"],
    )
    async def get_community_preview_media_endpoint(
        case_id: UUID,
        preview_id: UUID,
        media_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> Response:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        content, content_type = await get_community_preview_media(
            pool, app.state.settings, user.id, case_id, preview_id, media_id, app.state.http_client
        )
        return Response(
            content=content,
            media_type=content_type,
            headers={"Cache-Control": "private, max-age=60"},
        )

    @app.post("/api/v1/community/{case_id}/like", tags=["Community"], response_model=CommunitySocialResult)
    async def like_community_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunitySocialResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        result = await like_community(pool, app.state.settings, user.id, case_id)
        await community_connections.broadcast(_social_event("community.like.updated", result))
        return result

    @app.delete("/api/v1/community/{case_id}/like", tags=["Community"], response_model=CommunitySocialResult)
    async def unlike_community_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunitySocialResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        result = await unlike_community(pool, app.state.settings, user.id, case_id)
        await community_connections.broadcast(_social_event("community.like.updated", result))
        return result

    @app.post("/api/v1/community/{case_id}/seen", tags=["Community"], response_model=CommunitySocialResult)
    async def record_community_view_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunitySocialResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        result = await record_community_view(pool, app.state.settings, user.id, case_id)
        await community_connections.broadcast(_social_event("community.view.updated", result))
        return result

    @app.post("/api/v1/community/{case_id}/share", tags=["Community"], response_model=CommunitySocialResult)
    async def record_community_share_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunitySocialResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
        result = await record_community_share(pool, app.state.settings, user.id, case_id)
        await community_connections.broadcast(_social_event("community.share.updated", result))
        return result

    @app.get("/api/v1/community/{case_id}/responses/{response_user_id}/image", tags=["Community"])
    async def get_community_response_image_endpoint(
        case_id: UUID,
        response_user_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> Response:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        content, content_type = await get_community_response_image(
            pool,
            app.state.settings,
            user.id,
            case_id,
            response_user_id,
            app.state.http_client,
        )
        return Response(
            content=content,
            media_type=content_type,
            headers={"Cache-Control": "private, max-age=60"},
        )

    @app.post(
        "/api/v1/history/{case_id}/community-preview",
        tags=["Community"],
        response_model=CommunityPreviewResponse,
    )
    async def create_community_preview_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityPreviewResponse:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await create_community_preview(pool, app.state.settings, user.id, case_id)

    @app.post(
        "/api/v1/history/{case_id}/community",
        tags=["Community"],
        response_model=CommunityStateResponse,
    )
    async def publish_community_case_endpoint(
        case_id: UUID,
        payload: CommunityPublishRequest,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityStateResponse:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        result = await publish_community_case(pool, app.state.settings, user.id, case_id, payload)
        await community_connections.broadcast(
            {"type": "community.created", "community_id": str(case_id), "payload": {}}
        )
        return result

    @app.delete(
        "/api/v1/history/{case_id}/community",
        tags=["Community"],
        response_model=CommunityStateResponse,
    )
    async def withdraw_community_case_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityStateResponse:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await withdraw_community_case(pool, app.state.settings, user.id, case_id)

    @app.post(
        "/api/v1/community/{case_id}/vote",
        tags=["Community"],
        response_model=CommunityVoteResult,
    )
    async def cast_community_vote_endpoint(
        case_id: UUID,
        payload: CommunityVoteRequest,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityVoteResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await cast_community_vote(pool, app.state.settings, user.id, case_id, payload)

    @app.delete(
        "/api/v1/community/{case_id}/vote",
        tags=["Community"],
        response_model=CommunityVoteResult,
    )
    async def remove_community_vote_endpoint(
        case_id: UUID,
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityVoteResult:
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        return await remove_community_vote(pool, app.state.settings, user.id, case_id)

    @app.post(
        "/api/v1/community/{case_id}/response",
        tags=["Community"],
        response_model=CommunityResponseResult,
    )
    async def submit_community_response_endpoint(
        case_id: UUID,
        vote: str = Form(...),
        reasoning: str = Form(..., min_length=10, max_length=5000),
        evidence: UploadFile | None = File(default=None),
        user: AuthenticatedUser = Depends(get_current_user),
    ) -> CommunityResponseResult:
        evidence_bytes: bytes | None = None
        evidence_content_type: str | None = None
        if evidence is not None:
            evidence_content_type = evidence.content_type
            if evidence_content_type not in {"image/jpeg", "image/png", "image/webp"}:
                raise ProductAPIError(
                    415, "UNSUPPORTED_MEDIA_TYPE", "Bukti tanggapan harus berupa JPG, PNG, atau WEBP."
                )
            evidence_bytes = await evidence.read(app.state.settings.max_image_bytes + 1)
            if len(evidence_bytes) > app.state.settings.max_image_bytes:
                raise ProductAPIError(
                    413, "PAYLOAD_TOO_LARGE", "Ukuran gambar bukti melebihi batas yang diizinkan."
                )
            if not evidence_bytes or not _matches_image_signature(evidence_bytes, evidence_content_type):
                raise ProductAPIError(
                    415, "UNSUPPORTED_MEDIA_TYPE", "Isi file tidak cocok dengan format gambar."
                )
            _validate_image_dimensions(evidence_bytes, evidence_content_type)
        pool = app.state.db_pool
        if pool is None:
            raise ProductAPIError(
                503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True
            )
        result = await submit_community_response(
            pool,
            app.state.settings,
            user.id,
            case_id,
            vote,
            reasoning,
            evidence_bytes,
            evidence_content_type,
            app.state.http_client,
        )
        event = {
            "type": "community.response.created",
            "community_id": str(case_id),
            "payload": {
                "hoaks_count": result.counts.HOAKS,
                "waspada_count": result.counts.WASPADA,
                "valid_count": result.counts.VALID,
                "response": result.response.model_dump(mode="json"),
            },
        }
        await community_connections.broadcast(event)
        await community_connections.broadcast(
            {**event, "type": "community.poll.updated", "payload": {k: v for k, v in event["payload"].items() if k != "response"}}
        )
        return result

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


def _social_event(event_type: str, result: CommunitySocialResult) -> dict[str, object]:
    return {
        "type": event_type,
        "community_id": str(result.case_id),
        "payload": {
            "like_count": result.like_count,
            "view_count": result.view_count,
            "comment_count": result.comment_count,
            "share_count": result.share_count,
        },
    }


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


def _matches_image_signature(image_bytes: bytes, content_type: str | None) -> bool:
    if content_type == "image/jpeg":
        return image_bytes.startswith(b"\xff\xd8\xff")
    if content_type == "image/png":
        return image_bytes.startswith(b"\x89PNG\r\n\x1a\n")
    if content_type == "image/webp":
        return (
            len(image_bytes) >= 12 and image_bytes[:4] == b"RIFF" and image_bytes[8:12] == b"WEBP"
        )
    return False


def _parse_image_dimensions(image_bytes: bytes, content_type: str | None) -> tuple[int, int] | None:
    """Parse (width, height) from raw bytes without external libraries.

    Returns None if the format cannot be parsed safely.
    Supports JPEG (SOF0/SOF2 markers), PNG (IHDR), and WEBP (VP8/VP8L/VP8X chunks).
    """
    try:
        if content_type == "image/png":
            # PNG IHDR: bytes 16-23 are width (big-endian u32) and height (big-endian u32)
            if len(image_bytes) < 24:
                return None
            width, height = struct.unpack(">II", image_bytes[16:24])
            return width, height

        if content_type == "image/jpeg":
            # Scan for SOF0 (0xFFC0) or SOF2 (0xFFC2) markers
            i = 2  # skip initial 0xFFD8
            while i + 3 < len(image_bytes):
                if image_bytes[i] != 0xFF:
                    break
                marker = image_bytes[i + 1]
                if marker in (0xC0, 0xC2):
                    # SOF: 1 byte precision, 2 bytes height, 2 bytes width
                    if i + 9 < len(image_bytes):
                        height, width = struct.unpack(">HH", image_bytes[i + 5 : i + 9])
                        return width, height
                    break
                # Advance past this segment
                if i + 3 >= len(image_bytes):
                    break
                seg_len = struct.unpack(">H", image_bytes[i + 2 : i + 4])[0]
                i += 2 + seg_len
            return None

        if content_type == "image/webp":
            # WEBP: check VP8L (lossless) or VP8X (extended) or VP8 (lossy)
            if len(image_bytes) < 30:
                return None
            chunk_id = image_bytes[12:16]
            if chunk_id == b"VP8L":
                # Lossless: 1 bit unused + 14 bits width-1 + 14 bits height-1
                if len(image_bytes) < 25:
                    return None
                bits = struct.unpack("<I", image_bytes[21:25])[0]
                width = (bits & 0x3FFF) + 1
                height = ((bits >> 14) & 0x3FFF) + 1
                return width, height
            if chunk_id == b"VP8X":
                # Extended: canvas width-1 (3 bytes LE) at offset 24, height-1 at offset 27
                if len(image_bytes) < 30:
                    return None
                width = struct.unpack("<I", image_bytes[24:27] + b"\x00")[0] + 1
                height = struct.unpack("<I", image_bytes[27:30] + b"\x00")[0] + 1
                return width, height
            if chunk_id == b"VP8 ":
                # Lossy: frame tag 3 bytes, start code 3 bytes, then 16-bit w/h with scaling
                if len(image_bytes) < 30:
                    return None
                raw_w, raw_h = struct.unpack("<HH", image_bytes[26:30])
                width = raw_w & 0x3FFF
                height = raw_h & 0x3FFF
                return width, height
    except Exception:
        pass
    return None


def _validate_image_dimensions(image_bytes: bytes, content_type: str | None) -> None:
    """Raise ProductAPIError if image dimensions are outside contract §5.2 bounds.

    Skips validation silently if dimensions cannot be parsed (fail-open for forward compat).
    """
    dims = _parse_image_dimensions(image_bytes, content_type)
    if dims is None:
        return  # cannot parse — let upstream AI service reject if truly invalid
    width, height = dims
    if width < _IMAGE_MIN_DIM or height < _IMAGE_MIN_DIM:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            f"Dimensi gambar terlalu kecil. Minimal {_IMAGE_MIN_DIM}×{_IMAGE_MIN_DIM} piksel.",
        )
    if width > _IMAGE_MAX_DIM or height > _IMAGE_MAX_DIM:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            f"Dimensi gambar terlalu besar. Maksimal {_IMAGE_MAX_DIM}×{_IMAGE_MAX_DIM} piksel.",
        )
    if width * height > _IMAGE_MAX_PIXELS:
        raise ProductAPIError(
            422,
            "VALIDATION_ERROR",
            "Jumlah piksel gambar melebihi batas yang diizinkan (30 juta piksel).",
        )
