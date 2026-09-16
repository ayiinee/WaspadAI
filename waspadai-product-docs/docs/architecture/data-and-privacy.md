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

