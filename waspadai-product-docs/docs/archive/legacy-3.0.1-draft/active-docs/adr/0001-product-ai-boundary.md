# ADR-0001 — Product dan AI sebagai repository dan deployment terpisah

Tanggal: 15 September 2026. Status batas layanan: DECIDED berdasarkan percakapan. Detail operasional: TARGET/PROPOSED. Tidak menyatakan telah disetujui melalui PR tim.

## Konteks

Lampiran folder lama memindahkan `apps/api` AI ke `backend`, `apps/web` ke `frontend/web`, dan memasukkan pipeline/RAG/provider ke Product. PRD v2.1 dan keputusan selanjutnya mengharuskan aplikasi cukup memanggil AI API. Tim memerlukan initial development tanpa dependency AI, sambil tetap mengelola Supabase JWT, history, learning, community dan moderation secara konsisten.

## Keputusan

1. Product repo boleh menjadi monorepo **Android + Product API + web opsional**, tetapi tidak mencakup implementasi AI. Istilah monorepo tidak otomatis berarti semua service digabung.
2. Android menggunakan Supabase Auth untuk sesi dan Product FastAPI untuk operasi bisnis. Akses tabel bisnis langsung dari client bukan jalur utama.
3. Product API memvalidasi access token, memeriksa role dan ownership, membatasi input, menerapkan idempotency, memanggil internal AI API dan menangani persistence.
4. AI Service menjalankan OCR backend, Vision, CaseContext, Rulebook, BM25, hashed-subword, planner, evidence retrieval, guardrail, assessment enam dimensi dan indeks AI.
5. Komunikasi Product→AI memakai internal HTTP API dengan `X-Waspadai-API-Key`, transport terlindungi dan konfigurasi server-only. JWT pengguna tidak diteruskan ke AI.
6. Tidak ada clone/install AI dalam quick start Product, submodule, subtree, vendor code, atau Python import lintas repo. Clone oleh AI engineer untuk pekerjaan AI sendiri tidak dilarang.
7. Product mempunyai OpenAPI sendiri; AI contract berasal dari upstream asli pada commit yang dipin. Tidak menghasilkan ulang kontrak AI hanya dari dugaan.
8. Supabase Product adalah sumber kebenaran state aplikasi, kontribusi dan consent. Canonical Rulebook berada di AI repo. Qdrant/indeks adalah turunan milik AI, bukan database transaksional Product.
9. Snapshot rule matches/provenance boleh disimpan Product tanpa menyalin corpus. Jangan membuat foreign key ke tabel rulebook yang hanya ada di layanan lain.
10. Community post belum terverifikasi tidak boleh masuk knowledge index. Jumlah vote tidak menetapkan kebenaran. Indexing/deindex melalui outbox hanya diaktifkan setelah AI menyediakan kontraknya.
11. Screenshot default memory-only. Penyimpanan sementara opt-in harus mempunyai consent terpisah, expiry maksimal 24 jam, bucket privat dan cleanup teruji.
12. Mock hanya development/test/demo berlabel eksplisit; bukan fallback otomatis saat remote gagal.

## Pembagian tanggung jawab

| Masalah | Product | AI |
|---|---|---|
| Login, JWT, role, user ownership | Pemilik | Tidak menerima session pengguna |
| Input review, consent, pembatasan upload | Validasi awal dan UI | Validasi ulang input di boundary AI |
| Redaksi PII | Sebelum persistence/log/forward bila relevan | Sebelum query/model/retrieval |
| Kesimpulan faktual, scam risk, evidence | Menampilkan, memvalidasi dan menyimpan sesuai policy | Menghasilkan dan menegakkan guardrail |
| Rulebook dan retrieval | Menyimpan ID/version snapshot bila dikirim | Pemilik corpus dan algoritma |
| History, community, learning | Pemilik state | Tidak menjadi database produk |
| Community RAG | Canonical record, consent, moderasi, outbox | Validasi event, embedding, index, filter, deindex |
| Deployment dan rollback | Rilis Product | Rilis AI independen |

## Alternatif yang ditinggalkan

- Android langsung ke public AI: tidak memenuhi keputusan gateway/JWT server-side/history dan dapat membingungkan client terhadap pemisahan kontrak.
- Memindahkan seluruh repo AI ke backend: menambah dependency, ownership dan beban release di luar kebutuhan Product.
- Product mengakses Qdrant langsung: menggandakan retrieval policy dan membocorkan batas otorisasi indeks.
- Hanya validasi login di Android: client bukan batas keamanan; request dapat dibuat di luar APK.

## Konsekuensi

Keuntungan: dependency Product ringan, AI dapat rilis terpisah, key tidak di APK, ownership jelas. Biaya: hop jaringan, timeout dan error mapping, contract drift, kebutuhan health/capability check, serta eventual consistency saat sinkronisasi komunitas.

Mitigasi: pinned contract, typed adapter, pooled client, batas concurrency, idempotency lokal, outbox terversi, contract tests, pemisahan LIVE/MOCK, observability dan runbook.

## Konflik sumber yang diselesaikan dan yang belum

| Topik | Keputusan aktif |
|---|---|
| `/api/v1/verify/*` Product lama | Product memakai `/api/v1/verifications/*`; AI tetap `/api/internal/v1/verify/*` |
| Wrapper hasil | Product `request_id`, `status`, `history`, `result`; ID AI tetap di dalam `result` |
| `output_mode` | Product menetapkan `BOTH`; client tidak memilih |
| `pgvector` di Product | Tidak diperlukan untuk pipeline AI; bukan larangan mutlak extension untuk fitur lain di masa depan |
| Enam skor angka per kasus | Enam dimensi kategori dari kontrak AI; jangan mengarang enam angka |
| Koneksi antarpengguna | Koneksi = feed kasus komunitas; friend request/DM tidak dibangun pada baseline ini |
| History seluruh hasil vs review saja | OPEN. Draft memakai `REVIEW_REQUIRED` mengikuti aturan eksplisit lampiran kontrak. Jika tim memilih ALL, revisi PRD, schema/examples dan test sebelum freeze |
| Feed hanya verified vs publikasi kasus review | PROPOSED: feed dapat memuat `PUBLISHED_UNVERIFIED` berlabel; hanya `VERIFIED_EVIDENCE` menjadi bukti RAG |
| POST vs PUT vote | Draft memakai POST sesuai ringkasan audit percakapan terbaru; lampiran lama PUT tetap dicatat. Konfirmasi sebelum generated client dibekukan |
| Hapus kasus verified | Draft mempertahankan lock 403 + jalur permintaan pencabutan; kebijakan penghapusan data pengguna secara lengkap masih perlu keputusan tim |
| Versi upstream | API `0.7.0` terkonfirmasi dari unggahan; `b456057` masih reported dan full commit/deployment belum diverifikasi |

## Kriteria penerapan ADR

- Product tidak memiliki dependency Groq/Tavily/Qdrant/Tesseract maupun import AI repo.
- Build Android tidak memuat internal key, DB credential atau service role.
- AI key salah menghasilkan kegagalan integrasi, bukan percobaan public endpoint tanpa key.
- JWT invalid ditolak sebelum pemanggilan AI.
- RLS dan ownership test lintas dua pengguna lulus.
- Kontrak upstream asli disimpan dengan SHA-256 dan full commit SHA.
- Tidak ada runtime automatic fallback remote→mock.

## Perubahan ADR

Perubahan batas layanan memerlukan review PM/Technical dan AI engineer, rencana migrasi kontrak, pengujian interoperabilitas dan rollback. Dokumen historis di paket bukan izin untuk menghidupkan kembali direct Android→AI.
