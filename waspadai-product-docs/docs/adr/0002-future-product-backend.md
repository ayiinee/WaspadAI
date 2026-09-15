# ADR-0002: Product Backend sebagai Gateway Fase Lanjutan

- Status: Proposed
- Date: 2026-09-15
- Owners: Product owner, Backend lead

## Context

History, private Storage, ownership, community, voting, dan moderation tidak aman bila hanya ditegakkan di Android. Fitur tersebut memerlukan server yang memvalidasi identity dan memiliki credential internal.

## Proposed decision

Ketika fitur server-side diprioritaskan, Product Backend menjadi boundary Android–AI. Android mengirim Bearer token Supabase ke Product Backend. Backend memvalidasi token, menentukan `user_id`, mengelola data Product, lalu memanggil internal WaspadAI endpoint menggunakan server-only `X-Waspadai-API-Key`.

## Not effective yet

ADR ini tidak mengubah topology MVP dan tidak mengaktifkan endpoint draft. Promotion membutuhkan ADR accepted/migration plan, OpenAPI current, implementasi, security test, staging smoke, Android migration, rollback, serta pembaruan dokumentasi current.

## Open decisions

- policy penyimpanan history;
- exact Product API paths/versioning dan response envelope;
- vote method (`PUT` pada kontrak sumber vs `POST` pada draft legacy);
- retention, deletion, locked verified cases, dan consent version;
- idempotency/unknown outcome behavior;
- indexing/retraction contract untuk knowledge komunitas.

