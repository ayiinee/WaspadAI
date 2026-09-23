"""Learning content seeder backed by the canonical Supabase SQL catalog."""
from __future__ import annotations

from pathlib import Path

from psycopg import AsyncConnection


_BEGIN_MARKER = "-- LEARNING_SEED_BEGIN"
_END_MARKER = "-- LEARNING_SEED_END"
_CANONICAL_SEED = Path(__file__).parents[2] / "supabase" / "seed.sql"


def canonical_learning_seed_sql() -> str:
    """Extract the learning-only statements so SQL and Python cannot drift."""
    source = _CANONICAL_SEED.read_text(encoding="utf-8")
    try:
        learning = source.split(_BEGIN_MARKER, 1)[1].split(_END_MARKER, 1)[0]
    except IndexError as error:
        raise RuntimeError("Canonical learning seed markers are missing") from error
    return f"do $$\nbegin\n{learning}\nend $$;"


async def seed_learning_content(connection: AsyncConnection) -> int:
    """Run the canonical learning catalog and return its published module count."""
    await connection.execute(canonical_learning_seed_sql())
    result = await connection.execute(
        """select count(*)::int as count
             from public.learning_modules
            where status = 'PUBLISHED'
              and slug in (
                  'phishing-otp-pin',
                  'impersonation-instansi-resmi',
                  'misinformasi-dan-klaim-tanpa-bukti'
              )"""
    )
    row = await result.fetchone()
    return int(row["count"])
