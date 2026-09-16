"""Uvicorn loop factory compatible with Psycopg async connections on Windows."""

from __future__ import annotations

import asyncio


def selector_loop_factory(*, use_subprocess: bool = False) -> asyncio.AbstractEventLoop:
    """Use a selector loop; Psycopg async does not support Windows Proactor."""

    return asyncio.SelectorEventLoop()
