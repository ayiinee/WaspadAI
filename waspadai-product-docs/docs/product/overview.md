# Product Overview

Status: `CURRENT`.

## Ringkasan

WaspadAI membantu pengguna memeriksa pesan, klaim, URL, dan screenshot yang mencurigakan. Produk menyajikan narasi yang mudah dipahami, tingkat risiko, alasan, sumber, bukti, rekomendasi tindakan, serta ketidakpastian. Hasil merupakan dukungan keputusan, bukan jaminan kebenaran atau pengganti verifikasi manusia.

## Sasaran MVP

- Pengguna login melalui Supabase sebelum memakai fitur pemeriksaan.
- Pengguna dapat mengirim teks atau screenshot dari Android.
- Android memanggil public WaspadAI API secara langsung.
- Hasil utama memakai `presentation.narrative.text`; detail ditampilkan bertingkat.
- Pengalaman tetap jelas saat loading panjang, validasi gagal, jaringan terputus, rate limited, atau service error.
- Tidak ada secret server di APK dan tidak ada klaim fitur server-side yang belum tersedia.

## Prinsip produk

1. **Safety before certainty.** Ketidakpastian harus terlihat; `UNVERIFIED` bukan “aman”.
2. **Progressive disclosure.** Narasi dan tindakan tampil dahulu, evidence/detail dibuka saat dibutuhkan.
3. **No hidden persistence.** Input tidak dianggap menjadi history atau konten komunitas tanpa backend dan consent yang nyata.
4. **Clear system state.** Loading, success, validation error, timeout, dan offline harus berbeda secara visual dan semantik.
5. **Contract-driven delivery.** Wire behavior mengikuti [kontrak current](../../contracts/current/android-api-contract.md), bukan draft masa depan.

## Batas MVP

API AI publik bersifat stateless terhadap akun WaspadAI Product. Login Supabase adalah gate di aplikasi, bukan autentikasi yang divalidasi WaspadAI API. Karena belum ada Product Backend pada jalur verifikasi current, API publik tidak menyediakan history pengguna, ownership, community feed, vote, atau moderation.

Rincian fase ada di [scope dan status](scope-and-status.md).

