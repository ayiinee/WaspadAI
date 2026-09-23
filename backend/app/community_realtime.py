from __future__ import annotations

import asyncio
from typing import Any
from uuid import UUID

from fastapi import WebSocket


class CommunityConnectionManager:
    """Single-process community event bus for the hackathon deployment."""

    def __init__(self) -> None:
        self._connections: dict[WebSocket, UUID] = {}
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket, user_id: UUID) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections[websocket] = user_id

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections.pop(websocket, None)

    async def broadcast(
        self,
        event: dict[str, Any],
        exclude_user_id: UUID | None = None,
    ) -> None:
        async with self._lock:
            connections = tuple(
                websocket
                for websocket, user_id in self._connections.items()
                if user_id != exclude_user_id
            )
        stale: list[WebSocket] = []
        for websocket in connections:
            try:
                await websocket.send_json(event)
            except Exception:
                stale.append(websocket)
        if stale:
            async with self._lock:
                for websocket in stale:
                    self._connections.pop(websocket, None)
