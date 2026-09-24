# Android Client Architecture

Status: `CURRENT`.

Android bertanggung jawab atas Supabase session, form/crop, validasi awal, call Product API, rendering state, dan pesan error. Android tidak membaca database, menyusun community evidence, memvalidasi moderation, atau menyimpan secret server.

## Network contract

| Konfigurasi | Nilai |
| --- | --- |
| Base URL | Product API dari build configuration |
| Text path | `/api/v1/verifications/text` |
| Image path | `/api/v1/verifications/image` |
| Auth | `Authorization: Bearer <supabase_access_token>` |
| Idempotency | UUID v4 wajib per aksi; gunakan ulang saat retry aksi yang sama |
| Android timeout | 150 detik |
| Response | Wrapper `history`, `result`, `execution_mode` |

Text request tidak memiliki `output_mode`; unknown field ditolak. `page_context` opsional mendukung `title`, `before`, dan `after` sesuai kontrak kanonik.

Tidak boleh ada `X-Waspadai-API-Key`, Supabase service-role key, database URL/password, atau refresh token pada request Product API.

Gunakan state `Idle`, `Validating`, `Submitting`, `Success`, dan `Failure`. Hasil utama dibaca dari `result.presentation.narrative.text`; client tidak menghitung ulang verdict.

## Context trigger layer

`core/assistant` memiliki `VoiceInteractionService` ringan di proses `:voice`, session service di proses aplikasi utama, extractor `AssistStructure`, crop view in-memory, dan state machine `WaitingForContext -> Previewing -> Submitting -> Result -> Failure/Closed`. Repository hanya dipanggil dari transisi konfirmasi pengguna.

`core/trigger` menormalkan origin menjadi `ASSISTANT`, `SHARE_SHEET`, `QUICK_SETTINGS`, `FLOATING_OVERLAY`, atau `IN_APP`. Origin tetap metadata client; kontrak Product API tidak berubah. Share dan Quick Settings memakai pending store one-shot in-memory untuk mengantar konten ke preview verifikasi.

Assistant memprioritaskan screenshot. Teks `AssistStructure` menjadi fallback dan dikirim sebagai `page_context` bila tersedia. Result mapper harus mempertahankan `CRITICAL`, verdict, factual status, evidence, sources, uncertainty, human-review flag, dan disclaimer; field opsional lama tetap aman.

Supabase access/refresh token dipersist secara terenkripsi menggunakan Android Keystore AES-GCM. File preference session dan remembered credentials dikecualikan dari backup serta device transfer. Logout membersihkan session, pending trigger, dan floating service.

