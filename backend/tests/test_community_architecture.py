from __future__ import annotations

import ast
from pathlib import Path

import app.models as legacy_models
from app.schemas.community import CommunityItem


def test_community_service_contains_no_inline_sql() -> None:
    service_path = Path(__file__).parents[1] / "app" / "community_service.py"
    tree = ast.parse(service_path.read_text(encoding="utf-8"))

    inline_sql_lines = [
        node.lineno
        for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "execute"
        and node.args
        and isinstance(node.args[0], ast.Constant)
        and isinstance(node.args[0].value, str)
    ]

    assert inline_sql_lines == []


def test_legacy_models_reexports_canonical_community_schema() -> None:
    assert legacy_models.CommunityItem is CommunityItem
