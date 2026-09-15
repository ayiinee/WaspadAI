# WaspadAI — Setup, Testing, Deployment and Demo Runbook

Versi 3.0.1-draft. Semua perintah adalah target runbook setelah source/scaffold tersedia; paket dokumentasi sendiri belum membuat backend, Android, migration, worker, atau deployment berjalan.

| Urutan | Modul | Cakupan |
|---|---|---|
| 1 | [Prerequisites dan backend](prerequisites-and-backend-setup.md) | Versi tool, environment, repository, dan backend scaffold |
| 2 | [Database setup](database-setup.md) | Supabase hosted development, migration, role, dan connection strategy |
| 3 | [Auth, AI, dan clients](auth-ai-and-client-setup.md) | JWT, remote/mock AI, Android, physical device, dan web |
| 4 | [Testing dan CI](testing-and-ci.md) | Test matrix, CI, contract freeze, dan release gate teknis |
| 5 | [Deployment dan operations](deployment-and-operations.md) | Topologi, observability, troubleshooting, rollback, dan incident |
| 6 | [Demo dan release](demo-and-release.md) | Demo runbook, checklist final, dan referensi verifikasi |

Versi lengkap tersedia dalam arsip setup [3.0.0](../archive/monoliths/setup.v3.0.0-draft.md) dan [3.0.1](../archive/monoliths/setup.v3.0.1-draft.md).
