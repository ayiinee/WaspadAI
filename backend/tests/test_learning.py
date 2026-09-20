from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from types import SimpleNamespace
from typing import Any
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

import app.learning_service as learning_service
from app.auth import AuthenticatedUser, get_current_user
from app.errors import ProductAPIError
from app.main import create_app
from app.models import (
    LearningQuiz,
    QuizAttemptAnswer,
    QuizAttemptRequest,
    QuizOption,
    QuizQuestion,
)


class FakeCursor:
    def __init__(
        self,
        rows: list[dict[str, Any]] | None = None,
        row: dict[str, Any] | None = None,
    ) -> None:
        self._rows = rows if rows is not None else ([row] if row is not None else [])
        self._row = row if row is not None else (rows[0] if rows else None)

    async def fetchall(self) -> list[dict[str, Any]]:
        return self._rows

    async def fetchone(self) -> dict[str, Any] | None:
        return self._row


class FakeLearningConnection:
    def __init__(
        self,
        queries_responses: dict[str, Any] | None = None,
        default_row: dict[str, Any] | None = None,
    ) -> None:
        self.queries_responses = queries_responses or {}
        self.default_row = default_row
        self.executed: list[tuple[str, tuple[Any, ...]]] = []

    async def execute(self, query: str, parameters: tuple[Any, ...] = ()) -> FakeCursor:
        self.executed.append((query, parameters))
        for pattern, response in self.queries_responses.items():
            if pattern in query:
                if isinstance(response, list):
                    return FakeCursor(rows=response)
                return FakeCursor(row=response)
        return FakeCursor(row=self.default_row)


@asynccontextmanager
async def fake_user_transaction(
    connection: FakeLearningConnection,
) -> AsyncIterator[FakeLearningConnection]:
    yield connection


def test_list_learning_modules_requires_token() -> None:
    """Requirement 1: GET /api/v1/learning/modules requires Bearer authentication."""
    with TestClient(create_app()) as client:
        response = client.get("/api/v1/learning/modules")
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "INVALID_ACCESS_TOKEN"


def test_quiz_response_does_not_contain_is_correct(monkeypatch: pytest.MonkeyPatch) -> None:
    """Requirement 2: Quiz response does not contain is_correct in any option."""
    user_id = uuid4()
    module_id = uuid4()
    question_id = uuid4()
    opt_1 = uuid4()
    opt_2 = uuid4()

    mock_quiz = LearningQuiz(
        module_id=module_id,
        module_version=1,
        questions=[
            QuizQuestion(
                question_id=question_id,
                text="Apakah petugas bank boleh meminta OTP?",
                options=[
                    QuizOption(option_id=opt_1, text="Tidak pernah."),
                    QuizOption(option_id=opt_2, text="Boleh jika darurat."),
                ],
            )
        ],
    )

    async def mock_get_quiz(*_args: Any, **_kwargs: Any) -> LearningQuiz:
        return mock_quiz

    app = create_app()
    app.dependency_overrides[get_current_user] = lambda: AuthenticatedUser(user_id, None)
    monkeypatch.setattr("app.main.get_module_quiz", mock_get_quiz)

    with TestClient(app) as client:
        response = client.get(f"/api/v1/learning/modules/{module_id}/quiz")

    assert response.status_code == 200
    data = response.json()
    assert data["module_id"] == str(module_id)
    assert len(data["questions"]) == 1
    options = data["questions"][0]["options"]
    assert len(options) == 2
    for option in options:
        assert "option_id" in option
        assert "text" in option
        assert "is_correct" not in option


def test_complete_lesson_is_idempotent(monkeypatch: pytest.MonkeyPatch) -> None:
    """Requirement 3: Completing a lesson twice is idempotent and does not fail."""
    user_id = uuid4()
    lesson_id = uuid4()
    module_id = uuid4()
    idempotency_key = uuid4()
    completed_time = datetime.now(UTC)

    connection = FakeLearningConnection(
        queries_responses={
            "select id, module_id from public.published_learning_lessons": {
                "id": lesson_id,
                "module_id": module_id,
            },
            "select completed_at from public.lesson_progress": {
                "completed_at": completed_time,
            },
            "select m.id, m.slug, m.title": {
                "id": module_id,
                "slug": "phishing-otp-pin",
                "title": "Phishing, OTP, dan PIN",
                "summary": "Summary",
                "difficulty": 1,
                "version": 1,
                "total_lessons": 2,
                "completed_lessons": 1,
            },
        }
    )

    @asynccontextmanager
    async def transaction(*_args: Any, **_kwargs: Any) -> AsyncIterator[FakeLearningConnection]:
        yield connection

    monkeypatch.setattr(learning_service, "user_transaction", transaction)

    settings = SimpleNamespace(db_statement_timeout_seconds=15)
    pool = object()

    # Panggilan pertama
    res1 = asyncio.run(
        learning_service.complete_lesson(pool, settings, user_id, lesson_id, idempotency_key)  # type: ignore[arg-type]
    )
    # Panggilan kedua (idempotent)
    res2 = asyncio.run(
        learning_service.complete_lesson(pool, settings, user_id, lesson_id, idempotency_key)  # type: ignore[arg-type]
    )

    assert res1.completed is True
    assert res2.completed is True
    assert res1.lesson_id == lesson_id
    assert res2.lesson_id == lesson_id
    assert res1.completed_at == res2.completed_at
    assert res1.progress_percent == res2.progress_percent == 50.0

    # Pastikan query insert memiliki klausa on conflict do nothing
    insert_queries = [
        q for q, _ in connection.executed if "insert into public.lesson_progress" in q
    ]
    assert len(insert_queries) == 2
    assert "on conflict (user_id, lesson_id) do nothing" in insert_queries[0]


def test_submit_quiz_valid_answers_computes_server_score(monkeypatch: pytest.MonkeyPatch) -> None:
    """Requirement 4: Submitting quiz with valid answers produces server-calculated score."""
    user_id = uuid4()
    module_id = uuid4()
    idempotency_key = uuid4()
    q1 = uuid4()
    q2 = uuid4()
    q1_opt_correct = uuid4()
    q1_opt_wrong = uuid4()
    q2_opt_correct = uuid4()
    q2_opt_wrong = uuid4()

    scoring_rows = [
        {
            "question_id": q1,
            "question_text": "Soal 1",
            "explanation": "Penjelasan 1",
            "version": 1,
            "option_id": q1_opt_correct,
            "option_text": "Jawaban Benar 1",
            "is_correct": True,
        },
        {
            "question_id": q1,
            "question_text": "Soal 1",
            "explanation": "Penjelasan 1",
            "version": 1,
            "option_id": q1_opt_wrong,
            "option_text": "Jawaban Salah 1",
            "is_correct": False,
        },
        {
            "question_id": q2,
            "question_text": "Soal 2",
            "explanation": "Penjelasan 2",
            "version": 1,
            "option_id": q2_opt_correct,
            "option_text": "Jawaban Benar 2",
            "is_correct": True,
        },
        {
            "question_id": q2,
            "question_text": "Soal 2",
            "explanation": "Penjelasan 2",
            "version": 1,
            "option_id": q2_opt_wrong,
            "option_text": "Jawaban Salah 2",
            "is_correct": False,
        },
    ]

    attempt_id = uuid4()
    connection = FakeLearningConnection(
        queries_responses={
            "submission_key = %s": None,  # no existing attempt
            "select id, version from public.published_learning_modules": {
                "id": module_id,
                "version": 1,
            },
            "from public.quiz_questions q": scoring_rows,
            "insert into public.quiz_attempts": {
                "id": attempt_id,
                "score": 100.0,
                "correct_answers": 2,
                "total_questions": 2,
                "question_snapshot": [
                    {
                        "question_id": str(q1),
                        "selected_option_id": str(q1_opt_correct),
                        "is_correct": True,
                        "explanation": "Penjelasan 1",
                    },
                    {
                        "question_id": str(q2),
                        "selected_option_id": str(q2_opt_correct),
                        "is_correct": True,
                        "explanation": "Penjelasan 2",
                    },
                ],
            },
        }
    )

    @asynccontextmanager
    async def transaction(*_args: Any, **_kwargs: Any) -> AsyncIterator[FakeLearningConnection]:
        yield connection

    monkeypatch.setattr(learning_service, "user_transaction", transaction)

    settings = SimpleNamespace(db_statement_timeout_seconds=15)
    pool = object()

    payload = QuizAttemptRequest(
        module_version=1,
        answers=[
            QuizAttemptAnswer(question_id=q1, selected_option_id=q1_opt_correct),
            QuizAttemptAnswer(question_id=q2, selected_option_id=q2_opt_correct),
        ],
    )

    result = asyncio.run(
        learning_service.submit_quiz_attempt(
            pool,
            settings,
            user_id,
            module_id,
            idempotency_key,
            payload,  # type: ignore[arg-type]
        )
    )

    assert result.attempt_id == attempt_id
    assert result.score == 100.0
    assert result.correct_answers == 2
    assert result.total_questions == 2
    assert len(result.feedback) == 2
    assert all(item.correct for item in result.feedback)


def test_submit_quiz_rejects_option_from_another_question(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Requirement 5: Submitting option from another question is rejected with 422."""
    user_id = uuid4()
    module_id = uuid4()
    idempotency_key = uuid4()
    q1 = uuid4()
    q2 = uuid4()
    q1_opt = uuid4()
    q2_opt = uuid4()

    scoring_rows = [
        {
            "question_id": q1,
            "question_text": "Soal 1",
            "explanation": "Penjelasan 1",
            "version": 1,
            "option_id": q1_opt,
            "option_text": "Opsi 1",
            "is_correct": True,
        },
        {
            "question_id": q2,
            "question_text": "Soal 2",
            "explanation": "Penjelasan 2",
            "version": 1,
            "option_id": q2_opt,
            "option_text": "Opsi 2",
            "is_correct": True,
        },
    ]

    connection = FakeLearningConnection(
        queries_responses={
            "submission_key = %s": None,
            "select id, version from public.published_learning_modules": {
                "id": module_id,
                "version": 1,
            },
            "from public.quiz_questions q": scoring_rows,
        }
    )

    @asynccontextmanager
    async def transaction(*_args: Any, **_kwargs: Any) -> AsyncIterator[FakeLearningConnection]:
        yield connection

    monkeypatch.setattr(learning_service, "user_transaction", transaction)

    settings = SimpleNamespace(db_statement_timeout_seconds=15)
    pool = object()

    # q1 dijawab dengan q2_opt (option dari soal lain!)
    payload = QuizAttemptRequest(
        module_version=1,
        answers=[
            QuizAttemptAnswer(question_id=q1, selected_option_id=q2_opt),
            QuizAttemptAnswer(question_id=q2, selected_option_id=q2_opt),
        ],
    )

    with pytest.raises(ProductAPIError) as exc_info:
        asyncio.run(
            learning_service.submit_quiz_attempt(
                pool,
                settings,
                user_id,
                module_id,
                idempotency_key,
                payload,  # type: ignore[arg-type]
            )
        )

    assert exc_info.value.status_code == 422
    assert exc_info.value.code == "QUIZ_OPTION_MISMATCH"


def test_user_progress_is_isolated_between_users(monkeypatch: pytest.MonkeyPatch) -> None:
    """Requirement 6: User A's progress is not visible to User B."""
    user_a = uuid4()
    user_b = uuid4()
    module_id = uuid4()

    # Mocking query execution to record user_id parameter passed to SQL
    executed_params: list[tuple[Any, ...]] = []

    class ProgressConnection:
        async def execute(self, query: str, parameters: tuple[Any, ...] = ()) -> FakeCursor:
            executed_params.append(parameters)
            current_user = parameters[0]
            if current_user == user_a:
                # User A sudah menyelesaikan 2 lesson dan kuis skor 90
                rows = [
                    {
                        "module_id": module_id,
                        "total_lessons": 2,
                        "completed_lessons": 2,
                        "last_lesson_completed_at": datetime.now(UTC),
                        "latest_score": 90.0,
                        "best_score": 90.0,
                        "latest_quiz_completed_at": datetime.now(UTC),
                    }
                ]
            else:
                # User B belum menyelesaikan apa pun
                rows = [
                    {
                        "module_id": module_id,
                        "total_lessons": 2,
                        "completed_lessons": 0,
                        "last_lesson_completed_at": None,
                        "latest_score": None,
                        "best_score": None,
                        "latest_quiz_completed_at": None,
                    }
                ]
            return FakeCursor(rows=rows)

    @asynccontextmanager
    async def transaction(*_args: Any, **_kwargs: Any) -> AsyncIterator[ProgressConnection]:
        yield ProgressConnection()

    monkeypatch.setattr(learning_service, "user_transaction", transaction)

    settings = SimpleNamespace(db_statement_timeout_seconds=15)
    pool = object()

    progress_a = asyncio.run(
        learning_service.get_learning_progress(pool, settings, user_a)  # type: ignore[arg-type]
    )
    progress_b = asyncio.run(
        learning_service.get_learning_progress(pool, settings, user_b)  # type: ignore[arg-type]
    )

    # Verifikasi User A melihat progress dirinya
    assert len(progress_a.items) == 1
    assert progress_a.items[0].completed_lessons == 2
    assert progress_a.items[0].progress_percent == 100.0
    assert progress_a.items[0].latest_score == 90.0

    # Verifikasi User B tidak melihat progress User A (tetap 0)
    assert len(progress_b.items) == 1
    assert progress_b.items[0].completed_lessons == 0
    assert progress_b.items[0].progress_percent == 0.0
    assert progress_b.items[0].latest_score is None

    # Verifikasi parameter user_id pada SQL query diikat ke user masing-masing
    assert executed_params[0] == (user_a, user_a, user_a)
    assert executed_params[1] == (user_b, user_b, user_b)
