from uuid import uuid4

from fastapi.testclient import TestClient

from app.auth import AuthenticatedUser, get_current_user
from app.main import create_app
from app.models import CommunityStateResponse


def test_product_routes_and_idempotency_header_are_exported() -> None:
    specification = create_app().openapi()
    post_operation = specification["paths"]["/api/v1/verifications/text"]["post"]
    parameters = post_operation["parameters"]

    assert {parameter["name"] for parameter in parameters} == {"Idempotency-Key"}
    assert parameters[0]["in"] == "header"
    assert parameters[0]["required"] is True
    assert "/api/v1/history" in specification["paths"]
    assert "/api/v1/history/{case_id}" in specification["paths"]
    assert "/api/v1/verifications/image" in specification["paths"]
    assert "/api/v1/verify/text" not in specification["paths"]
    assert "/api/v1/verify/image" not in specification["paths"]
    assert "/api/v1/community" in specification["paths"]
    assert "/api/v1/community/me/summary" in specification["paths"]
    assert "/api/v1/community/{case_id}" in specification["paths"]
    assert "/api/v1/history/{case_id}/community-preview" in specification["paths"]
    assert "/api/v1/history/{case_id}/community" in specification["paths"]
    assert "/api/v1/learning/modules" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}" in specification["paths"]
    assert "/api/v1/learning/lessons/{lesson_id}/complete" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}/quiz" in specification["paths"]
    assert "/api/v1/learning/modules/{module_id}/quiz-attempts" in specification["paths"]
    assert "/api/v1/learning/progress" in specification["paths"]
    complete_parameters = specification["paths"][
        "/api/v1/learning/lessons/{lesson_id}/complete"
    ]["post"]["parameters"]
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
    monkeypatch.setattr("app.main.withdraw_community_case", withdraw)
    with TestClient(app) as client:
        response = client.delete(f"/api/v1/history/{case_id}/community")

    assert response.status_code == 200
    assert response.json() == {
        "case_id": str(case_id),
        "community_state": "WITHDRAWN",
        "revision": 3,
    }
