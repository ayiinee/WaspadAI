# User Flows

Status: `CURRENT`, dengan bagian future ditandai eksplisit.

## Login dan akses fitur

1. Pengguna login melalui Supabase SDK di Android.
2. Android memastikan session aktif sebelum membuka aksi verifikasi.
3. Session digunakan untuk kontrol akses aplikasi. Token tidak dikirim ke public WaspadAI API.
4. Jika session tidak aktif, pengguna diarahkan kembali ke login.

## Verifikasi teks

1. Pengguna menempel atau mengetik teks, opsional menambahkan pertanyaan dan source URL.
2. Android memvalidasi field sesuai kontrak.
3. Android mengirim JSON langsung ke `/api/v1/verify/text` dengan `output_mode=BOTH`.
4. UI mempertahankan satu loading state hingga respons atau timeout 120 detik.
5. Android menampilkan `presentation.narrative.text` sebagai jawaban utama.
6. Evidence, sources, actions, uncertainty, dan dimensions tersedia sebagai detail bertingkat.

Setiap submit adalah pemeriksaan baru; fitur ini bukan chat multi-turn.

## Verifikasi screenshot

1. Pengguna memilih/capture screenshot melalui flow Android.
2. Android menampilkan preview dan crop.
3. Android memvalidasi format, ukuran, dimensi, serta keterbacaan hasil kompresi.
4. Android mengirim file sebagai multipart field `image`, bukan Base64 JSON.
5. Hasil ditampilkan dengan pola yang sama seperti verifikasi teks.

## Failure flow

- Input invalid: tetap di form dan tampilkan pesan field yang ramah.
- Offline/connection failure: pertahankan input dan tawarkan retry manual.
- Timeout: jelaskan bahwa hasil belum diterima; jangan mengklaim pemeriksaan gagal secara faktual.
- `413`: minta crop atau kompres ulang.
- `415`: minta JPG, PNG, atau WEBP.
- `429`/`5xx`: tawarkan coba lagi sesuai [error policy](../api/errors-and-resilience.md).

## Flow future: history dan community

Flow ini belum tersedia pada MVP. Setelah Product Backend current, hasil tertentu dapat disimpan ke history dan dipublikasikan hanya setelah preview redaksi server-side serta consent eksplisit. Detail rancangan berada di [Future Product API](../api/future-product-api.md); UI tidak boleh mengaktifkannya sebelum endpoint, auth, storage, dan test tersedia.

