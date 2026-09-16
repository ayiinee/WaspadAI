# Development Setup

Status: `CURRENT`.

## Android

Gunakan Android Studio/JDK/SDK sesuai Gradle project. Konfigurasi client hanya memuat Supabase URL, publishable key, dan Product API base URL. Jangan menaruh `X-Waspadai-API-Key`, service-role key, atau database credential di APK.

## Product Backend

Backend Python 3.11 membaca `.env` root. Untuk vertical slice saat ini diperlukan `DATABASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, dan `HISTORY_CURSOR_SIGNING_KEY`. Production wajib memakai `AI_SERVICE_MODE=remote`, tetapi remote adapter harus benar-benar tersedia dan lulus test sebelum deployment.

```powershell
Set-Location backend
py -m uv run pytest
py -m uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --loop app.uvicorn_loop:selector_loop_factory
```

Validasi `GET /api/ready`, lalu uji `POST /api/v1/verifications/text` dengan Bearer token dan `Idempotency-Key`. Response saat mode mock wajib berlabel `execution_mode=MOCK` dan tidak boleh dipresentasikan sebagai hasil AI live.

## Database

Migration di `supabase/migrations/` adalah sumber bentuk schema. Gunakan role migrator hanya untuk migration dan role `product_app` untuk runtime. Jangan commit `.env`, token, password, keystore, screenshot, atau payload production.

