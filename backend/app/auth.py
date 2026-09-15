from __future__ import annotations

from dataclasses import dataclass
from typing import Annotated
from uuid import UUID

import httpx
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Settings

bearer_scheme = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class AuthenticatedUser:
    id: UUID
    email: str | None


async def get_current_user(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
) -> AuthenticatedUser:
    """Validate the access token with Supabase Auth in the MVP get_user mode."""

    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing bearer token")
    settings: Settings = request.app.state.settings
    if settings.supabase_auth_mode != "get_user" or not settings.supabase_auth_is_configured:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="auth unavailable",
        )
    client: httpx.AsyncClient = request.app.state.auth_client
    try:
        response = await client.get(
            f"{settings.supabase_url.rstrip('/')}/auth/v1/user",
            headers={
                "apikey": settings.supabase_publishable_key.get_secret_value(),
                "authorization": f"Bearer {credentials.credentials}",
            },
        )
    except httpx.RequestError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="auth unavailable",
        ) from error
    if response.status_code in {400, 401, 403}:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid access token")
    if response.is_error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="auth unavailable",
        )
    try:
        payload = response.json()
        return AuthenticatedUser(id=UUID(payload["id"]), email=payload.get("email"))
    except (KeyError, TypeError, ValueError, httpx.DecodingError) as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="invalid auth response",
        ) from error
