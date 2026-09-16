# API Documentation

Status: `CURRENT`.

[`contracts/current/android-api-contract.md`](../../contracts/current/android-api-contract.md) adalah sumber kebenaran integrasi Android–Product–AI.

## Product API

| Method | Path | Status | Auth |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Implemented | Tidak ada |
| `GET` | `/api/ready` | Implemented | Tidak ada |
| `POST` | `/api/v1/verifications/text` | Implemented, `MOCK` | Bearer + `Idempotency-Key` |
| `POST` | `/api/v1/verifications/image` | `TARGET` | Bearer + `Idempotency-Key` |
| `GET` | `/api/v1/history` | Implemented | Bearer |
| `GET` | `/api/v1/history/{case_id}` | Implemented | Bearer |

Android tidak memakai public/internal WaspadAI secara langsung. Product Backend menambahkan `output_mode=BOTH` dan, setelah didukung kedua repository, `community_evidence` pada request internal.

## Anti-drift

- Product path menggunakan plural `/verifications/`.
- `Idempotency-Key` wajib untuk verification.
- Android text request tidak memiliki `output_mode` dan boleh memiliki `page_context`.
- Product response memakai wrapper `history`, `result`, `execution_mode`.
- Community evidence harus mengikuti gate Bagian 11 kontrak kanonik; tidak ada kontrak database terpisah.

