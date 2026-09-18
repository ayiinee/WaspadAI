# Product Backend

Status: `CURRENT` sebagai boundary Android–AI; verification remote dan community feed/vote tersedia partial runtime, sedangkan contribution/moderation dan community evidence masih `TARGET`.

Vertical slice yang tersedia: `GET /api/health`, `GET /api/ready`, `POST /api/v1/verifications/text`, `POST /api/v1/verifications/image`, `GET /api/v1/history`, `GET /api/v1/history/{case_id}`, feed/detail komunitas, vote klasifikasi, preview/publikasi, dan withdrawal komunitas. Verification text/image memerlukan Bearer Supabase, role database `product_app`, UUID `Idempotency-Key`, dan mode development masih menghasilkan fixture `MOCK`.

Keputusan Android memakai Product API sudah berlaku, tetapi integrasi end-to-end belum boleh diklaim live sampai adapter remote, migration Supabase, dan smoke test staging selesai. Contribution/moderation, query community evidence, dan pengiriman field tersebut ke WaspadAI belum diimplementasikan.

```powershell
uv sync --locked
uv run uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload --loop app.uvicorn_loop:selector_loop_factory
uv run pytest
```

Untuk testing di HP fisik, pakai IP LAN komputer sebagai `WASPADAI_API_BASE_URL`, misalnya `http://10.30.172.167:8001`, lalu rebuild dan reinstall APK debug.

Konfigurasi dibaca dari `.env` root. Untuk runtime saat ini, siapkan `DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, dan `HISTORY_CURSOR_SIGNING_KEY`. Gunakan role `product_app` pada `DATABASE_URL`; jangan memakai password migrator atau service-role sebagai credential umum runtime.

Production mewajibkan `AI_SERVICE_MODE=remote`, tetapi konfigurasi saja tidak cukup: adapter remote dan community payload harus mengikuti [`../android-api-contract.md`](../android-api-contract.md), exported schema, serta contract test lintas repository.

## Staging dan quality gates

| Workflow input | Environment | Secret |
| --- | --- | --- |
| `run_live_db_tests` | `supabase-development` | `PRODUCT_APP_DATABASE_URL`, test user A/B |
| `run_pgtap` | `supabase-development` | `MIGRATION_DATABASE_URL` |
| `run_staging_smoke` | `staging` | `STAGING_BASE_URL`, Bearer user A/B |

Smoke saat ini menguji readiness, verification text mock, idempotency replay, history owner-only, dan cross-user 404. Token test harus berumur pendek dan tidak dicetak. Mode `MOCK` selalu diberi label jujur melalui `execution_mode`.
