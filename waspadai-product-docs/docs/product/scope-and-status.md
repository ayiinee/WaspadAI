# Scope dan Status Implementasi

Status: `CURRENT`.

| Kapabilitas | Keputusan kontrak | Status kode saat ini |
| --- | --- | --- |
| Supabase Auth | Android mengirim Bearer token ke Product API | Auth validation tersedia |
| Verifikasi teks | `POST /api/v1/verifications/text`, `Idempotency-Key` wajib | Tersedia dalam mode `MOCK`; remote AI belum dipanggil |
| Verifikasi screenshot | `POST /api/v1/verifications/image` | `TARGET`, route belum diekspor |
| Response Product | Wrapper `history`, `result`, `execution_mode` | Tersedia pada text slice |
| History list/detail | Product API + database owner-only | Tersedia |
| Community preview/feed/vote | Product API + database/Storage | `TARGET`, belum tersedia |
| Contribution/moderation | Product API + moderator authorization | `TARGET`, schema database tersedia |
| Community evidence ke AI | Product memilih data eligible dan mengirim ke internal AI | `TARGET`, kedua sisi belum mendukung field |

Schema migration tidak sama dengan fitur runtime. Tabel community, consent, moderation, dan outbox sudah mendefinisikan state, tetapi service/API yang menegakkan lifecycle tersebut belum lengkap.

## Batas keamanan

- Android tidak memanggil public/internal WaspadAI secara langsung.
- Bearer Supabase hanya dikirim ke Product API.
- `X-Waspadai-API-Key`, database credential, dan service-role key tidak boleh berada di APK.
- WaspadAI tidak menerima credential Supabase dan tidak mengakses database Product.
- Community evidence kosong adalah kondisi valid; backend tidak boleh menurunkan gate agar mendapatkan hasil.

