from uuid import uuid4

from fastapi.testclient import TestClient

from app.auth import AuthenticatedUser, get_current_user
from app.errors import ProductAPIError
from app.main import create_app
from app.models import ConversationItem
from app.schemas.community import CommunityStateResponse


def test_product_routes_and_idempotency_header_are_exported() -> None:
    specification = create_app().openapi()
    post_operation = specification["paths"]["/api/v1/verifications/text"]["post"]
    parameters = post_operation["parameters"]

    assert {parameter["name"] for parameter in parameters} == {"Idempotency-Key"}
    assert parameters[0]["in"] == "header"
    assert parameters[0]["required"] is True
    assert "/api/v1/history" in specification["paths"]
    assert "/api/v1/history/{case_id}" in specification["paths"]
    assert "/api/v1/conversations" in specification["paths"]
    assert "/api/v1/conversations/{conversation_id}" in specification["paths"]
    assert {"get", "patch", "delete"} <= set(
        specification["paths"]["/api/v1/conversations/{conversation_id}"]
    )
    assert (
        "/api/v1/conversations/{conversation_id}/attachments/{case_id}"
        in specification["paths"]
    )
    update_schema = specification["components"]["schemas"]["ConversationUpdateRequest"]
    assert update_schema["properties"]["title"]["maxLength"] == 80
    attachment_schema = specification["components"]["schemas"]["ConversationAttachment"]
    assert {"available", "content_type", "size_bytes"} <= set(
        attachment_schema["properties"]
    )
    assert "/api/v1/home" in specification["paths"]
    assert "/api/v1/verifications/image" in specification["paths"]
    text_request = specification["components"]["schemas"]["TextVerificationRequest"]
    assert "conversation_id" in text_request["properties"]
    history_meta = specification["components"]["schemas"]["HistoryMeta"]
    assert "conversation_id" in history_meta["properties"]
    assert "/api/v1/verify/text" not in specification["paths"]
    assert "/api/v1/verify/image" not in specification["paths"]
    assert "/api/v1/community" in specification["paths"]
    assert "refresh" in {
        parameter["name"]
        for parameter in specification["paths"]["/api/v1/community"]["get"]["parameters"]
    }
    assert "refresh" in {
        parameter["name"]
        for parameter in specification["paths"]["/api/v1/community/bootstrap"]["get"]["parameters"]
    }
    assert "/api/v1/community/me/summary" in specification["paths"]
    assert "/api/v1/community/{case_id}" in specification["paths"]
    assert "patch" in specification["paths"]["/api/v1/community/{case_id}"]
    assert "/api/v1/community/{case_id}/media/{media_id}" in specification["paths"]
    assert "/api/v1/history/{case_id}/community-preview" in specification["paths"]
    assert "/api/v1/history/{case_id}/community" in specification["paths"]
    assert "/api/v1/learning/modules" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}" in specification["paths"]
    assert "/api/v1/learning/lessons/{lesson_id}/complete" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}/quiz" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}/quiz-attempts" in specification["paths"]
    assert "/api/v1/learning/media/{object_path}" in specification["paths"]
    assert "/api/v1/learning/progress" in specification["paths"]
    complete_parameters = specification["paths"]["/api/v1/learning/lessons/{lesson_id}/complete"][
        "post"
    ]["parameters"]
    assert "Idempotency-Key" in {parameter["name"] for parameter in complete_parameters}
    quiz_attempt_parameters = specification["paths"][
        "/api/v1/learning/modules/{module_id}/quiz-attempts"
    ]["post"]["parameters"]
    assert "Idempotency-Key" in {parameter["name"] for parameter in quiz_attempt_parameters}
    quiz_option_schema = specification["components"]["schemas"]["QuizOption"]
    assert "is_correct" not in quiz_option_schema["properties"]
    withdrawal = specification["paths"]["/api/v1/history/{case_id}/community"]["delete"]
    assert withdrawal["responses"]["200"]["content"]["application/json"]["schema"]["$ref"].endswith(
        "/CommunityStateResponse"
    )
    vote_schema = specification["components"]["schemas"]["CommunityVoteRequest"]
    assert vote_schema["properties"]["vote"]["enum"] == ["HOAKS", "WASPADA", "VALID"]
    publication = specification["paths"]["/api/v1/history/{case_id}/community"]["post"]
    assert publication["responses"]["200"]["content"]["application/json"]["schema"][
        "$ref"
    ].endswith("/CommunityItem")
    publish_request = specification["components"]["schemas"]["CommunityPublishRequest"]
    assert "caption" in publish_request["required"]
    assert publish_request["properties"]["caption"]["maxLength"] == 5000
    community_item = specification["components"]["schemas"]["CommunityItem"]
    assert {"id", "case_id", "creator", "media"} <= set(community_item["properties"])
    social_result = specification["components"]["schemas"]["CommunitySocialResult"]
    assert {"community_id", "case_id"} <= set(social_result["properties"])
    update_request = specification["components"]["schemas"]["CommunityUpdateRequest"]
    assert update_request["required"] == ["caption"]
    assert update_request["properties"]["caption"]["maxLength"] == 5000


def test_community_feed_requires_supabase_bearer() -> None:
    with TestClient(create_app()) as client:
        response = client.get("/api/v1/community")
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_ACCESS_TOKEN"


def test_legacy_android_verification_route_requires_same_supabase_bearer() -> None:
    with TestClient(create_app()) as client:
        response = client.post(
            "/api/v1/verify/text",
            headers={"Idempotency-Key": str(uuid4())},
            json={"text": "Pesan uji cukup panjang untuk validasi."},
        )
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_ACCESS_TOKEN"


def test_community_withdrawal_endpoint_returns_withdrawn(monkeypatch) -> None:
    user_id = uuid4()
    case_id = uuid4()

    async def withdraw(*_args: object) -> CommunityStateResponse:
        return CommunityStateResponse(case_id=case_id, community_state="WITHDRAWN", revision=3)

    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    monkeypatch.setattr(
        "app.api.v1.routes.community.withdraw_community_case",
        withdraw,
    )
    with TestClient(app) as client:
        response = client.delete(f"/api/v1/history/{case_id}/community")

    assert response.status_code == 200
    assert response.json() == {
        "case_id": str(case_id),
        "community_state": "WITHDRAWN",
        "revision": 3,
    }


def test_self_response_error_contract(monkeypatch) -> None:
    user_id = uuid4()
    community_id = uuid4()

    async def reject(*_args: object) -> None:
        raise ProductAPIError(
            403,
            "CANNOT_RESPOND_OWN_POST",
            "User cannot respond to their own community case",
        )

    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    monkeypatch.setattr(
        "app.api.v1.routes.community.submit_community_response",
        reject,
    )
    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.post(
            f"/api/v1/community/{community_id}/response",
            data={"vote": "VALID", "reasoning": "Alasan yang cukup panjang."},
        )
        app.state.db_pool = None

    assert response.status_code == 403
    assert response.json()["error"]["code"] == "CANNOT_RESPOND_OWN_POST"
    assert response.json()["error"]["message"] == (
        "User cannot respond to their own community case"
    )


def test_rename_conversation_normalizes_title(monkeypatch) -> None:
    user_id = uuid4()
    conversation_id = uuid4()
    captured_title: str | None = None

    async def rename(*args: object) -> ConversationItem:
        nonlocal captured_title
        captured_title = str(args[-1])
        return ConversationItem(
            conversation_id=conversation_id,
            title=captured_title,
            latest_message_preview="Preview",
            latest_message_role="ASSISTANT",
            last_verdict="UNVERIFIED",
            created_at="2026-09-25T00:00:00Z",
            updated_at="2026-09-25T00:00:00Z",
        )

    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    monkeypatch.setattr("app.main.rename_conversation", rename)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.patch(
            f"/api/v1/conversations/{conversation_id}",
            json={"title": "  Judul    baru  "},
        )
        app.state.db_pool = None

    assert response.status_code == 200
    assert captured_title == "Judul baru"
    assert response.json()["title"] == "Judul baru"


def test_delete_conversation_soft_deletes_then_cleans_assets(monkeypatch) -> None:
    user_id = uuid4()
    conversation_id = uuid4()
    cleaned: list[tuple[str, str]] = []

    async def delete(*_args: object) -> list[tuple[str, str]]:
        return [("verification-inputs", "user/input.png")]

    async def clean(*_args: object, bucket: str, object_path: str, **_kwargs: object) -> None:
        cleaned.append((bucket, object_path))

    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    monkeypatch.setattr("app.main.delete_conversation", delete)
    monkeypatch.setattr("app.main.delete_verification_input", clean)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.delete(f"/api/v1/conversations/{conversation_id}")
        app.state.db_pool = None

    assert response.status_code == 204
    assert cleaned == [("verification-inputs", "user/input.png")]


def test_conversation_attachment_preserves_private_mime_response(monkeypatch) -> None:
    user_id = uuid4()
    conversation_id = uuid4()
    case_id = uuid4()

    async def attachment(*_args: object) -> tuple[bytes, str]:
        return b"private-image", "image/png"

    monkeypatch.setattr("app.main.create_pool", lambda _settings: None)
    monkeypatch.setattr("app.main.get_conversation_attachment", attachment)
    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    with TestClient(app) as client:
        app.state.db_pool = object()
        response = client.get(
            f"/api/v1/conversations/{conversation_id}/attachments/{case_id}"
        )
        app.state.db_pool = None

    assert response.status_code == 200
    assert response.content == b"private-image"
    assert response.headers["content-type"] == "image/png"
    assert response.headers["cache-control"] == "private, no-store"
