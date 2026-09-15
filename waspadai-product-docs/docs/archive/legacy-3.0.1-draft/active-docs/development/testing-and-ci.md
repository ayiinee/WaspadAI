# Testing dan CI

[Kembali ke indeks setup](setup.md)

## 12. Test strategy lengkap

| Lapisan | Cakupan | Gate |
|---|---|---|
| Unit Product | auth, roles, input, privacy projection, history policy, state machine, scoring | Setiap PR |
| AI adapter mock | field mapping, BOTH, image multipart, no unsupported OCR, unknown enums/error/deadline | Setiap PR |
| Contract | Product schema, examples, upstream hash/full pin, generated client compatibility | Setiap perubahan API |
| DB | PK/FK/check, migration, runtime role/RLS, claims isolation, atomic transactions | Project test terisolasi sebelum schema merge; belum otomatis pada baseline CI |
| Security | cross-user, self-role, quiz key leak, self-vote, path traversal, file bombs, secret logs | Sebelum merge/rilis |
| Workflow | idempotency concurrent/replay/unknown/persistence-only retry; preview stale; moderation race; outbox stale version | Sebelum rilis |
| Android | permissions, capture cleanup, network/session, history, learning/community | HP final |
| Remote AI | staging contract and known golden cases | Setelah upstream pin/config berubah |
| Operations | retention/storage cleanup, outbox retry/deindex, restart/pool drain, rollback | Sebelum feature aktif |

Contoh setelah tests diimplementasikan:

```bash
python -m pytest backend/tests/unit backend/tests/contract
python -m pytest backend/tests/integration backend/tests/security
# DB pgTAP/RLS dijalankan pada project test terisolasi dengan runner yang disepakati.
```

Negative scenarios wajib: key sama payload beda; process death setelah AI dispatch; DB commit gagal; terminal cache expired; delete history lalu replay; unknown dimension; upstream401/429; image valid header tetapi corrupt; preview PII; revoked RAG consent; quiz question dari modul lain; two-user connection reuse; object upload berhasil tetapi DB gagal; stale event UPSERT sesudah retract.

## 13. CI dan contract freeze

Baseline CI Product kini menjalankan Ruff/pytest Python 3.11, Android `testDebugUnitTest` pada SDK 36, dan gate dokumentasi versi yang terkunci. Repository dokumentasi menerbitkan ZIP beserta sidecar SHA-256 lewat tag `docs-v3.0.1-draft`; Product mengambil URL dan archive SHA yang dicatat pada `contracts/documentation-artifact.lock.json`, lalu memeriksa checksum kontrak/fixture dan menjalankan verifier dokumentasi dari `.artifacts/`. Job ini fail-closed sampai URL dan hash rilis nyata terisi. Job local database/container telah dihapus. Migration/RLS runtime belum diuji oleh CI; gate tersebut harus memakai project Supabase test terisolasi dan kredensial protected setelah alurnya dibekukan.

CI target: format/lint/typecheck→unit/contract→review SQL→Android build/test→web lint/typecheck/build bila ada→secret scan→versioned backend release artifact. Migration/RLS pada project Supabase test terisolasi adalah protected gate terpisah, bukan klaim dari parser lokal. Remote AI smoke tidak otomatis pada semua PR yang belum dipercaya karena credential/cost; protected environment job hanya trusted branch/operator.

Export OpenAPI dari FastAPI yang sudah ada melalui `backend/scripts/export_openapi.py`, bandingkan normalized schema dengan kontrak draft yang disepakati. Jika mismatch, review perubahan; jangan overwrite baseline otomatis tanpa diff. Upstream snapshot immutable per rilis, original bytes hash dicatat. Jangan menghasilkan Kotlin dari schema AI internal; hanya Product contract.

Release gate awal dari paket: status upstream UNVERIFIED dan critical OPEN items harus diselesaikan. Lolos parser/examples dalam paket ini bukan lulus runtime/remote contract.
