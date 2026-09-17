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
