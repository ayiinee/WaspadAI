# Errors dan Resilience

Status: `CURRENT`.

## Error contract current

Public WaspadAI API mengikuti bentuk error FastAPI. Validasi umumnya berupa:

```json
{
  "detail": [
    {
      "type": "string_too_short",
      "loc": ["body", "text"],
      "msg": "String should have at least 10 characters"
    }
  ]
}
```

Android memetakan HTTP status ke pesan ramah dan menyimpan detail teknis hanya untuk debug non-sensitif. Jangan mengasumsikan envelope `{ "error": ... }` pada mode current.

| HTTP | Perilaku Android |
| --- | --- |
| `400`/`422` | Tampilkan masalah input/field |
| `401` | Tampilkan gangguan akses API; jangan otomatis refresh Supabase sebagai alur normal |
| `403`/`404`/`409` | Tampilkan alasan aman; jangan retry otomatis |
| `413` | Minta crop atau kompres image |
| `415` | Minta JPG, PNG, atau WEBP |
| `429` | Tunggu sesuai `Retry-After` bila tersedia; tawarkan retry |
| `502`/`503` | Tawarkan coba lagi kemudian |
| Network/timeout | Pertahankan input dan tawarkan retry manual |

## Timeout dan retry

- Pemeriksaan synchronous; timeout client yang disarankan 120 detik.
- `Idempotency-Key` boleh dikirim dengan UUID tetap untuk satu aksi, tetapi public API belum memprosesnya.
- Karena upstream belum menjamin idempotency, jangan melakukan retry otomatis berulang setelah request mungkin sudah terkirim.
- Retry manual menggunakan input yang sama dan selalu diperlakukan sebagai kemungkinan pemeriksaan baru.
- Jangan menjalankan dua submit paralel dari satu layar.

## Logging

Log yang diperbolehkan: request ID, endpoint, HTTP status, durasi, network class, build version, dan error category. Redact body teks, file/image bytes, token, API key, serta exception yang mengandung payload.

Error envelope standar Product Backend, refresh-on-401, dan exactly-once workflow hanya berlaku pada [future API](future-product-api.md).

