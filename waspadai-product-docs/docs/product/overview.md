# Product Overview

Status: `CURRENT` untuk keputusan arsitektur; status implementasi dirinci terpisah.

WaspadAI membantu pengguna memeriksa pesan, klaim, URL, dan screenshot mencurigakan. Hasil adalah dukungan keputusan, bukan jaminan kebenaran.

## Sasaran produk

- Pengguna login melalui Supabase sebelum menggunakan pemeriksaan.
- Android memanggil Product API dengan Bearer access token dan `Idempotency-Key`.
- Product API memvalidasi identity, mengelola state/history, dan memanggil internal WaspadAI tanpa membocorkan secret ke APK.
- Product API dapat memperkaya request dengan community evidence yang eligible dan sanitized dari database Product.
- Hasil utama berasal dari `result.presentation.narrative.text`; detail ditampilkan bertingkat.

## Prinsip

1. **Safety before certainty.** `UNVERIFIED` tidak berarti aman.
2. **Server-owned authorization.** Ownership, consent, moderation, dan database state tidak dipercaya dari client.
3. **Private by default.** Publikasi community memerlukan redaction dan consent eksplisit.
4. **No circular evidence.** Vote dan hasil AI lama bukan bukti faktual baru.
5. **Honest capability status.** Contract target tidak boleh diklaim sudah runtime tanpa kode dan test.

Saat ini backend baru memiliki vertical slice text `MOCK` dan history owner-only. Remote AI, image, community, moderation, dan pengiriman community evidence masih target. Lihat [scope dan status](scope-and-status.md).

