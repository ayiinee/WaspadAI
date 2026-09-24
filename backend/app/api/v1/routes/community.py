from __future__ import annotations

from uuid import UUID

from fastapi import (
    APIRouter,
    Depends,
    File,
    Form,
    HTTPException,
    Query,
    Request,
    Response,
    UploadFile,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.responses import HTMLResponse
from psycopg_pool import AsyncConnectionPool

from app.api.dependencies.database import get_database_pool
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
    record_community_refresh,
    record_community_share,
    record_community_view,
    remove_community_vote,
    submit_community_response,
    unlike_community,
    update_community_case,
    withdraw_community_case,
)
from app.errors import ProductAPIError
from app.privacy.image_validation import (
    matches_image_signature,
    validate_image_dimensions,
)
from app.schemas.community import (
    CommunityBootstrap,
    CommunityDetail,
    CommunityItem,
    CommunityPage,
    CommunityPreviewResponse,
    CommunityPublishRequest,
    CommunityResponseResult,
    CommunitySocialResult,
    CommunityStateResponse,
    CommunityUpdateRequest,
    CommunityUserSummary,
    CommunityVoteRequest,
    CommunityVoteResult,
)

router = APIRouter(tags=["Community"])


@router.get("/community/{case_id}", response_class=HTMLResponse)
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


@router.websocket("/api/v1/community/ws")
async def community_websocket(
    websocket: WebSocket,
    access_token: str = Query(...),
) -> None:
    try:
        user = await authenticate_access_token(
            websocket.app.state.settings,
            websocket.app.state.auth_client,
            access_token,
        )
    except HTTPException:
        await websocket.close(code=4401)
        return
    connections = _connections(websocket)
    await connections.connect(websocket, user.id)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        await connections.disconnect(websocket)


@router.get("/api/v1/community", response_model=CommunityPage)
async def list_community_endpoint(
    request: Request,
    response: Response,
    limit: int = Query(default=20, ge=1, le=100),
    cursor: str | None = Query(default=None, max_length=2048),
    refresh: bool = Query(default=False),
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityPage:
    response.headers["Cache-Control"] = "no-store"
    result = await list_community(pool, request.app.state.settings, user.id, limit, cursor)
    if refresh:
        await record_community_refresh(
            pool,
            request.app.state.settings,
            user.id,
            UUID(_request_id(request)),
            len(result.items),
        )
    return result


@router.get("/api/v1/community/bootstrap", response_model=CommunityBootstrap)
async def get_community_bootstrap_endpoint(
    request: Request,
    response: Response,
    limit: int = Query(default=20, ge=1, le=100),
    cursor: str | None = Query(default=None, max_length=2048),
    refresh: bool = Query(default=False),
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityBootstrap:
    response.headers["Cache-Control"] = "no-store"
    result = await get_community_bootstrap(
        pool,
        request.app.state.settings,
        user.id,
        limit,
        cursor,
    )
    if refresh:
        await record_community_refresh(
            pool,
            request.app.state.settings,
            user.id,
            UUID(_request_id(request)),
            len(result.feed.items),
        )
    return result


@router.get("/api/v1/community/me/summary", response_model=CommunityUserSummary)
async def get_community_user_summary_endpoint(
    request: Request,
    response: Response,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityUserSummary:
    response.headers["Cache-Control"] = "no-store"
    return await get_community_user_summary(
        pool,
        request.app.state.settings,
        user.id,
    )


@router.get("/api/v1/community/{case_id}", response_model=CommunityDetail)
async def get_community_detail_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityDetail:
    return await get_community_detail(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )


@router.get("/api/v1/community/{case_id}/image")
async def get_community_image_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> Response:
    content, content_type = await get_community_image(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        request.app.state.http_client,
    )
    return _image_response(content, content_type, max_age=60)


@router.get("/api/v1/community/{case_id}/media/{media_id}")
async def get_community_media_endpoint(
    request: Request,
    case_id: UUID,
    media_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> Response:
    content, content_type = await get_community_media(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        media_id,
        request.app.state.http_client,
    )
    return _image_response(content, content_type, max_age=86400, immutable=True)


@router.get("/api/v1/history/{case_id}/community-preview/{preview_id}/media/{media_id}")
async def get_community_preview_media_endpoint(
    request: Request,
    case_id: UUID,
    preview_id: UUID,
    media_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> Response:
    content, content_type = await get_community_preview_media(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        preview_id,
        media_id,
        request.app.state.http_client,
    )
    return _image_response(content, content_type, max_age=60)


@router.post(
    "/api/v1/community/{case_id}/like",
    response_model=CommunitySocialResult,
)
async def like_community_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunitySocialResult:
    result = await like_community(pool, request.app.state.settings, user.id, case_id)
    await _connections(request).broadcast(_social_event("community.like.updated", result))
    return result


@router.delete(
    "/api/v1/community/{case_id}/like",
    response_model=CommunitySocialResult,
)
async def unlike_community_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunitySocialResult:
    result = await unlike_community(pool, request.app.state.settings, user.id, case_id)
    await _connections(request).broadcast(_social_event("community.like.updated", result))
    return result


@router.patch("/api/v1/community/{case_id}", response_model=CommunityItem)
async def update_community_case_endpoint(
    request: Request,
    case_id: UUID,
    payload: CommunityUpdateRequest,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityItem:
    result = await update_community_case(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        payload,
    )
    realtime_post = result.model_copy(
        update={"creator": result.creator.model_copy(update={"is_current_user": False})}
    )
    await _connections(request).broadcast(
        {
            "type": "community.updated",
            "community_id": str(result.id),
            "payload": {"post": realtime_post.model_dump(mode="json")},
        },
        exclude_user_id=user.id,
    )
    return result


@router.post(
    "/api/v1/community/{case_id}/seen",
    response_model=CommunitySocialResult,
)
async def record_community_view_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunitySocialResult:
    result = await record_community_view(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )
    await _connections(request).broadcast(_social_event("community.view.updated", result))
    return result


@router.post(
    "/api/v1/community/{case_id}/share",
    response_model=CommunitySocialResult,
)
async def record_community_share_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunitySocialResult:
    result = await record_community_share(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )
    await _connections(request).broadcast(_social_event("community.share.updated", result))
    return result


@router.get("/api/v1/community/{case_id}/responses/{response_user_id}/image")
async def get_community_response_image_endpoint(
    request: Request,
    case_id: UUID,
    response_user_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> Response:
    content, content_type = await get_community_response_image(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        response_user_id,
        request.app.state.http_client,
    )
    return _image_response(content, content_type, max_age=60)


@router.post(
    "/api/v1/history/{case_id}/community-preview",
    response_model=CommunityPreviewResponse,
)
async def create_community_preview_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityPreviewResponse:
    return await create_community_preview(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )


@router.post("/api/v1/history/{case_id}/community", response_model=CommunityItem)
async def publish_community_case_endpoint(
    request: Request,
    case_id: UUID,
    payload: CommunityPublishRequest,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityItem:
    result = await publish_community_case(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        payload,
    )
    realtime_post = result.model_copy(
        update={"creator": result.creator.model_copy(update={"is_current_user": False})}
    )
    await _connections(request).broadcast(
        {
            "type": "community.created",
            "community_id": str(result.id),
            "payload": {"post": realtime_post.model_dump(mode="json")},
        },
        exclude_user_id=user.id,
    )
    return result


@router.delete(
    "/api/v1/history/{case_id}/community",
    response_model=CommunityStateResponse,
)
async def withdraw_community_case_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityStateResponse:
    result = await withdraw_community_case(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )
    await _connections(request).broadcast(
        {
            "type": "community.deleted",
            "community_id": str(case_id),
            "payload": {},
        },
        exclude_user_id=user.id,
    )
    return result


@router.post(
    "/api/v1/community/{case_id}/vote",
    response_model=CommunityVoteResult,
)
async def cast_community_vote_endpoint(
    request: Request,
    case_id: UUID,
    payload: CommunityVoteRequest,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityVoteResult:
    result = await cast_community_vote(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        payload,
    )
    await _connections(request).broadcast(_poll_event(result))
    return result


@router.delete(
    "/api/v1/community/{case_id}/vote",
    response_model=CommunityVoteResult,
)
async def remove_community_vote_endpoint(
    request: Request,
    case_id: UUID,
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityVoteResult:
    result = await remove_community_vote(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
    )
    await _connections(request).broadcast(_poll_event(result))
    return result


@router.post(
    "/api/v1/community/{case_id}/response",
    response_model=CommunityResponseResult,
)
async def submit_community_response_endpoint(
    request: Request,
    case_id: UUID,
    vote: str = Form(...),
    reasoning: str = Form(..., min_length=10, max_length=5000),
    evidence: UploadFile | None = File(default=None),
    user: AuthenticatedUser = Depends(get_current_user),
    pool: AsyncConnectionPool = Depends(get_database_pool),
) -> CommunityResponseResult:
    evidence_bytes, evidence_content_type = await _read_evidence(request, evidence)
    result = await submit_community_response(
        pool,
        request.app.state.settings,
        user.id,
        case_id,
        vote,
        reasoning,
        evidence_bytes,
        evidence_content_type,
        request.app.state.http_client,
    )
    event = {
        "type": "community.response.created",
        "community_id": str(result.community_id),
        "payload": {
            "hoaks_count": result.counts.HOAKS,
            "waspada_count": result.counts.WASPADA,
            "valid_count": result.counts.VALID,
            "response": result.response.model_dump(mode="json"),
        },
    }
    connections = _connections(request)
    await connections.broadcast(event)
    await connections.broadcast({**event, "type": "community.comment.created"})
    await connections.broadcast(
        {
            **event,
            "type": "community.poll.updated",
            "payload": {key: value for key, value in event["payload"].items() if key != "response"},
        }
    )
    return result


async def _read_evidence(
    request: Request,
    evidence: UploadFile | None,
) -> tuple[bytes | None, str | None]:
    if evidence is None:
        return None, None
    content_type = evidence.content_type
    if content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise ProductAPIError(
            415,
            "UNSUPPORTED_MEDIA_TYPE",
            "Bukti tanggapan harus berupa JPG, PNG, atau WEBP.",
        )
    evidence_bytes = await evidence.read(request.app.state.settings.max_image_bytes + 1)
    if len(evidence_bytes) > request.app.state.settings.max_image_bytes:
        raise ProductAPIError(
            413,
            "PAYLOAD_TOO_LARGE",
            "Ukuran gambar bukti melebihi batas yang diizinkan.",
        )
    if not evidence_bytes or not matches_image_signature(evidence_bytes, content_type):
        raise ProductAPIError(
            415,
            "UNSUPPORTED_MEDIA_TYPE",
            "Isi file tidak cocok dengan format gambar.",
        )
    validate_image_dimensions(evidence_bytes, content_type)
    return evidence_bytes, content_type


def _connections(request: Request | WebSocket) -> CommunityConnectionManager:
    return request.app.state.community_connections


def _request_id(request: Request) -> str:
    return request.state.request_id


def _image_response(
    content: bytes,
    content_type: str,
    max_age: int,
    immutable: bool = False,
) -> Response:
    cache_control = f"private, max-age={max_age}"
    if immutable:
        cache_control += ", immutable"
    return Response(
        content=content,
        media_type=content_type,
        headers={"Cache-Control": cache_control},
    )


def _social_event(
    event_type: str,
    result: CommunitySocialResult,
) -> dict[str, object]:
    return {
        "type": event_type,
        "community_id": str(result.community_id),
        "payload": {
            "like_count": result.like_count,
            "view_count": result.view_count,
            "comment_count": result.comment_count,
            "share_count": result.share_count,
        },
    }


def _poll_event(result: CommunityVoteResult) -> dict[str, object]:
    return {
        "type": "community.poll.updated",
        "community_id": str(result.community_id),
        "payload": {
            "hoaks_count": result.counts.HOAKS,
            "waspada_count": result.counts.WASPADA,
            "valid_count": result.counts.VALID,
        },
    }
