# WaspadAI Product

Repository ini berisi scaffold aplikasi Android, Supabase, dan Product Backend future untuk WaspadAI.

## Arsitektur MVP saat ini

```text
Android Kotlin ──> Public WaspadAI API ──> fact-check pipeline
      │
      └── Supabase login diperiksa di aplikasi
```

Android memanggil public endpoint `/api/v1/verify/text` dan `/api/v1/verify/image` secara langsung. Token Supabase tidak dikirim ke WaspadAI API, response tidak memakai wrapper `result/history`, dan internal API key tidak boleh berada di APK.

[`android-api-contract.md`](android-api-contract.md) adalah source of truth untuk integrasi AI. Dokumentasi yang sudah dirapikan berada di [`waspadai-product-docs/`](waspadai-product-docs/README.md).

## Status komponen

| Komponen | Status baseline |
| --- | --- |
| `frontend/` | Android Compose scaffold; integrasi end-to-end belum selesai |
| `backend/` | Preview Product Backend future: health/readiness, mock verifikasi teks, dan history owner-only; bukan gateway MVP current |
| `supabase/` | Migration/tooling awal untuk fitur Product future |
| `contracts/` | Pin artifact dokumentasi Product |
| `waspadai-product-docs/` | Dokumentasi aktif, kontrak current/future/reference, dan archive |

History server-side, Storage, community, voting, ownership, dan moderation membutuhkan Product Backend fase berikutnya. Keberadaan scaffold atau draft OpenAPI bukan bukti fitur tersebut tersedia.

## Mulai development

Baca [`TEAM_SETUP.md`](TEAM_SETUP.md), lalu jalankan quality gate dokumentasi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\waspadai-product-docs\scripts\verify-docs.ps1
```

Jangan commit `.env`, keystore, token, screenshot pengguna, database credential, Supabase service-role key, atau `X-Waspadai-API-Key`.
