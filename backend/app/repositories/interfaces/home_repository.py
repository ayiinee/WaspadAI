from __future__ import annotations

from typing import Protocol
from uuid import UUID

from app.domain.home.models import HomeSnapshot


class HomeRepository(Protocol):
    async def load(
        self,
        user_id: UUID,
        case_limit: int,
        learning_limit: int,
    ) -> HomeSnapshot: ...
