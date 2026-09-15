# Backend Product API

Python 3.11 dan dependency dikunci di `uv.lock`. Jalankan dari direktori ini:

```powershell
py -m uv sync --locked
py -m uv run python run_server.py
py -m uv run pytest
```

`uv` biasa juga dapat dipakai jika berada di PATH. Konfigurasi dibaca dari `.env` pada root repository. Backend dapat memulai tanpa credential remote agar endpoint health dan test lokal tersedia; readiness dan endpoint yang membutuhkan Supabase akan fail-closed sampai konfigurasi lengkap tersedia. `run_server.py` memakai Selector event loop agar psycopg async kompatibel dengan Windows.
