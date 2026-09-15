# AI Contract Provenance dan Ownership

[Kembali ke indeks integrasi AI](ai-service.md)

## 1. Provenance dan release blocker

Percakapan melaporkan audit commit `b456057`, OpenAPI `0.7.0`; PRD v2.1 mengacu `a84ad57`; lampiran kontrak Android yang tersedia masih direct mode. Akses GitHub tidak berhasil karena web dinonaktifkan saat penyusunan. Kini pengguna menyediakan openapi.json; snapshot byte-identik disertakan sebagai waspadai-ai.openapi.json, API0.7.0/OpenAPI3.1.0. Full commit dan versi deployment belum diketahui.

Sebelum remote integration dianggap siap, AI engineer memberikan: full commit SHA (bukan 7 karakter saja), salinan `contracts/openapi.json` asli, versi API/model/rulebook bila tersedia, URL internal yang bisa dijangkau deployment Product, key melalui secret manager, batas request/error, dan capabilities yang benar-benar aktif. Tim menghitung SHA-256 file asli, mengisi `contracts/upstream/contract-lock.json`, lalu menguji schema/runtime.

Tautan referensi untuk tim, belum diverifikasi ulang: [repo AI](https://github.com/ShafwanAdhi/waspadAI-demo), [kontrak Android](https://github.com/ShafwanAdhi/waspadAI-demo/blob/main/docs/android-api-contract.md), [OpenAPI AI](https://github.com/ShafwanAdhi/waspadAI-demo/blob/main/contracts/openapi.json). `main` bukan pin rilis; ubah referensi release ke full SHA setelah mendapatkannya.

## 2. Ownership kontrak

| Boundary | Pemilik | Auth | Kontrak |
|---|---|---|---|
| Android/Web→Product | Tim Product | Bearer Supabase access token | product-api.openapi.yaml |
| Product→AI | Shafwan/AI | X-Waspadai-API-Key | upstream file asli |
| Product→Supabase | Tim Product | JWT context/restricted DB role | Migration + repository policy |
| AI→provider/index | AI engineer | Provider-specific server secrets | Di repo AI, bukan Product |

Product tidak mengirim user_id/email/role/JWT/refresh token ke AI. `X-Request-ID` dipakai hanya jika upstream mendukung/mentoleransi header korelasi; ID AI yang dikembalikan tetap dipertahankan. Product tidak menggunakan public AI endpoint sebagai fallback jika key internal gagal.

## 3. Endpoint AI yang dilaporkan tersedia

| Method | Path AI | Pengguna |
|---|---|---|
| GET | /api/health | Health, bukan jaminan pipeline/provider siap |
| POST | /api/internal/v1/verify/text | Product backend dengan internal key |
| POST | /api/internal/v1/verify/image | Product backend dengan internal key |
| POST | /api/v1/verify/text | Public demo AI lama; tidak dipakai Android/Product normal |
| POST | /api/v1/verify/image | Public demo AI lama; tidak dipakai Android/Product normal |

Gap terkonfirmasi pada file unggahan: header API key tertulis `required:false` dan securitySchemes tidak ada. File ini sendiri tidak membuktikan enforcement runtime; Product tetap wajib mengirim key sesuai keputusan integrasi. Jangan mengubah file snapshot asli untuk menutupi gap. Catat test requirement internal key di adapter/contract lock, dan minta upstream memperbaiki kontrak asli.
