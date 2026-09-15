# AI Text dan Image Requests

[Kembali ke indeks integrasi AI](ai-service.md)

## 4. Request text dan mapping

Android mengirim `POST /api/v1/verifications/text`, JSON, Bearer token, `Idempotency-Key` UUID.

```json
{
  "text": "Pesan mengaku petugas bank dan meminta kode OTP segera.",
  "question": "Apakah tindakan yang diminta aman?",
  "source_url": null,
  "sender_context": "UNKNOWN_NUMBER"
}
```

| Field | Validasi Product | Pemetaan ke AI |
|---|---|---|
| text | Wajib; trim; 10–25.000 karakter; reject blank | text |
| question | Opsional, bukan null; max500 pada request teks | question atau omitted agar default upstream |
| page_context | Opsional/null; title≤300, before/after≤500, masing-masing nullable; tanpa field tambahan | page_context |
| source_url | Opsional/null; max 2.048, HTTP(S), public URL | source_url |
| sender_context | Default UNKNOWN; enum di bawah | sender_context |
| output_mode | Tidak diterima sebagai pilihan client | Product menetapkan BOTH |
| user_id/role/save_history | Tidak diterima dari client | Tidak dikirim |
| Authorization/Idempotency-Key | Diproses Product | Tidak dianggap didukung AI; tidak diteruskan untuk menjanjikan dedup |

Sender enum reported: NOT_APPLICABLE, UNKNOWN_NUMBER, KNOWN_CONTACT, FORWARDED, SOCIAL_MEDIA, UNKNOWN. Nilai bebas seperti “Mengaku petugas bank” tidak dipakai pada field enum; masukkan konteks relevan ke text/question jika perlu, bukan mengubah schema.

Teks yang hanya URL dapat valid bila memenuhi aturan. Tolak localhost/loopback/link-local/private/reserved/internal destination sesuai threat model. Validasi fetch lengkap termasuk DNS rebinding/redirect berada pada service yang benar-benar membuka URL. Product tidak mengunduh arbitrary URL pada saat forwarding.

## 5. Request image dan OCR boundary

Android `POST /api/v1/verifications/image` menggunakan multipart:

| Field | Status | Catatan |
|---|---|---|
| image | Wajib | Binary JPEG/JPG, PNG, WEBP |
| question | Opsional | Max 500 karakter |
| output_mode | Server-only | Product menambah BOTH saat call AI |

Reported limit: maksimal 8 MB, masing-masing sisi minimal 64 dan maksimal 6.000 piksel, total ≤30.000.000 pixel. **OPEN satuan MB:** draft Product konservatif 8.000.000 bytes sampai upstream mengonfirmasi apakah 8 MiB. Jangan menganggap content-length cukup: batasi stream bytes, MIME signature, actual decoding dan decompression bomb. Proxy body limit sedikit di atas batas image untuk multipart overhead, sementara validator image tetap menolak file di atas cap.

Filename tidak menjadi path filesystem server. Streaming/temp file dibatasi; tempfile dihapus pada semua jalur. Strip EXIF/metadata, jangan menyimpan input tanpa consent. Tidak kirim Base64 dalam JSON.

**Batas draft storage:** kontrak verification saat ini belum menerima consent penyimpanan screenshot. Karena itu `STORE_SCREENSHOTS_ENABLED` harus tetap false pada baseline runnable. Kebijakan opt-in ≤24 jam dalam PRD/database adalah rancangan yang dipertahankan; sebelum diaktifkan, tim harus menambahkan transport consent/version yang eksplisit ke kontrak atau endpoint asset terpisah, memeriksa binding consent ke image digest, dan menambah test. Flag server saja tidak pernah dianggap consent pengguna.

`ocr_text`, `source_url`, `sender_context` untuk image **tidak didokumentasikan sebagai field upstream pada lampiran yang tersedia**. Draft Product image tidak menerima field tambahan tersebut. OCR Android tetap berguna untuk review/koreksi atau jalur text terpisah. Menambahkan metadata image membutuhkan versi kontrak baru yang disepakati, bukan menjejalkannya ke request atau prompt tersembunyi.
