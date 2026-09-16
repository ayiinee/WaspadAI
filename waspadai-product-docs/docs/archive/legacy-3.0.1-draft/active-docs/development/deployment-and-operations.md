# Deployment dan Operations

[Kembali ke indeks setup](setup.md)

## 14. Deployment topology

Product deployment: reverse proxy→Product API, web opsional, worker, Supabase managed. AI deployment milik Shafwan terpisah. Jika satu VPS, hubungkan service melalui loopback/private interface dan firewall yang disepakati; jangan publish port internal AI tanpa kebutuhan. Jika beda host, gunakan HTTPS/internal VPN/allowlist sesuai operator; key tetap wajib.

Product API dan worker dapat dijalankan sebagai proses Python 3.11 terpisah pada managed runtime atau service manager host, dari artifact kode/dependency yang versinya dipin. Gunakan user non-root, secret manager, temporary upload directory yang dibatasi dan dibersihkan, serta restart/health policy; tidak ada dependency AI lokal. Inference concurrency4/maxHTTP10 awal, DB pool terbatas. Proxy max request body9m sebagai contoh untuk image8.000.000+multipart; application limit tetap utama. Proxy timeout145s harus lebih besar Product135s; target hosting harus mendukung.

Urutan rilis:

1. Freeze contract/policy/config dan dapatkan candidate AI yang disepakati.
2. Backup DB, review expand-only migration, satu operator apply.
3. Seed learning content publik idempotent sesuai approval.
4. Deploy artifact Product berversi dengan secret staging/production masing-masing.
5. Run DB/health/auth/integration smoke dari jaringan Product.
6. Deploy Android candidate pointing Product staging; uji HP final.
7. Enable community workflows bertahap; indexing off sampai capability/tests selesai.
8. Run retention worker dan pastikan expiry sebelum storage opt-in diaktifkan.
9. Tag release dan catat Product version, migration head, AI full SHA/hash/API/model/rule versions.

## 15. Observability minimum

Structured logs: request_started, auth_validated, ai_request_started/completed/failed, history_persisted/failed, preview_created/published, moderation_decided, sync_requested/retry/completed, retention_cleanup. Field: timestamp, severity, route, request_id, AI request/trace ID bila aman, duration, safe error code. User ID dapat dipseudonimkan; jangan log input penuh, token, image, provider response debug, signed URL.

Metrics: request/error/latency per route, auth failures, upstream duration/status, schema invalid, concurrency saturation, DB pool wait, persistence failures, UNKNOWN_OUTCOME count, outbox backlog/oldest age/retries/deindex lag, expired assets not deleted. Alert berbasis nilai awal yang tim ukur; jangan mengarang SLO sudah tercapai.

Health `/api/health` liveness ringan tanpa memanggil provider. `/api/ready` minimal ready/degraded dengan detail internal terlindungi. AI down dapat membuat capability verification degraded sementara history tetap bisa dibaca; jangan mematikan seluruh proses karena health dependency flapping. Debug/prompt trace production off.

## 16. Troubleshooting

| Gejala | Diagnosis aman | Tindakan |
|---|---|---|
| HP gagal koneksi | Cek adb device/reverse dan base URL Product | Perbaiki routing debug, bukan disable TLS production |
| Semua Product401 | Cek issuer/audience/SDK session/clock | Refresh sekali; verifikasi project config |
| AI401/403 | Key/header/internal route/network | Operator rotasi/perbaiki secret server; jangan public fallback |
| AI504 | Deadline, dispatch outcome, provider latency | Tandai unknown, jangan blind inferensi ulang |
| History saved false | Policy NOT_REQUIRED vsSAVE_FAILED | Tampilkan alasan; persistence-only retry bila hasil cached |
| Cross-user read | Runtime role/grants/claims LOCAL/owner filter | Stop release, perbaiki dan negative test |
| Quiz key bocor | Data API/table grants/projection | Revoke raw access, gunakan safe DTO/view |
| Vote counts ganda | Unique/upsert transaction | Reconcile counts, regression concurrency |
| Community index stale | Outbox revision/ack/retract worker | Sembunyikan canonical, deindex/reconcile; disable unsafe retrieval |
| Asset lewat24h | Cleanup job/permission/storage API | Disable new opt-in sampai deletion berhasil |

## 17. Rollback dan incident minimum

Product rollback ke artifact kode/dependency berversi sebelumnya, bukan menghapus DB. Gunakan expand/contract migration; destructive down migration bukan rollback default. AI rollback dikelola Shafwan sendiri selama kontrak compatible. Feature flags dapat menonaktifkan publikasi/index/storage baru tanpa menghapus data.

Jika key bocor: batasi akses, tambah key baru AI, update Product secret, smoke, cabut key lama, periksa log tanpa menyebarkan credential. Jika data bocor: hentikan jalur terdampak, batasi akses, pertahankan audit minimum, koordinasi operator dan pemilik kebijakan; paket ini bukan prosedur hukum final.

Jika outcome inference tak diketahui: jangan restart worker untuk mengulang semua operasi secara buta. Rekonsiliasi berdasarkan ID/capability upstream; tanpa status API, laporkan keterbatasan dan biarkan pengguna memulai aksi baru secara sadar bila dipilih.
