# API Documentation

Status: `CURRENT`.

## Source of truth

[`contracts/current/android-api-contract.md`](../../contracts/current/android-api-contract.md) adalah sumber kebenaran untuk integrasi Android–AI. Halaman di folder ini merangkum cara implementasi tanpa menggantikan kontrak tersebut.

## Current endpoints

Base URL: `https://waspadai.shafwan.digital`

| Method | Path | Consumer | Auth |
| --- | --- | --- | --- |
| `GET` | `/api/health` | Android/monitoring | Tidak ada |
| `POST` | `/api/v1/verify/text` | Android | Tidak ada Bearer/API key |
| `POST` | `/api/v1/verify/image` | Android | Tidak ada Bearer/API key |

Endpoint `/api/internal/v1/verify/*` bukan API Android. Endpoint tersebut hanya untuk Product Backend future dan membutuhkan `X-Waspadai-API-Key` server-side.

## Dokumen implementasi

- [Request dan response](request-response.md)
- [Errors dan resilience](errors-and-resilience.md)
- [Future Product API](future-product-api.md)
- [Contract registry](../../contracts/README.md)

## Aturan anti-drift

- Jangan memakai plural path `/api/v1/verifications/*` pada MVP current.
- Jangan mengirim `Authorization: Bearer` ke public WaspadAI API.
- Jangan membaca response dari `result.*`; response current tidak dibungkus.
- Jangan menganggap `history.*` atau `execution_mode` tersedia.
- Jangan menambahkan `page_context` atau field lain yang tidak ada di kontrak current.
- Gunakan `output_mode=BOTH` agar narrative dan structured data tersedia.

