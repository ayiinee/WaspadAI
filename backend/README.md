# Product Backend Scaffold

Status: `FUTURE` untuk jalur verifikasi. Backend ini bukan gateway Android→AI pada MVP current; Android memanggil public WaspadAI API sesuai [`../android-api-contract.md`](../android-api-contract.md).

Scaffold Python 3.11 menyediakan health/readiness dan preview vertical slice server-side:
`POST /api/v1/verifications/text`, `GET /api/v1/history`, serta
`GET /api/v1/history/{case_id}`. Slice tersebut memerlukan Bearer Supabase,
role database `product_app`, `Idempotency-Key`, dan mode `mock`; endpoint tidak
boleh dipakai Android sampai contract/rollout resmi disetujui.

```powershell
py -m uv sync --locked
py -m uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload --loop app.uvicorn_loop:selector_loop_factory
py -m uv run pytest
```

Konfigurasi dibaca dari `.env` root. Untuk history, set secret mandiri
`HISTORY_CURSOR_SIGNING_KEY` dengan nilai acak berentropi tinggi; jangan memakai
password database. Backend dapat start tanpa credential remote; readiness dan
dependency yang belum dikonfigurasi harus fail closed. Jangan menganggap mode
mock, draft OpenAPI, atau endpoint health sebagai bukti integrasi AI/backend
end-to-end.

Jika Product Backend kelak menjadi gateway, ikuti [future architecture](../waspadai-product-docs/docs/architecture/future-product-backend.md) dan lakukan contract/security migration resmi.
