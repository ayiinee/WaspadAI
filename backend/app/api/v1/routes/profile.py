from __future__ import annotations

from fastapi import APIRouter, Depends, File, Query, Request, Response, UploadFile, status

from app.auth import AuthenticatedUser, get_current_user
from app.errors import ProductAPIError
from app.repositories.postgres.profile_repository import PostgresProfileRepository
from app.schemas.profile import (
    ProfileCommunityActivityPage,
    ProfileLearningResponse,
    ProfileOverviewResponse,
    ProfilePublicationPage,
    ProfileResponse,
    ProfileUpdateRequest,
    ProfileVerificationPage,
)
from app.services.profile_service import (
    MAX_AVATAR_BYTES,
    delete_avatar,
    fetch_avatar,
    get_overview,
    get_profile,
    list_community_activity,
    list_learning,
    list_publications,
    list_verifications,
    update_profile,
    upload_avatar,
)

router = APIRouter(prefix="/api/v1/me/profile", tags=["Profile"])


def _repository(request: Request) -> PostgresProfileRepository:
    pool = request.app.state.db_pool
    if pool is None:
        raise ProductAPIError(503, "PERSISTENCE_UNAVAILABLE", "Database belum dikonfigurasi.", True)
    return PostgresProfileRepository(pool, request.app.state.settings.db_statement_timeout_seconds)


def _avatar_url(request: Request) -> str:
    return str(request.url_for("get_profile_avatar_endpoint"))


@router.get("", response_model=ProfileResponse)
async def get_profile_endpoint(
    request: Request, user: AuthenticatedUser = Depends(get_current_user)
) -> ProfileResponse:
    return await get_profile(_repository(request), user.id, user.email, _avatar_url(request))


@router.patch("", response_model=ProfileResponse)
async def update_profile_endpoint(
    payload: ProfileUpdateRequest,
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
) -> ProfileResponse:
    return await update_profile(
        _repository(request), user.id, user.email, payload, _avatar_url(request)
    )


@router.get("/overview", response_model=ProfileOverviewResponse)
async def get_profile_overview_endpoint(
    request: Request, user: AuthenticatedUser = Depends(get_current_user)
) -> ProfileOverviewResponse:
    return await get_overview(_repository(request), user.id)


@router.get("/verifications", response_model=ProfileVerificationPage)
async def get_profile_verifications_endpoint(
    request: Request,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    status_filter: str | None = Query(None, alias="status"),
    user: AuthenticatedUser = Depends(get_current_user),
) -> ProfileVerificationPage:
    return await list_verifications(_repository(request), user.id, limit, offset, status_filter)


@router.get("/publications", response_model=ProfilePublicationPage)
async def get_profile_publications_endpoint(
    request: Request,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    status_filter: str | None = Query(None, alias="status"),
    user: AuthenticatedUser = Depends(get_current_user),
) -> ProfilePublicationPage:
    return await list_publications(_repository(request), user.id, limit, offset, status_filter)


@router.get("/community-activity", response_model=ProfileCommunityActivityPage)
async def get_profile_community_activity_endpoint(
    request: Request,
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    user: AuthenticatedUser = Depends(get_current_user),
) -> ProfileCommunityActivityPage:
    return await list_community_activity(_repository(request), user.id, limit, offset)


@router.get("/learning", response_model=ProfileLearningResponse)
async def get_profile_learning_endpoint(
    request: Request, user: AuthenticatedUser = Depends(get_current_user)
) -> ProfileLearningResponse:
    return await list_learning(_repository(request), user.id)


@router.put("/avatar", response_model=ProfileResponse)
async def put_profile_avatar_endpoint(
    request: Request,
    avatar: UploadFile = File(...),
    user: AuthenticatedUser = Depends(get_current_user),
) -> ProfileResponse:
    avatar_bytes = await avatar.read(MAX_AVATAR_BYTES + 1)
    await upload_avatar(
        _repository(request),
        request.app.state.http_client,
        request.app.state.settings,
        user.id,
        avatar_bytes,
        avatar.content_type or "",
    )
    return await get_profile(_repository(request), user.id, user.email, _avatar_url(request))


@router.get("/avatar", name="get_profile_avatar_endpoint")
async def get_profile_avatar_endpoint(
    request: Request, user: AuthenticatedUser = Depends(get_current_user)
) -> Response:
    content, content_type = await fetch_avatar(
        _repository(request), request.app.state.http_client, request.app.state.settings, user.id
    )
    return Response(
        content=content, media_type=content_type, headers={"Cache-Control": "private, max-age=300"}
    )


@router.delete("/avatar", status_code=status.HTTP_204_NO_CONTENT)
async def delete_profile_avatar_endpoint(
    request: Request, user: AuthenticatedUser = Depends(get_current_user)
) -> None:
    await delete_avatar(
        _repository(request), request.app.state.http_client, request.app.state.settings, user.id
    )
