from __future__ import annotations

import asyncio
import os

import uvicorn


def selector_event_loop_factory(use_subprocess: bool = False) -> asyncio.AbstractEventLoop:
    """Use the event loop required by psycopg async on Windows."""

    return asyncio.SelectorEventLoop()


if __name__ == "__main__":
    config = uvicorn.Config(
        "app.main:app",
        host=os.getenv("APP_HOST", "0.0.0.0"),
        port=int(os.getenv("APP_PORT", "8001")),
        reload=True,
        loop="run_server:selector_event_loop_factory",
    )
    uvicorn.Server(config).run()
