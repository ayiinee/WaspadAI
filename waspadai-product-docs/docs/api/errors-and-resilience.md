# Errors dan Resilience

Status: `CURRENT`.

Product API mengirim error aman berbentuk:

```json
{
  "error": {
    "code": "INVALID_ACCESS_TOKEN",
    "message": "Sesi tidak valid atau sudah berakhir.",
    "request_id": "c9602d76-69a1-45ee-85f4-da42f9c9f08f",
    "retryable": false,
    "retry_after_seconds": null
  }
}
```

- `401`: refresh Supabase session lalu retry paling banyak sekali.
- `409`: jangan membuat aksi baru; gunakan state/key sesuai error.
- `422`: perbaiki field input.
- `429`: hormati `Retry-After`.
- `502`/`503`/timeout: pertahankan input dan tampilkan retry aman.

Android timeout 150 detik; Product Backend memberi upstream AI deadline 120 detik. Retry aksi yang sama wajib memakai UUID `Idempotency-Key` yang sama. Log hanya metadata non-sensitif dan tidak memuat token, body, screenshot, community text, atau secret.

