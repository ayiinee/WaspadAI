from __future__ import annotations

from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.models import QuizAttemptRequest, QuizOption


def test_quiz_option_public_model_has_no_answer_key() -> None:
    option = QuizOption(option_id=uuid4(), text="Jangan bagikan OTP.")

    assert option.model_dump().keys() == {"option_id", "text"}


def test_quiz_attempt_rejects_duplicate_question_answers() -> None:
    question_id = uuid4()

    with pytest.raises(ValidationError):
        QuizAttemptRequest.model_validate(
            {
                "module_version": 1,
                "answers": [
                    {"question_id": str(question_id), "selected_option_id": str(uuid4())},
                    {"question_id": str(question_id), "selected_option_id": str(uuid4())},
                ],
            }
        )


def test_quiz_attempt_accepts_session_durations() -> None:
    request = QuizAttemptRequest.model_validate(
        {
            "module_version": 1,
            "answers": [
                {"question_id": str(uuid4()), "selected_option_id": str(uuid4())},
            ],
            "reading_duration_seconds": 125,
            "quiz_duration_seconds": 48,
        }
    )

    assert request.reading_duration_seconds == 125
    assert request.quiz_duration_seconds == 48


def test_quiz_attempt_rejects_unreasonable_session_duration() -> None:
    with pytest.raises(ValidationError):
        QuizAttemptRequest.model_validate(
            {
                "module_version": 1,
                "answers": [
                    {"question_id": str(uuid4()), "selected_option_id": str(uuid4())},
                ],
                "reading_duration_seconds": 86_401,
                "quiz_duration_seconds": 1,
            }
        )
