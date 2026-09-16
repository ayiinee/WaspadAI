# Product Backend Gateway

Status: `CURRENT` untuk peran arsitektur; sebagian capability masih `TARGET`.

Product Backend adalah boundary Android–AI. Backend memvalidasi Bearer token Supabase, mengambil `sub`, mengelola data Product, menyimpan internal AI key, dan menyeragamkan response/error Android.

## Sudah tersedia

- health/readiness;
- validasi Supabase token;
- `POST /api/v1/verifications/text` dengan mode `MOCK`;
- idempotency Product;
- persistence dan history owner-only.

## Target berikutnya

- remote adapter ke `/api/internal/v1/verify/text`;
- query community evidence yang relevan dan eligible;
- pengiriman `community_evidence` tanpa memberi WaspadAI akses database;
- image verification;
- preview, publication, feed, vote, contributions, moderation, dan cleanup.

Target tidak menjadi runtime hanya karena schema database atau draft OpenAPI tersedia. Exit criteria: route/schema exported, authorization dan RLS teruji, AI contract kompatibel, smoke staging lulus, serta rollback tersedia.

