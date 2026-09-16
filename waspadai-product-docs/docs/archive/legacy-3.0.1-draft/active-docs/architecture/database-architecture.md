# WaspadAI — Database Architecture, Dictionary, and Setup

Versi 3.0.1-draft. Status PROPOSED schema Product; belum merupakan migration yang dijalankan. Migration SQL yang direview menjadi sumber kebenaran setelah implementasi.

| Urutan | Modul | Cakupan |
|---|---|---|
| 1 | [Foundations dan relasi](database-foundations.md) | Keputusan dasar, konvensi, dan relasi inti |
| 2 | [Identity](database-identity.md) | Profile dan role |
| 3 | [Verification](database-verification.md) | Request operations, cases, result, evidence, dan rule match |
| 4 | [Asset dan community](database-assets-and-community.md) | Storage metadata, preview, consent, post, vote, contribution, dan moderation |
| 5 | [Learning](database-learning.md) | Module, lesson, quiz, answer, attempt, dan progress |
| 6 | [Outbox dan audit](database-outbox-and-audit.md) | Sinkronisasi community index dan audit log |
| 7 | [Access dan transactions](database-access-and-transactions.md) | RLS, grants, runtime role, dan consistency boundary |
| 8 | [Storage, migration, dan testing](database-storage-migrations-and-testing.md) | Retensi, urutan migration, setup, test, dan Definition of Done |

SQL pada lampiran lama tidak otomatis berlaku. Sumber lengkap tersedia dalam arsip database [3.0.0](../archive/monoliths/database-architecture.v3.0.0-draft.md) dan [3.0.1](../archive/monoliths/database-architecture.v3.0.1-draft.md).
