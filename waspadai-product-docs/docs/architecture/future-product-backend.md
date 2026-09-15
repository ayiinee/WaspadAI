# Future Product Backend

Status: `FUTURE` — belum berada pada jalur verifikasi MVP.

## Kapan backend diperlukan

Product Backend diperlukan ketika produk harus menegakkan autentikasi/ownership server-side, menyimpan history, mengelola private Storage, melakukan redaksi publikasi, menyediakan community/vote, atau menjalankan moderation.

Pada fase tersebut, topology berubah menjadi Android→Product Backend→internal WaspadAI API. Perubahan ini adalah migration arsitektur dan contract, bukan sekadar mengganti base URL.

## Tanggung jawab future

- validasi Bearer access token Supabase dan mengambil `sub` sebagai `user_id` terpercaya;
- menyimpan history sesuai policy yang disepakati;
- memakai private bucket dan signed URL berumur pendek;
- menjalankan preview/redaksi, consent, ownership, vote, dan moderation;
- menyimpan `X-Waspadai-API-Key` hanya pada server;
- memanggil `/api/internal/v1/verify/text|image` dengan `output_mode=BOTH`;
- menyeragamkan error envelope, idempotency, audit, dan observability.

## Exit criteria untuk menjadi current

1. ADR migration disetujui owner Android, Product Backend, AI, dan Product.
2. OpenAPI Product direview dan ditandai current.
3. Endpoint diimplementasikan dan exported schema cocok dengan kontrak.
4. Supabase auth validation, RLS, ownership, consent, serta retention lulus test.
5. Internal API key diserahkan melalui secret manager dan diuji di staging.
6. Android migration, rollback, dan backward compatibility disepakati.
7. Dokumentasi current diubah dalam release yang sama.

Draft lama tersedia di [`contracts/future/product-api.openapi.yaml`](../../contracts/future/product-api.openapi.yaml). Draft itu tidak otomatis memenuhi exit criteria.

