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

## Staging preview dan quality gates

Deployment staging harus memakai Python 3.11, `AI_SERVICE_MODE=mock`, serta secret
`DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, dan
`HISTORY_CURSOR_SIGNING_KEY`. Gunakan role `product_app` pada `DATABASE_URL` dan
jangan menaruh secret tersebut pada repository atau GitHub Actions variables biasa.
Setelah deploy, `/api/ready` harus mengembalikan HTTP 200 dan `{"status":"ready"}`.

Workflow **Product CI** selalu menjalankan unit test dan Ruff. Dari GitHub Actions,
jalankan manual workflow tersebut dengan input sesuai kebutuhan berikut; masing-masing
job memakai GitHub Environment agar secret dapat diproteksi.

| Input | Environment | GitHub Actions secrets |
| --- | --- | --- |
| `run_live_db_tests` | `supabase-development` | `PRODUCT_APP_DATABASE_URL`, `WASPADAI_DB_TEST_USER_A_ID`, `WASPADAI_DB_TEST_USER_B_ID` |
| `run_pgtap` | `supabase-development` | `MIGRATION_DATABASE_URL` |
| `run_staging_smoke` | `staging` | `STAGING_BASE_URL`, `STAGING_BEARER_USER_A`, `STAGING_BEARER_USER_B` |

`run_pgtap` memakai Docker pada GitHub runner, bukan pada laptop anggota tim.
`run_staging_smoke` menguji readiness, verifikasi teks mock, idempotency replay,
history/detail milik owner, dan respons 404 bagi non-owner. Token A/B harus berasal
dari dua akun Supabase test berbeda, berumur pendek, dan tidak boleh dicetak pada log.
Android tetap memakai API AI publik; workflow ini tidak mengubah base URL Android.
