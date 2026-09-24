"""Learning content seeder backed by the canonical Supabase SQL catalog."""
from __future__ import annotations

from pathlib import Path

import httpx
from psycopg import AsyncConnection

from app.config import Settings


_BEGIN_MARKER = "-- LEARNING_SEED_BEGIN"
_END_MARKER = "-- LEARNING_SEED_END"
_CANONICAL_SEED = Path(__file__).parents[2] / "supabase" / "seed.sql"
_ASSET_DIRECTORY = Path(__file__).parents[1] / "assets" / "learning"
LEARNING_ASSETS = {
    "learning/phishing-otp-pin.jpg": ("phishing-otp-pin.jpg", "image/jpeg"),
    "learning/impersonation-instansi-resmi.jpg": (
        "impersonation-instansi-resmi.jpg",
        "image/jpeg",
    ),
    "learning/misinformasi-fact-checking.png": (
        "misinformasi-fact-checking.png",
        "image/png",
    ),
}


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


async def upload_learning_assets(settings: Settings) -> int:
    """Idempotently upload curated project assets to the existing private bucket."""
    if settings.supabase_url is None or settings.supabase_service_role_key is None:
        raise RuntimeError("SUPABASE_URL dan SUPABASE_SERVICE_ROLE_KEY wajib untuk seed media")
    service_key = settings.supabase_service_role_key.get_secret_value()
    async with httpx.AsyncClient(timeout=30) as client:
        for object_path, (filename, content_type) in LEARNING_ASSETS.items():
            asset_path = _ASSET_DIRECTORY / filename
            response = await client.put(
                f"{settings.supabase_url.rstrip('/')}/storage/v1/object/learning-assets/{object_path}",
                content=asset_path.read_bytes(),
                headers={
                    "Authorization": f"Bearer {service_key}",
                    "apikey": service_key,
                    "Content-Type": content_type,
                    "Cache-Control": "public, max-age=31536000, immutable",
                    "x-upsert": "true",
                },
            )
            response.raise_for_status()
    return len(LEARNING_ASSETS)
