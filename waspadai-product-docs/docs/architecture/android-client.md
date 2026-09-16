# Android Client Architecture

Status: `CURRENT`.

Android bertanggung jawab atas Supabase session, form/crop, validasi awal, call Product API, rendering state, dan pesan error. Android tidak membaca database, menyusun community evidence, memvalidasi moderation, atau menyimpan secret server.

## Network contract

| Konfigurasi | Nilai |
| --- | --- |
| Base URL | Product API dari build configuration |
| Text path | `/api/v1/verifications/text` |
| Image path | `/api/v1/verifications/image` (`TARGET`) |
| Auth | `Authorization: Bearer <supabase_access_token>` |
| Idempotency | UUID v4 wajib per aksi; gunakan ulang saat retry aksi yang sama |
| Android timeout | 150 detik |
| Response | Wrapper `history`, `result`, `execution_mode` |

Text request tidak memiliki `output_mode`; unknown field ditolak. `page_context` opsional mendukung `title`, `before`, dan `after` sesuai kontrak kanonik.

Tidak boleh ada `X-Waspadai-API-Key`, Supabase service-role key, database URL/password, atau refresh token pada request Product API.

Gunakan state `Idle`, `Validating`, `Submitting`, `Success`, dan `Failure`. Hasil utama dibaca dari `result.presentation.narrative.text`; client tidak menghitung ulang verdict.

