from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.models import CommunityItem, CommunityVoteRequest, ImageVerificationRequest


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


def test_community_media_supports_one_to_four_items() -> None:
    payload = {
        "id": "73e42666-e1de-4e40-a0fe-5504609700d1",
        "case_id": "73e42666-e1de-4e40-a0fe-5504609700d2",
        "creator": {"display_name": "Anda", "is_current_user": True},
        "title": "Kasus",
        "redacted_text": "Konten aman",
        "status": "PUBLISHED_UNVERIFIED",
        "published_at": "2026-09-22T00:00:00Z",
        "counts": {},
        "media": [
            {
                "id": f"73e42666-e1de-4e40-a0fe-55046097000{index}",
                "url": f"/api/v1/community/case/media/{index}",
                "position": index,
            }
            for index in range(4)
        ],
    }
    item = CommunityItem.model_validate(payload)
    assert [media.position for media in item.media] == [0, 1, 2, 3]

    payload["media"].append(
        {
            "id": "73e42666-e1de-4e40-a0fe-550460970099",
            "url": "/too-many",
            "position": 0,
        }
    )
    with pytest.raises(ValidationError):
        CommunityItem.model_validate(payload)
