from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.auth import AuthenticatedUser, get_current_user
from app.errors import ProductAPIError
from app.repositories.postgres.home_repository import PostgresHomeRepository
from app.schemas.home import HomeResponse
from app.services.home_service import get_home_dashboard

router = APIRouter(prefix="/api/v1/home", tags=["Home"])


@router.get("", response_model=HomeResponse)
async def get_home_endpoint(
    request: Request,
    user: AuthenticatedUser = Depends(get_current_user),
) -> HomeResponse:
    pool = request.app.state.db_pool
    if pool is None:
        raise ProductAPIError(
            503,
            "PERSISTENCE_UNAVAILABLE",
            "Database belum dikonfigurasi.",
            True,
        )
    repository = PostgresHomeRepository(
        pool=pool,
        statement_timeout_seconds=request.app.state.settings.db_statement_timeout_seconds,
    )
    return await get_home_dashboard(repository, user.id, user.email)
