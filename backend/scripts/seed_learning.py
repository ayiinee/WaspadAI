from __future__ import annotations

import asyncio

from app.config import get_settings
from app.database import create_pool
from app.learning_seed import seed_learning_content


async def main() -> None:
    settings = get_settings()
    pool = create_pool(settings)
    if pool is None:
        raise RuntimeError("DATABASE_URL belum dikonfigurasi")
    await pool.open()
    try:
        async with pool.connection() as connection:
            async with connection.transaction():
                count = await seed_learning_content(connection)
        print(f"Seeded {count} learning modules")
    finally:
        await pool.close()


if __name__ == "__main__":
    asyncio.run(main())
