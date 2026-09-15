# Data, Security, dan Privacy

Status: `CURRENT`, dengan policy backend ditandai future.

## Data flow current

Teks atau hasil crop screenshot dikirim dari perangkat ke public WaspadAI API untuk satu pemeriksaan synchronous. API current tidak mengikat hasil dengan akun Supabase dan tidak mengembalikan metadata history pengguna.

Android harus:

- mengirim hanya input yang diperlukan untuk pemeriksaan;
- menampilkan preview/crop sebelum upload gambar;
- menghindari log request body, screenshot, token, dan URL bertanda tangan;
- tidak menyimpan screenshot lebih lama dari kebutuhan UX kecuali ada consent dan requirement yang jelas;
- menghapus temporary crop/file sesuai lifecycle aplikasi;
- memakai HTTPS dan menolak konfigurasi cleartext production.

## Credential boundary

| Credential | Lokasi | Dikirim ke public AI? |
| --- | --- | --- |
| Supabase access/refresh token | secure client session | Tidak |
| Supabase publishable key | config client | Tidak diperlukan untuk call AI |
| Supabase service-role key | server only | Tidak pernah |
| `X-Waspadai-API-Key` | Product Backend future only | Tidak pernah dari Android |

## Future persistence

History/Storage/community membutuhkan Product Backend. Sebelum aktif, tim wajib menetapkan consent version, data classification, retention, deletion, redaction, signed URL TTL, audit policy, RLS/authorization, incident response, dan test pemisahan user A/user B.

Kasus baru harus privat secara default. Publikasi komunitas memerlukan preview hasil redaksi server-side dan consent eksplisit; vote tidak boleh mengubah verdict faktual secara otomatis.

