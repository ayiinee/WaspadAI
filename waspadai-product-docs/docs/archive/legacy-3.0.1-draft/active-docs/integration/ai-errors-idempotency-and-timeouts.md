# AI Errors, Idempotency, dan Timeouts

[Kembali ke indeks integrasi AI](ai-service.md)

## 8. Error envelope dan retry

```json
{
  "error": {
    "code": "AI_TIMEOUT",
    "message": "Pemeriksaan belum memberikan respons. Coba lagi setelah status dipastikan.",
    "request_id": "b683ece9-2d46-4df6-9dac-a9de609af723",
    "retryable": false,
    "retry_after_seconds": null
  }
}
```

| HTTP | Product code | Perilaku |
|---|---|---|
| 400 | INVALID_REQUEST | Perbaiki input |
| 401 | INVALID_ACCESS_TOKEN | Refresh token satu kali; ulang dengan key sama |
| 403 | FORBIDDEN / OWNER_CANNOT_VOTE / CASE_LOCKED | Tidak retry |
| 404 | CASE_NOT_FOUND / NOT_FOUND | Tidak membocorkan private resource |
| 409 | IDEMPOTENCY_CONFLICT / REQUEST_IN_PROGRESS / UNKNOWN_OUTCOME / PREVIEW_EXPIRED / REVISION_CONFLICT | Baca state, jangan inferensi baru otomatis |
| 413 | PAYLOAD_TOO_LARGE | Crop/kompres |
| 415 | UNSUPPORTED_MEDIA_TYPE | Gunakan format yang didukung |
| 422 | VALIDATION_ERROR | Safe field message tanpa raw input |
| 429 | RATE_LIMITED | Hormati Retry-After; hanya retry aman sesuai outcome |
| 502 | AI_INVALID_RESPONSE / FACT_CHECK_UPSTREAM_FAILURE | Schema/provider gagal, bukan verdict |
| 503 | SERVICE_UNAVAILABLE / AI_AUTH_FAILED / PERSISTENCE_UNAVAILABLE | Gangguan service/config; jangan meminta user memperbaiki internal key |
| 504 | AI_TIMEOUT | Jika outcome unknown, retryable false sampai rekonsiliasi |

AI 401/403 berarti service key salah/akses internal ditolak; **bukan** token Supabase user expired. Jangan mengirim 401 user yang memicu refresh loop. AI 429 sebelum work dapat diberi retry hint; read timeout/network reset sesudah request terkirim berpotensi sudah dieksekusi. Error debug body tidak diteruskan.

## 9. Idempotency dan exactly-once limitation

1. Setelah auth/input, bentuk hash dari canonical payload efektif: trim text, optional defaults, sender, output BOTH; image bytes hash + question. Jangan gunakan random filename sebagai hash.
2. Unique `(user,route,key)` claim operation dalam transaksi pendek. Key reused dengan hash berbeda →409.
3. Jika COMPLETED dan cache tersedia, replay response dengan case ID yang sama; current request dapat diberi header replay, ID original tetap traceable.
4. Jika PROCESSING →409 REQUEST_IN_PROGRESS dengan retry-after singkat; tidak memanggil AI lagi.
5. Jika connection gagal sebelum satu byte request terkirim dan client dapat memastikan itu, satu reconnect retry boleh dilakukan dalam deadline. Jangan retry read/write/reset unknown hanya karena Product mempunyai key.
6. Read timeout setelah dispatch →UNKNOWN_OUTCOME. Key lokal **tidak** membuat AI endpoint idempotent. Hindari automatic retry; pengguna/operator harus mendapat status jelas.
7. Jika AI valid tetapi persistence gagal, cache aman hasil untuk retry persistence-only dengan key yang sama. Jika DB/cache sama-sama gagal dan proses mati, hasil mungkin tidak dapat dipulihkan; jangan menjanjikan exactly-once atau recovery sempurna.
8. Cache terminal dibersihkan setelah 10 menit sebagai proposal; repeated request setelah expiry tidak dijamin dedup. UI jangan auto retry lama tanpa batas. Retention/deletion menginvalidasi cached private result agar data yang dihapus tidak muncul dari replay.

## 10. Connection strategy dan time budget

Satu `httpx.AsyncClient` pada lifespan, ditutup saat shutdown. Fixed base URL dari environment, header key server-only, TLS verification aktif; no arbitrary target dari request client.

| Parameter draft | Nilai |
|---|---:|
| Connect timeout | 5 detik |
| Pool timeout | 5 detik |
| Write timeout | 30 detik |
| Read timeout | 120 detik |
| Absolute AI call deadline | 120 detik termasuk retry/connect/write/read |
| Product request deadline | 135 detik |
| Reverse proxy timeout | 145 detik |
| Android call timeout | 150 detik |
| HTTP max connections / keepalive | 10 / 5 |
| In-flight inference per Product instance | 4 awal; sesuaikan kapasitas AI |

Timeout phase HTTPX bukan total deadline; implementasikan deadline outer, bukan menganggap read=120 membatasi seluruh request. Angka ini PROPOSED karena client 120s lama berpotensi memotong request sebelum AI 120s selesai dan database commit. Pastikan hosting/load balancer benar-benar mengizinkan budget; jika tidak, desain async job perlu perubahan kontrak, tidak disisipkan sepihak.

Rate limiter user/IP yang tidak menyimpan PII berlebihan, bounded semaphore, queue wait terbatas. Circuit breaker draft buka 30 detik setelah 5 transient failures berturut; half-open satu probe aman. Health GET bukan request inference yang ditagih. Jangan menganggap success health memverifikasi key/model/search.
