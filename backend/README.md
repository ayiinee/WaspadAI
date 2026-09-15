# Product Backend Scaffold

Status: `FUTURE` untuk jalur verifikasi. Backend ini bukan gateway Android→AI pada MVP current; Android memanggil public WaspadAI API sesuai [`../android-api-contract.md`](../android-api-contract.md).

Scaffold Python 3.11 saat ini digunakan untuk health/readiness dan persiapan fitur server-side seperti history, Storage, community, ownership, vote, dan moderation.

```powershell
py -m uv sync --locked
py -m uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
py -m uv run pytest
```

Konfigurasi dibaca dari `.env` root. Backend dapat start tanpa credential remote; readiness dan dependency yang belum dikonfigurasi harus fail closed. Jangan menganggap mode mock, draft OpenAPI, atau endpoint health sebagai bukti integrasi AI/backend end-to-end.

Jika Product Backend kelak menjadi gateway, ikuti [future architecture](../waspadai-product-docs/docs/architecture/future-product-backend.md) dan lakukan contract/security migration resmi.
