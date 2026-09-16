from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.models import CommunityVoteRequest, ImageVerificationRequest


@pytest.mark.parametrize("vote", ["HOAKS", "WASPADA", "VALID"])
def test_community_vote_accepts_classification_enum(vote: str) -> None:
    assert CommunityVoteRequest.model_validate({"vote": vote}).vote == vote


@pytest.mark.parametrize("vote", ["DIDUKUNG", "DIBANTAH", "SUPPORTED", ""])
def test_community_vote_rejects_legacy_or_unknown_values(vote: str) -> None:
    with pytest.raises(ValidationError):
        CommunityVoteRequest.model_validate({"vote": vote})


def test_community_vote_rejects_extra_fields() -> None:
    with pytest.raises(ValidationError):
        CommunityVoteRequest.model_validate({"vote": "VALID", "case_id": "ignored"})


def test_image_question_is_trimmed_and_bounded() -> None:
    request = ImageVerificationRequest.model_validate({"question": "  Apakah benar?  "})
    assert request.question == "Apakah benar?"
    with pytest.raises(ValidationError):
        ImageVerificationRequest.model_validate({"question": "x" * 501})
