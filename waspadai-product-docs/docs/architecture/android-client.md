# Android Client Architecture

Status: `CURRENT`.

## Tanggung jawab

Android bertanggung jawab atas login/gating Supabase, pengambilan dan crop screenshot, validasi awal, call public AI, rendering state, dan pesan error ramah. Android tidak bertanggung jawab atas validasi server-side identity, history lintas perangkat, ownership, moderation, atau penyimpanan secret backend.

## Struktur yang disarankan

```text
app/
├── core/
│   ├── network/       # HTTP client, timeout, safe logging
│   ├── auth/          # Supabase session gate
│   └── ui/            # shared loading/error primitives
├── feature/verification/
│   ├── data/          # AI API DTO, multipart, repository implementation
│   ├── domain/        # use cases dan model presentasi
│   └── ui/            # form, preview/crop, result, detail
└── MainActivity.kt
```

Struktur aktual boleh berkembang berbeda, tetapi boundary berikut wajib dipertahankan:

- base URL berasal dari build configuration, bukan tersebar sebagai string;
- API interface hanya memuat public endpoint current;
- DTO wire terpisah dari UI model agar perubahan kontrak terlokalisasi;
- parsing toleran terhadap field opsional, tetapi field yang dibutuhkan UI divalidasi;
- log tidak memuat isi pesan, screenshot, token, atau response sensitif;
- tidak ada `X-Waspadai-API-Key`, service-role key, atau refresh token di APK.

## Network configuration

| Konfigurasi | Nilai current |
| --- | --- |
| Base URL | `https://waspadai.shafwan.digital` |
| Text path | `/api/v1/verify/text` |
| Image path | `/api/v1/verify/image` |
| Health path | `/api/health` |
| Auth header ke AI | Tidak ada |
| Output mode | `BOTH` |
| Client timeout | 120 detik |
| Idempotency key | Opsional; belum diproses upstream |

`401` dari public AI bukan sinyal normal untuk refresh Supabase. Refresh session hanya berkaitan dengan Supabase atau Product Backend future.

## UI state model

Gunakan state eksplisit: `Idle`, `Validating`, `Submitting`, `Success`, dan `Failure`. Hindari boolean terpisah yang dapat menghasilkan loading dan error secara bersamaan. Simpan input/crop lokal saat transient failure agar pengguna dapat retry tanpa mengulang pekerjaan.

Hasil utama berasal dari `presentation.narrative.text`. Jika narrative tidak tersedia atau respons tidak dapat diparse, tampilkan safe error dan catat metadata diagnostik non-sensitif; jangan membuat verdict client-side.

