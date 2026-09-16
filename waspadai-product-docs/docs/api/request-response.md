# Request dan Response Product API

Status: `CURRENT` untuk text; image/community ditandai `TARGET` pada kontrak kanonik.

## Text verification

```http
POST /api/v1/verifications/text
Authorization: Bearer <supabase_access_token>
Idempotency-Key: <uuid-v4>
Content-Type: application/json
```

```json
{
  "text": "Pesan mengaku dari bank dan meminta OTP agar akun tidak diblokir.",
  "question": "Apakah pesan ini aman?",
  "source_url": null,
  "sender_context": "UNKNOWN_NUMBER",
  "page_context": null
}
```

`text` berukuran 10–25.000 karakter. `question` maksimal 500, `source_url` maksimal 2.048 dan harus public HTTP(S), sedangkan `page_context` opsional berisi `title` maksimal 300 serta `before`/`after` maksimal 500 karakter. Unknown field, termasuk `output_mode`, ditolak.

## Success response

```json
{
  "request_id": "c9602d76-69a1-45ee-85f4-da42f9c9f08f",
  "status": "COMPLETED",
  "history": {
    "saved": true,
    "case_id": "56f50192-7dd1-4bec-9a52-d838174c9d23",
    "save_reason": "UNVERIFIED",
    "community_eligible": true,
    "community_state": "PRIVATE"
  },
  "result": {
    "request_id": "req_upstream_example",
    "status": "COMPLETED",
    "mode": "LIVE",
    "verdict": "UNVERIFIED",
    "headline": "Bukti belum cukup untuk memastikan klaim",
    "presentation": {
      "narrative": {
        "text": "Bukti belum cukup; periksa sumber resmi."
      }
    }
  },
  "execution_mode": "MOCK"
}
```

Contoh di atas memperlihatkan struktur; object `result` runtime berisi seluruh field AI yang diwajibkan kontrak kanonik. Android membaca naratif dari `result.presentation.narrative.text`.

