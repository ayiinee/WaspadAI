# WaspadAI — Product API and Remote AI Integration

Versi 3.0.1-draft. Batas Product–AI DECIDED; wire schema AI VERIFIED terhadap unggahan 0.7.0. Workflow Product tetap PROPOSED dan full commit/deployment belum diverifikasi.

| Urutan | Modul | Cakupan |
|---|---|---|
| 1 | [Contract dan ownership](ai-contract-and-ownership.md) | Provenance, release blocker, owner, auth, dan endpoint upstream |
| 2 | [Requests](ai-requests.md) | Mapping request text, image, OCR boundary, dan input validation |
| 3 | [Response dan Product API](ai-response-and-product-api.md) | Envelope, history policy, dan katalog Product API |
| 4 | [Errors, idempotency, timeouts](ai-errors-idempotency-and-timeouts.md) | Error mapping, retry safety, exactly-once limitation, dan budget koneksi |
| 5 | [Testing, indexing, dan open items](ai-testing-indexing-and-open-items.md) | Mock, contract test, community indexing, baseline AI, dan keputusan terbuka |

Kontrak Android direct-to-AI lama hanya bersifat historis. Sumber lengkap tersedia dalam arsip integrasi [3.0.0](../archive/monoliths/ai-service.v3.0.0-draft.md) dan [3.0.1](../archive/monoliths/ai-service.v3.0.1-draft.md).
