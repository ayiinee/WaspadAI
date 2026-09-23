from __future__ import annotations

import asyncio
import sys

from app.config import get_settings
from app.learning_seed import seed_learning_content, upload_learning_assets
from psycopg import AsyncConnection
from psycopg.rows import dict_row


async def main() -> None:
    settings = get_settings()
    if settings.migration_database_url is None:
        raise RuntimeError(
            "MIGRATION_DATABASE_URL belum dikonfigurasi. Seeder tidak boleh memakai role runtime."
        )
    asset_count = await upload_learning_assets(settings)
    async with await AsyncConnection.connect(
        settings.migration_database_url.get_secret_value(),
        row_factory=dict_row,
    ) as connection:
        async with connection.transaction():
            count = await seed_learning_content(connection)
    print(f"Seeded {count} learning modules and uploaded {asset_count} learning assets")


if __name__ == "__main__":
    if sys.platform == "win32":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    asyncio.run(main())
