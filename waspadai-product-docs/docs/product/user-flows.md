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

## Verifikasi kontekstual melalui System Assistant

1. Pengguna membuka **Akses Cepat WaspadAI** dan menyetujui penggantian Digital Assistant melalui role dialog sistem.
2. Pengguna memanggil gesture assistant perangkat. Gesture dapat berupa long-press tombol samping, home-hold, atau corner swipe; aplikasi tidak menjanjikan satu gesture universal.
3. `VoiceInteractionSession` menerima screenshot dan/atau `AssistStructure`. WaspadAI tidak meminta mikrofon dan tidak berjalan dari lock screen.
4. Screenshot dapat di-crop dan teks dapat diedit di preview. Pada tahap ini belum ada request jaringan.
5. Setelah **Periksa sekarang**, Android mengirim input ke Product API dan menampilkan verdict, factual status, risk, evidence, source, uncertainty, safe action, human-review notice, dan disclaimer.
6. Pertanyaan lanjutan memakai crop atau teks yang sama selama session aktif. Menutup session membersihkan bitmap, byte array, teks, dan percakapan lokal.

Jika screenshot tidak diberikan oleh sistem, teks dari `AssistStructure` dipakai. Jika keduanya tidak tersedia, panel menawarkan pembukaan aplikasi untuk upload manual; pengguna juga dapat memakai Share Sheet atau tile **Periksa layar**. Secure window tidak dilewati.

## Fallback verifikasi

- **Share Sheet:** `ACTION_SEND` menerima teks atau satu gambar; `ACTION_SEND_MULTIPLE` menerima maksimal lima gambar. Semua konten dibaca ke memori dan masuk preview sebelum submit.
- **Quick Settings:** tile **Periksa layar** membuka consent MediaProjection. Capture bersifat one-shot; pada Android 14+ consent diminta untuk setiap capture.
- **Floating Verify:** fallback lanjutan, nonaktif secara default, dan hanya tersedia dari menu **Akses Cepat WaspadAI**.

Android mengirim binary image sebagai multipart ke Product API. Product API meneruskan file sebagai binary, bukan Base64.

## History

List dan detail history bersifat owner-only. Default policy menyimpan hasil `UNVERIFIED` atau yang memerlukan human review; policy `ALL` menghasilkan `save_reason=ALL_POLICY`.

## Community (`TARGET`)

Kasus baru selalu `PRIVATE`. Publikasi memerlukan preview redaksi dan consent. Post `PUBLISHED_UNVERIFIED` dapat tampil di feed tetapi tidak boleh dipakai sebagai factual evidence. Hanya `VERIFIED_EVIDENCE` yang memenuhi seluruh gate consent/moderation/revision pada kontrak kanonik boleh dikirim ke AI.

