from __future__ import annotations

from typing import Any, Protocol
from uuid import UUID


class ProfileRepository(Protocol):
    async def get_profile(self, user_id: UUID) -> dict[str, Any] | None: ...
    async def update_profile(
        self,
        user_id: UUID,
        display_name: str | None,
        bio: str | None,
        update_bio: bool,
    ) -> dict[str, Any] | None: ...
    async def overview(self, user_id: UUID) -> dict[str, Any]: ...
    async def list_verifications(
        self, user_id: UUID, limit: int, offset: int, status: str | None
    ) -> list[dict[str, Any]]: ...
    async def list_publications(
        self, user_id: UUID, limit: int, offset: int, status: str | None
    ) -> list[dict[str, Any]]: ...
    async def list_community_activity(
        self, user_id: UUID, limit: int, offset: int
    ) -> list[dict[str, Any]]: ...
    async def list_learning(self, user_id: UUID) -> list[dict[str, Any]]: ...
    async def insert_avatar_asset(
        self,
        user_id: UUID,
        object_path: str,
        mime_type: str,
        size: int,
        digest: str,
    ) -> UUID: ...
    async def replace_avatar(
        self, user_id: UUID, asset_id: UUID
    ) -> dict[str, Any] | None: ...
    async def clear_avatar(self, user_id: UUID) -> dict[str, Any] | None: ...
