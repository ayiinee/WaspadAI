from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import Annotated
from uuid import UUID

import httpx
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Settings

logger = logging.getLogger(__name__)

bearer_scheme = HTTPBearer(auto_error=False)
_AUTH_REQUEST_ATTEMPTS = 2
_AUTH_RETRY_DELAY_SECONDS = 0.2


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
    return await authenticate_access_token(
        settings,
        request.app.state.auth_client,
        credentials.credentials,
    )


async def authenticate_access_token(
    settings: Settings,
    client: httpx.AsyncClient,
    access_token: str,
) -> AuthenticatedUser:
    """Validate an access token for HTTP and WebSocket transports."""

    if settings.supabase_auth_mode != "get_user" or not settings.supabase_auth_is_configured:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="auth unavailable",
        )
    auth_url = f"{settings.supabase_url.rstrip('/')}/auth/v1/user"
    headers = {
        "apikey": settings.supabase_publishable_key.get_secret_value(),
        "authorization": f"Bearer {access_token}",
    }
    for attempt in range(_AUTH_REQUEST_ATTEMPTS):
        try:
            response = await client.get(auth_url, headers=headers)
            break
        except httpx.RequestError as error:
            is_last_attempt = attempt == _AUTH_REQUEST_ATTEMPTS - 1
            logger.warning(
                "Supabase auth request failed (attempt %d/%d, url=%s, error_type=%s): %s",
                attempt + 1,
                _AUTH_REQUEST_ATTEMPTS,
                auth_url,
                type(error).__name__,
                error,
                exc_info=is_last_attempt,
            )
            if is_last_attempt:
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="auth unavailable",
                ) from error
            await asyncio.sleep(_AUTH_RETRY_DELAY_SECONDS)
    if response.status_code in {400, 401, 403}:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid access token")
    if response.is_error:
        logger.warning(f"Supabase auth returned HTTP {response.status_code}: {response.text}")
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
