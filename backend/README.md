# Product Backend

Status: `CURRENT` sebagai boundary Android–AI; remote AI dan community evidence masih `TARGET`.

Vertical slice yang tersedia: `GET /api/health`, `GET /api/ready`, `POST /api/v1/verifications/text`, `GET /api/v1/history`, dan `GET /api/v1/history/{case_id}`. Verification text memerlukan Bearer Supabase, role database `product_app`, UUID `Idempotency-Key`, dan saat ini menghasilkan fixture `MOCK`.

Keputusan Android memakai Product API sudah berlaku, tetapi integrasi end-to-end tidak boleh diklaim live sampai Android adapter dan remote AI mode tersedia serta diuji. Endpoint image, community, contribution/moderation, query community evidence, dan pengiriman field tersebut ke WaspadAI belum diimplementasikan.

```powershell
uv sync --locked
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload --loop app.uvicorn_loop:selector_loop_factory
uv run pytest
```

Konfigurasi dibaca dari `.env` root. Untuk runtime saat ini, siapkan `DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, dan `HISTORY_CURSOR_SIGNING_KEY`. Gunakan role `product_app` pada `DATABASE_URL`; jangan memakai password migrator atau service-role sebagai credential umum runtime.

Production mewajibkan `AI_SERVICE_MODE=remote`, tetapi konfigurasi saja tidak cukup: adapter remote dan community payload harus mengikuti [`../android-api-contract.md`](../android-api-contract.md), exported schema, serta contract test lintas repository.

## Staging dan quality gates

| Workflow input | Environment | Secret |
| --- | --- | --- |
| `run_live_db_tests` | `supabase-development` | `PRODUCT_APP_DATABASE_URL`, test user A/B |
| `run_pgtap` | `supabase-development` | `MIGRATION_DATABASE_URL` |
| `run_staging_smoke` | `staging` | `STAGING_BASE_URL`, Bearer user A/B |

Smoke saat ini menguji readiness, verification text mock, idempotency replay, history owner-only, dan cross-user 404. Token test harus berumur pendek dan tidak dicetak. Mode `MOCK` selalu diberi label jujur melalui `execution_mode`.
