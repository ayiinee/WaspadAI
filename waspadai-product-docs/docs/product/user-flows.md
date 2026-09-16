# User Flows

Status: `CURRENT`, dengan flow yang belum diimplementasikan ditandai `TARGET`.

## Login dan verifikasi teks

1. Pengguna login melalui Supabase SDK.
2. Android mengirim JSON, Bearer access token, dan UUID `Idempotency-Key` ke `POST /api/v1/verifications/text`.
3. Product API memvalidasi token dan mengambil `sub` sebagai `user_id`.
4. Product API membentuk request internal dengan `output_mode=BOTH`; pada target community-assisted fact-check, backend menambahkan evidence eligible dari database.
5. Android menampilkan `result.presentation.narrative.text`, lalu detail evidence/source/action.
6. Jika `401`, Android refresh session dan retry paling banyak sekali dengan key yang sama.

Android tidak mengirim `output_mode` atau community evidence.

## Verifikasi screenshot (`TARGET`)

Android menampilkan preview/crop, lalu mengirim binary image sebagai multipart ke Product API. Product API meneruskan file sebagai binary, bukan Base64. Route Product image belum tersedia saat ini.

## History

List dan detail history bersifat owner-only. Default policy menyimpan hasil `UNVERIFIED` atau yang memerlukan human review; policy `ALL` menghasilkan `save_reason=ALL_POLICY`.

## Community (`TARGET`)

Kasus baru selalu `PRIVATE`. Publikasi memerlukan preview redaksi dan consent. Post `PUBLISHED_UNVERIFIED` dapat tampil di feed tetapi tidak boleh dipakai sebagai factual evidence. Hanya `VERIFIED_EVIDENCE` yang memenuhi seluruh gate consent/moderation/revision pada kontrak kanonik boleh dikirim ke AI.

