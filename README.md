# WaspadAI Product

Repository ini berisi aplikasi Android, Product API FastAPI, migration Supabase, dan dokumentasi integrasi WaspadAI.

## Arsitektur yang berlaku

```text
Android ──Bearer Supabase──> Product API ──X-Waspadai-API-Key──> WaspadAI
                                  │
                                  └── Supabase Database/Storage
```

Android tidak memanggil WaspadAI secara langsung. Product API memvalidasi identity, mengelola state Product, mengambil community evidence eligible dari database, lalu—setelah integrasi remote tersedia—mengirim input dan proyeksi sanitized ke endpoint internal WaspadAI.

[`android-api-contract.md`](android-api-contract.md) adalah source of truth. Dokumentasi terstruktur berada di [`waspadai-product-docs/`](waspadai-product-docs/README.md).

## Status implementasi

| Komponen | Status |
| --- | --- |
| `frontend/` | Android Compose scaffold; integrasi Product API end-to-end belum selesai |
| `backend/` | Auth, health/readiness, text verification `MOCK`, idempotency, dan history owner-only tersedia |
| `supabase/` | Schema identity, verification, consent/assets, community, moderation/outbox, learning, RLS, dan Storage |
| Remote AI adapter | `TARGET`; backend belum memanggil WaspadAI live |
| Image/community/moderation API | `TARGET`; schema database bukan bukti endpoint runtime |
| Community evidence transport | `TARGET`; kontrak ada pada Bagian 11, belum didukung kedua service |

## Quality gate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\waspadai-product-docs\scripts\verify-docs.ps1
Set-Location backend
uv run pytest
```

Jangan commit `.env`, keystore, token, screenshot pengguna, database credential, Supabase service-role key, atau `X-Waspadai-API-Key`.
