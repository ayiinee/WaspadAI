from pathlib import Path

from app.learning_seed import canonical_learning_seed_sql


def test_python_learning_seed_uses_canonical_sql_catalog() -> None:
    """The Python entry point extracts, rather than duplicates, the SQL catalog."""
    sql = (Path(__file__).parents[2] / "supabase" / "seed.sql").read_text(encoding="utf-8")
    extracted = canonical_learning_seed_sql()
    assert "LEARNING_SEED_BEGIN" not in extracted
    assert "insert into public.learning_modules" in extracted
    assert "insert into public.learning_media" in extracted
    assert "insert into public.quiz_options" in extracted
    assert "insert into public.community_posts" not in extracted
    assert all(slug in sql and slug in extracted for slug in (
        "phishing-otp-pin",
        "impersonation-instansi-resmi",
        "misinformasi-dan-klaim-tanpa-bukti",
    ))


def test_learning_seed_runs_independently_from_auth_fixture() -> None:
    sql = (Path(__file__).parents[2] / "supabase" / "seed.sql").read_text(encoding="utf-8")
    community_guard_end = sql.index("end if;")
    learning_start = sql.index("-- LEARNING_SEED_BEGIN")
    assert community_guard_end < learning_start
