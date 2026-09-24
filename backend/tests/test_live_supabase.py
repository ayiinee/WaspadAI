"""Opt-in hosted development acceptance tests; never run automatically on PRs.

Create two temporary Supabase Auth users *after* applying the initial migration.
Supply their UUIDs and the product_app DSN through the operator's secret manager.
All inserted test rows are rolled back. Delete the Auth users after the run.
"""

from __future__ import annotations

import asyncio
import json
import os
from collections.abc import Coroutine
from dataclasses import dataclass
from typing import Any
from uuid import UUID, uuid4

import psycopg
import pytest
from fastapi.testclient import TestClient
from psycopg.conninfo import conninfo_to_dict
from psycopg_pool import AsyncConnectionPool

from app.config import get_settings
from app.database import user_transaction
from app.main import create_app

pytestmark = pytest.mark.live_database


@dataclass(frozen=True)
class LiveConfig:
    database_url: str
    user_a: UUID
    user_b: UUID


@pytest.fixture(scope="module")
def live_config() -> LiveConfig:
    if os.getenv("WASPADAI_RUN_LIVE_DB_TESTS") != "1":
        pytest.skip("live Supabase tests require WASPADAI_RUN_LIVE_DB_TESTS=1")

    required = ("WASPADAI_DB_TEST_USER_A_ID", "WASPADAI_DB_TEST_USER_B_ID")
    missing = [name for name in required if not os.getenv(name)]
    if missing:
        pytest.fail("missing live test configuration: " + ", ".join(missing), pytrace=False)

    dsn = os.getenv("DATABASE_URL")
    if not dsn:
        configured_dsn = get_settings().database_url
        dsn = configured_dsn.get_secret_value() if configured_dsn is not None else None
    if not dsn:
        pytest.fail("missing live test configuration: DATABASE_URL or backend .env", pytrace=False)
    sslmode = conninfo_to_dict(dsn).get("sslmode")
    if sslmode not in {"require", "verify-ca", "verify-full"}:
        pytest.fail("DATABASE_URL must explicitly require TLS", pytrace=False)

    user_a = UUID(os.environ["WASPADAI_DB_TEST_USER_A_ID"])
    user_b = UUID(os.environ["WASPADAI_DB_TEST_USER_B_ID"])
    if user_a == user_b:
        pytest.fail("test user A and B must be distinct", pytrace=False)
    return LiveConfig(dsn, user_a, user_b)


def run_async(coroutine: Coroutine[Any, Any, None]) -> None:
    # Psycopg's async connection does not support Windows' default Proactor loop.
    with asyncio.Runner(loop_factory=asyncio.SelectorEventLoop) as runner:
        runner.run(coroutine)


async def open_single_connection_pool(dsn: str) -> AsyncConnectionPool:
    pool = AsyncConnectionPool(
        conninfo=dsn,
        min_size=1,
        max_size=1,
        open=False,
        kwargs={"autocommit": False},
        reset=_reset_connection,
    )
    await pool.open(wait=True)
    return pool


async def _reset_connection(connection: psycopg.AsyncConnection) -> None:
    await connection.execute("RESET ALL")
    if not connection.autocommit:
        await connection.rollback()


async def set_test_claim(connection: psycopg.AsyncConnection, user_id: UUID) -> None:
    # Match the Product backend's transaction-local claim mechanism.
    await connection.execute(
        """select set_config('request.jwt.claims', %s, true),
                  set_config('request.jwt.claim.sub', %s, true)""",
        (json.dumps({"sub": str(user_id), "role": "authenticated"}), str(user_id)),
    )


def test_signup_trigger_and_owner_visibility(live_config: LiveConfig) -> None:
    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            for owner, other in (
                (live_config.user_a, live_config.user_b),
                (live_config.user_b, live_config.user_a),
            ):
                async with user_transaction(pool, owner, 15) as connection:
                    profile = await connection.execute(
                        "select id from public.profiles where id = %s", (owner,)
                    )
                    assert await profile.fetchone() == (owner,)
                    role = await connection.execute(
                        "select role from public.user_roles where user_id = %s", (owner,)
                    )
                    assert await role.fetchone() == ("USER",)
                    hidden = await connection.execute(
                        "select id from public.profiles where id = %s", (other,)
                    )
                    assert await hidden.fetchone() is None
                    hidden_role = await connection.execute(
                        "select role from public.user_roles where user_id = %s", (other,)
                    )
                    assert await hidden_role.fetchone() is None
        finally:
            await pool.close()

    run_async(check())


def test_role_grants_and_forbidden_columns(live_config: LiveConfig) -> None:
    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            async with user_transaction(pool, live_config.user_a, 15) as connection:
                cursor = await connection.execute(
                    """select current_user, r.rolcanlogin, r.rolbypassrls,
                              r.rolsuper, r.rolcreaterole
                       from pg_roles r where r.rolname = current_user"""
                )
                assert await cursor.fetchone() == ("product_app", True, False, False, False)
                cursor = await connection.execute(
                    """select has_column_privilege(current_user, 'public.profiles',
                                'display_name', 'UPDATE'),
                              has_column_privilege(current_user, 'public.profiles',
                                'is_active', 'UPDATE'),
                              has_table_privilege(current_user, 'public.user_roles', 'INSERT')"""
                )
                assert await cursor.fetchone() == (True, False, False)
                with pytest.raises(psycopg.errors.InsufficientPrivilege):
                    async with connection.transaction():
                        await connection.execute(
                            "update public.profiles set is_active = false where id = %s",
                            (live_config.user_a,),
                        )
                with pytest.raises(psycopg.errors.InsufficientPrivilege):
                    async with connection.transaction():
                        await connection.execute(
                            "insert into public.user_roles (user_id, role) values (%s, 'ADMIN')",
                            (live_config.user_a,),
                        )
        finally:
            await pool.close()

    run_async(check())


def test_private_operation_rls_and_claim_reset(live_config: LiveConfig) -> None:
    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            operation_id = uuid4()
            async with pool.connection() as connection:
                async with connection.transaction(force_rollback=True):
                    await set_test_claim(connection, live_config.user_a)
                    await connection.execute(
                        """insert into private.request_operations
                           (id, user_id, route_key, idempotency_key, payload_hash,
                            state, persistence_state, expires_at)
                           values (%s, %s, 'live-test', %s, %s, 'PROCESSING',
                                   'NOT_REQUIRED', now() + interval '1 hour')""",
                        (operation_id, live_config.user_a, uuid4(), "a" * 64),
                    )
                    cursor = await connection.execute(
                        "select id from private.request_operations where id = %s", (operation_id,)
                    )
                    assert await cursor.fetchone() == (operation_id,)
                    await set_test_claim(connection, live_config.user_b)
                    cursor = await connection.execute(
                        "select id from private.request_operations where id = %s", (operation_id,)
                    )
                    assert await cursor.fetchone() is None
                    cursor = await connection.execute(
                        "update private.request_operations set state = 'FAILED' "
                        "where id = %s returning id",
                        (operation_id,),
                    )
                    assert await cursor.fetchone() is None

            # max_size=1 reuses the pooled client connection. The transaction-local
            # claim itself must not survive; auth.uid() is intentionally exercised
            # through RLS policies rather than invoked directly by Product code.
            async with pool.connection() as connection:
                cursor = await connection.execute(
                    "select current_setting('request.jwt.claim.sub', true)"
                )
                assert (await cursor.fetchone())[0] in (None, "")
                cursor = await connection.execute("select id from public.profiles")
                assert await cursor.fetchall() == []
            async with user_transaction(pool, live_config.user_b, 15) as connection:
                cursor = await connection.execute(
                    "select current_setting('request.jwt.claim.sub', true)"
                )
                assert await cursor.fetchone() == (str(live_config.user_b),)
            async with pool.connection() as connection:
                cursor = await connection.execute(
                    "select current_setting('request.jwt.claim.sub', true)"
                )
                assert (await cursor.fetchone())[0] in (None, "")
        finally:
            await pool.close()

    run_async(check())


def test_verification_history_rls_is_owner_scoped(live_config: LiveConfig) -> None:
    """The newer history tables must not weaken the original product_app RLS boundary."""

    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            case_id = uuid4()
            operation_id = uuid4()
            async with pool.connection() as connection:
                async with connection.transaction(force_rollback=True):
                    await set_test_claim(connection, live_config.user_a)
                    await connection.execute(
                        """insert into private.request_operations
                               (id, user_id, route_key, idempotency_key, payload_hash,
                                state, persistence_state, expires_at)
                           values (%s, %s, 'history-live-test', %s, %s, 'COMPLETED',
                                   'SAVED', now() + interval '1 hour')""",
                        (operation_id, live_config.user_a, uuid4(), "b" * 64),
                    )
                    await connection.execute(
                        """insert into public.verification_conversations
                               (id, user_id, title, latest_message_preview,
                                latest_message_role, last_verdict, next_turn_index,
                                retention_expires_at)
                           values (%s, %s, 'Fixture history', 'Fixture history',
                                   'ASSISTANT', 'UNVERIFIED', 2, now() + interval '1 day')""",
                        (case_id, live_config.user_a),
                    )
                    await connection.execute(
                        """insert into public.verification_cases
                               (id, user_id, operation_id, product_request_id,
                                conversation_id, turn_index, input_type,
                                input_source, input_hash, headline, verdict, risk_level,
                                requires_human_review, save_reason, community_state,
                                retention_expires_at)
                           values (%s, %s, %s, %s, %s, 1, 'TEXT', 'MANUAL', %s, 'Fixture history',
                                   'UNVERIFIED', 'UNKNOWN', true, 'UNVERIFIED', 'PRIVATE',
                                   now() + interval '1 day')""",
                        (case_id, live_config.user_a, operation_id, uuid4(), case_id, "c" * 64),
                    )
                    await connection.execute(
                        """insert into public.verification_results
                               (case_id, factual_status, source_authenticity, sender_identity,
                                channel_status, scam_risk, content_authenticity, result_json,
                                execution_mode)
                           values (%s, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
                                   'UNVERIFIED', 'UNKNOWN', 'NOT_APPLICABLE', %s, 'MOCK')""",
                        (case_id, json.dumps({"fixture": True})),
                    )
                    own_case = await connection.execute(
                        "select id from public.verification_cases where id = %s", (case_id,)
                    )
                    assert await own_case.fetchone() == (case_id,)
                    own_conversation = await connection.execute(
                        "select id from public.verification_conversations where id = %s",
                        (case_id,),
                    )
                    assert await own_conversation.fetchone() == (case_id,)

                    await set_test_claim(connection, live_config.user_b)
                    hidden_case = await connection.execute(
                        "select id from public.verification_cases where id = %s", (case_id,)
                    )
                    assert await hidden_case.fetchone() is None
                    hidden_result = await connection.execute(
                        "select case_id from public.verification_results where case_id = %s",
                        (case_id,),
                    )
                    assert await hidden_result.fetchone() is None
                    hidden_conversation = await connection.execute(
                        "select id from public.verification_conversations where id = %s",
                        (case_id,),
                    )
                    assert await hidden_conversation.fetchone() is None
        finally:
            await pool.close()

    run_async(check())


def test_withdrawn_community_post_is_hidden_and_consents_are_revoked(
    live_config: LiveConfig,
) -> None:
    """Exercise the publication visibility gates with the real product_app RLS role."""

    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            case_id = uuid4()
            operation_id = uuid4()
            publication_consent_id = uuid4()
            rag_consent_id = uuid4()
            post_id = uuid4()
            content_hash = "d" * 64
            async with pool.connection() as connection:
                async with connection.transaction(force_rollback=True):
                    await set_test_claim(connection, live_config.user_a)
                    await connection.execute(
                        """insert into private.request_operations
                               (id, user_id, route_key, idempotency_key, payload_hash,
                                state, persistence_state, expires_at)
                           values (%s, %s, 'community-live-test', %s, %s, 'COMPLETED',
                                   'SAVED', now() + interval '1 hour')""",
                        (operation_id, live_config.user_a, uuid4(), "e" * 64),
                    )
                    await connection.execute(
                        """insert into public.verification_conversations
                               (id, user_id, title, latest_message_preview,
                                latest_message_role, last_verdict, next_turn_index,
                                retention_expires_at)
                           values (%s, %s, 'Fixture community', 'Fixture community',
                                   'ASSISTANT', 'UNVERIFIED', 2, now() + interval '1 day')""",
                        (case_id, live_config.user_a),
                    )
                    await connection.execute(
                        """insert into public.verification_cases
                               (id, user_id, operation_id, product_request_id,
                                conversation_id, turn_index, input_type,
                                input_source, sanitized_text, input_hash, headline, verdict,
                                risk_level, requires_human_review, save_reason, community_state,
                                retention_expires_at)
                           values (%s, %s, %s, %s, %s, 1, 'TEXT', 'MANUAL', 'Konten sintetis aman',
                                   %s, 'Fixture community', 'UNVERIFIED', 'UNKNOWN', true,
                                   'UNVERIFIED', 'PUBLISHED_UNVERIFIED',
                                   now() + interval '1 day')""",
                        (case_id, live_config.user_a, operation_id, uuid4(), case_id, content_hash),
                    )
                    await connection.execute(
                        """insert into public.verification_results
                               (case_id, factual_status, source_authenticity, sender_identity,
                                channel_status, scam_risk, content_authenticity, result_json,
                                execution_mode)
                           values (%s, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
                                   'UNVERIFIED', 'UNKNOWN', 'NOT_APPLICABLE', %s, 'MOCK')""",
                        (case_id, json.dumps({"fixture": True})),
                    )
                    for consent_id, scope in (
                        (publication_consent_id, "COMMUNITY_PUBLICATION"),
                        (rag_consent_id, "RAG_REUSE"),
                    ):
                        await connection.execute(
                            """insert into private.consent_records
                                   (id, user_id, scope, case_id, content_hash, policy_version)
                               values (%s, %s, %s, %s, %s, 'community-live-test')""",
                            (consent_id, live_config.user_a, scope, case_id, content_hash),
                        )
                    await connection.execute(
                        """insert into public.community_posts
                               (id, case_id, owner_id, title, redacted_text, status,
                                publication_consent_id, rag_consent_id, content_hash, revision)
                           values (%s, %s, %s, 'Fixture community', 'Konten sintetis aman',
                                   'PUBLISHED_UNVERIFIED', %s, %s, %s, 1)""",
                        (
                            post_id,
                            case_id,
                            live_config.user_a,
                            publication_consent_id,
                            rag_consent_id,
                            content_hash,
                        ),
                    )

                    await set_test_claim(connection, live_config.user_b)
                    visible = await connection.execute(
                        """select p.case_id
                             from public.community_posts p
                            where p.case_id = %s
                              and p.withdrawn_at is null
                              and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
                              and exists (
                                  select 1 from private.consent_records c
                                   where c.id = p.publication_consent_id
                                     and c.scope = 'COMMUNITY_PUBLICATION'
                                     and c.revoked_at is null
                              )""",
                        (case_id,),
                    )
                    assert await visible.fetchone() == (case_id,)

                    await set_test_claim(connection, live_config.user_a)
                    await connection.execute(
                        """update public.community_posts
                              set status = 'WITHDRAWN', withdrawn_at = now(),
                                  revision = revision + 1
                            where id = %s""",
                        (post_id,),
                    )
                    await connection.execute(
                        """update public.verification_cases
                              set community_state = 'WITHDRAWN', revision = revision + 1
                            where id = %s""",
                        (case_id,),
                    )
                    await connection.execute(
                        """update private.consent_records set revoked_at = now()
                            where id in (%s, %s)""",
                        (publication_consent_id, rag_consent_id),
                    )

                    await set_test_claim(connection, live_config.user_b)
                    hidden = await connection.execute(
                        "select case_id from public.community_posts where id = %s", (post_id,)
                    )
                    assert await hidden.fetchone() is None

                    await set_test_claim(connection, live_config.user_a)
                    revoked = await connection.execute(
                        """select scope, revoked_at is not null
                             from private.consent_records
                            where id in (%s, %s)
                            order by scope""",
                        (publication_consent_id, rag_consent_id),
                    )
                    assert await revoked.fetchall() == [
                        ("COMMUNITY_PUBLICATION", True),
                        ("RAG_REUSE", True),
                    ]
        finally:
            await pool.close()

    run_async(check())


def test_backend_health_and_readiness(
    live_config: LiveConfig, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("DATABASE_URL", live_config.database_url)
    get_settings.cache_clear()
    prior_policy = asyncio.get_event_loop_policy()
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    try:
        with TestClient(create_app()) as client:
            assert client.get("/api/health").status_code == 200
            assert client.get("/api/ready").status_code == 200
    finally:
        if os.name == "nt":
            asyncio.set_event_loop_policy(prior_policy)
        get_settings.cache_clear()


def test_published_community_is_shared_and_social_is_idempotent(
    live_config: LiveConfig,
) -> None:
    """User B can see A's case, like once, and submit a shared response."""

    async def check() -> None:
        pool = await open_single_connection_pool(live_config.database_url)
        try:
            case_id = uuid4()
            operation_id = uuid4()
            post_id = uuid4()
            publication_consent_id = uuid4()
            content_hash = "f" * 64
            async with pool.connection() as connection:
                async with connection.transaction(force_rollback=True):
                    await set_test_claim(connection, live_config.user_a)
                    await connection.execute(
                        """insert into private.request_operations
                           (id, user_id, route_key, idempotency_key, payload_hash,
                            state, persistence_state, expires_at)
                           values (%s, %s, 'community-cross-user', %s, %s,
                                   'COMPLETED', 'SAVED', now() + interval '1 hour')""",
                        (operation_id, live_config.user_a, uuid4(), content_hash),
                    )
                    await connection.execute(
                        """insert into public.verification_conversations
                           (id, user_id, title, latest_message_preview,
                            latest_message_role, last_verdict, next_turn_index,
                            retention_expires_at)
                           values (%s, %s, 'Shared case', 'Shared case',
                                   'ASSISTANT', 'UNVERIFIED', 2, now() + interval '1 day')""",
                        (case_id, live_config.user_a),
                    )
                    await connection.execute(
                        """insert into public.verification_cases
                           (id, user_id, operation_id, product_request_id,
                            conversation_id, turn_index, input_type,
                            input_source, sanitized_text, input_hash, headline, verdict,
                            risk_level, requires_human_review, save_reason, community_state,
                            retention_expires_at)
                           values (%s, %s, %s, %s, %s, 1, 'TEXT', 'MANUAL', 'Shared case', %s,
                                   'Shared case', 'UNVERIFIED', 'LOW', false, 'UNVERIFIED',
                                   'PUBLISHED_UNVERIFIED', now() + interval '1 day')""",
                        (case_id, live_config.user_a, operation_id, uuid4(), case_id, content_hash),
                    )
                    await connection.execute(
                        """insert into public.verification_results
                           (case_id, factual_status, source_authenticity, sender_identity,
                            channel_status, scam_risk, content_authenticity, result_json,
                            execution_mode)
                           values (%s, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
                                   'UNVERIFIED', 'LOW', 'NOT_APPLICABLE', %s, 'MOCK')""",
                        (case_id, json.dumps({"fixture": True})),
                    )
                    await connection.execute(
                        """insert into private.consent_records
                           (id, user_id, scope, case_id, content_hash, policy_version)
                           values (%s, %s, 'COMMUNITY_PUBLICATION', %s, %s, 'live-test')""",
                        (publication_consent_id, live_config.user_a, case_id, content_hash),
                    )
                    await connection.execute(
                        """insert into public.community_posts
                           (id, case_id, owner_id, title, redacted_text, status,
                            publication_consent_id, content_hash, revision)
                           values (%s, %s, %s, 'Shared case', 'Shared case body',
                                   'PUBLISHED_UNVERIFIED', %s, %s, 1)""",
                        (post_id, case_id, live_config.user_a, publication_consent_id, content_hash),
                    )

                    await set_test_claim(connection, live_config.user_b)
                    visible = await connection.execute(
                        """select p.case_id from public.community_posts p
                            join public.verification_results r on r.case_id = p.case_id
                           where p.case_id = %s and p.status = 'PUBLISHED_UNVERIFIED'
                             and p.withdrawn_at is null""",
                        (case_id,),
                    )
                    assert await visible.fetchone() == (case_id,)
                    await connection.execute(
                        """insert into public.community_likes(post_id, user_id)
                           values (%s, %s) on conflict do nothing""",
                        (post_id, live_config.user_b),
                    )
                    await connection.execute(
                        """insert into public.community_likes(post_id, user_id)
                           values (%s, %s) on conflict do nothing""",
                        (post_id, live_config.user_b),
                    )
                    like_count = await connection.execute(
                        "select count(*) from public.community_likes where post_id = %s",
                        (post_id,),
                    )
                    assert await like_count.fetchone() == (1,)
                    await connection.execute(
                        """insert into public.community_votes(post_id, user_id, vote, reasoning)
                           values (%s, %s, 'VALID', 'Response from another user')
                           on conflict (post_id, user_id) do update
                           set vote = excluded.vote, reasoning = excluded.reasoning""",
                        (post_id, live_config.user_b),
                    )
                    await set_test_claim(connection, live_config.user_a)
                    response = await connection.execute(
                        "select user_id from public.community_votes where post_id = %s",
                        (post_id,),
                    )
                    assert (live_config.user_b,) in await response.fetchall()
        finally:
            await pool.close()

    run_async(check())
