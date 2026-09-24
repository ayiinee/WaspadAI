from __future__ import annotations

from uuid import UUID

import anyio
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from starlette.requests import Request

from app.auth import get_current_user
from app.config import Settings

TEST_USER_ID = "c80567d5-0529-4d6d-a8f0-b06fcc1dd175"


def make_request(client: httpx.AsyncClient) -> Request:
    app = FastAPI()
    app.state.settings = Settings(
        supabase_url="https://example.supabase.co",
        supabase_publishable_key="publishable-test-key",
    )
    app.state.auth_client = client
    return Request({"type": "http", "app": app})


def test_get_user_sends_bearer_only_to_supabase_auth() -> None:
    async def check() -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            assert str(request.url) == "https://example.supabase.co/auth/v1/user"
            assert request.headers["authorization"] == "Bearer sample-token"
            assert request.headers["apikey"] == "publishable-test-key"
            return httpx.Response(200, json={"id": TEST_USER_ID, "email": "user@example.invalid"})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            principal = await get_current_user(
                make_request(client),
                HTTPAuthorizationCredentials(scheme="Bearer", credentials="sample-token"),
            )
        assert principal.id == UUID(TEST_USER_ID)

    anyio.run(check)


def test_invalid_token_is_401_and_auth_network_failure_is_503() -> None:
    async def check() -> None:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(lambda _: httpx.Response(401))
        ) as client:
            try:
                await get_current_user(
                    make_request(client),
                    HTTPAuthorizationCredentials(scheme="Bearer", credentials="invalid"),
                )
            except HTTPException as error:
                assert error.status_code == 401
            else:
                raise AssertionError("invalid token was accepted")

        def disconnected(request: httpx.Request) -> httpx.Response:
            raise httpx.ConnectError("test disconnect", request=request)

        async with httpx.AsyncClient(transport=httpx.MockTransport(disconnected)) as client:
            try:
                await get_current_user(
                    make_request(client),
                    HTTPAuthorizationCredentials(scheme="Bearer", credentials="unknown"),
                )
            except HTTPException as error:
                assert error.status_code == 503
            else:
                raise AssertionError("auth outage was not fail-closed")

    anyio.run(check)


def test_auth_retries_one_transient_network_failure() -> None:
    async def check() -> None:
        attempts = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise httpx.ConnectError("temporary disconnect", request=request)
            return httpx.Response(200, json={"id": TEST_USER_ID, "email": None})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            principal = await get_current_user(
                make_request(client),
                HTTPAuthorizationCredentials(scheme="Bearer", credentials="sample-token"),
            )

        assert attempts == 2
        assert principal.id == UUID(TEST_USER_ID)

    anyio.run(check)
