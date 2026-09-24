# Data, Security, dan Privacy

Status: `CURRENT`.

## Data flow

Android mengirim input dan Bearer token ke Product API. Product API memvalidasi pengguna, menyimpan data sesuai policy, dan mengirim input fact-check ke internal WaspadAI. Pada target community-assisted fact-check, Product API membaca database lalu mengirim hanya proyeksi sanitized yang lulus seluruh gate kontrak.

| Data/credential | Pemilik | Boleh ke WaspadAI? |
| --- | --- | --- |
| Supabase access/refresh token | Android/Product Auth boundary | Tidak |
| `user_id`, owner/moderator/voter identity | Product | Tidak |
| Database URL/service-role key | Server/operator | Tidak |
| `X-Waspadai-API-Key` | Product Backend | Ya, hanya sebagai header internal |
| Input fact-check | Pengguna/Product | Ya, sesuai consent/policy |
| Community evidence sanitized | Product | Ya, hanya jika eligible dan `RAG_REUSE` aktif |
| Screenshot/path Storage privat | Product | Tidak sebagai community evidence |

Kasus baru privat. `COMMUNITY_PUBLICATION` tidak sama dengan `RAG_REUSE`; keduanya harus diperiksa terpisah. Withdrawal, revocation, expiry, deletion, retraction, dan revision baru membatalkan pemakaian record lama.

## Context capture

- Screenshot assistant, hasil crop, teks layar, share attachment, dan quick capture hanya berada di memori sebelum submit; tidak ditulis ke disk, backup, WorkManager, analytics, atau log.
- Tidak ada upload saat state `WaitingForContext` atau `Previewing`. Upload hanya dimulai dari aksi eksplisit **Periksa sekarang**.
- Screenshot dan percakapan follow-up assistant dibersihkan ketika session ditutup, disembunyikan, atau dihentikan oleh lock screen.
- Quick capture membuat satu `MediaProjection` untuk satu capture lalu melepaskan virtual display, `ImageReader`, dan projection.
- `FLAG_SECURE`, capture kosong, URI tidak terbaca, MIME palsu, file kosong, ukuran di atas 8 MB, dan jumlah attachment di atas lima gagal secara aman dan menawarkan fallback.
- Tidak ada `RECORD_AUDIO`; recognition-service hanya placeholder framework dan selalu mengembalikan error client.

