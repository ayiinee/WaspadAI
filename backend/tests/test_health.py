from fastapi.testclient import TestClient

from app.main import create_app


def test_health_is_available_without_remote_configuration() -> None:
    with TestClient(create_app()) as client:
        response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "waspadai-product"}
