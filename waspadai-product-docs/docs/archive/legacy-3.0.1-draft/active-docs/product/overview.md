# Product Overview dan Prinsip

[Kembali ke indeks PRD](prd.md)

## 1. Cara memakai dokumen

Lima modul pada [indeks PRD](prd.md) merupakan spesifikasi aktif paket ini. Sumber 3.0.1 beserta lampiran PRD v2.1 dipertahankan **utuh** di [arsip](../archive/monoliths/product-prd.v3.0.1-draft.md) agar tidak ada rincian sumber hilang; label CURRENT di arsip berarti laporan pada baseline lama, bukan pemeriksaan saat ini. Implementasi Product belum diaudit. Jangan menandai fitur selesai hanya karena tercantum dalam dokumen.

Keputusan rinci yang belum pernah dipastikan tim ditandai PROPOSED/OPEN. Definisi API, transport dan schema berada di [integrasi](../integration/ai-service.md), bukan disalin menjadi kontrak kedua dalam PRD.

## 2. Ringkasan, masalah, dan nilai produk

WaspadAI adalah asisten keamanan informasi Android berbahasa Indonesia untuk membantu pengguna, dengan fokus awal remaja 13–17 tahun menurut PRD terdahulu, memeriksa pesan, tautan, klaim dan screenshot sebelum bertindak. Produk memberikan dukungan keputusan, bukan jaminan bahwa informasi/pengirim aman.

Masalah: pemeriksaan lintas aplikasi merepotkan; pengguna sering menyamakan kebenaran suatu fakta dengan keamanan tautan, keaslian identitas pengirim, atau keamanan tindakan. Model generatif tanpa bukti dapat memberikan jawaban terlalu pasti. Produk mengurangi friksi pemeriksaan dengan capture yang dimulai pengguna, hasil terstruktur, bukti yang dapat diperiksa dan tindakan aman.

Tiga alur produk:

| Alur | Urutan | Ukuran selesai |
|---|---|---|
| Protection | Login, input/capture, review, verifikasi, hasil, history sesuai policy | Satu kasus nyata dapat diperiksa dan dibuka kembali jika eligible |
| Learning | Hasil, materi terkait, lesson, quiz, progress | Penyelesaian dan skor berasal dari state server |
| Knowledge | Preview sanitized, consent, publikasi, evidence/contribution, moderasi, index | Hanya bukti yang diverifikasi masuk retrieval dan dapat dicabut |

## 3. Persona, peran dan batas akses

| Peran | Kebutuhan | Akses |
|---|---|---|
| Guest | Memahami produk | Informasi onboarding; verifikasi pribadi mengharuskan login |
| User | Cek konten, melihat riwayat, belajar, kontribusi | Hanya data privat miliknya dan feed sanitized yang dipublikasikan |
| Moderator | Menilai bukti, menjaga kualitas komunitas | Antrean kontribusi dan data yang memang diserahkan untuk moderasi; bukan semua screenshot privat |
| Admin/operator | Operasi dan akses terkontrol | Role management, konfigurasi dan audit minimum melalui jalur server |
| AI service | Memproses konten yang diperlukan | Tidak menerima user session, role, refresh token, atau akses database Product |

Role bukan field bebas dari client. Identitas diambil dari token tervalidasi. Data remaja memerlukan perhatian terhadap minimisasi, consent yang jelas, dan peluncuran sesuai kebijakan institusi; paket ini tidak menetapkan kesimpulan kepatuhan hukum.

## 4. Prinsip wajib

1. Capture satu kali hanya setelah tindakan pengguna dan consent sistem; tidak ada pemantauan kontinu, auto-read chat, atau penyadapan notifikasi.
2. Preview/crop sebelum mengirim gambar. Pengguna dapat membatalkan atau mengganti input.
3. Screenshot default tidak disimpan; penyimpanan opsional terpisah dari consent publikasi dan consent RAG.
4. Truth, source, sender, channel, scam risk dan content authenticity tidak dipertukarkan.
5. Bukti tidak cukup boleh menghasilkan UNVERIFIED; jangan mengarang sumber, confidence atau identitas.
6. Rulebook adalah aturan pemeriksaan, bukan evidence atas kebenaran klaim.
7. Indikator OTP/PIN/password/seed phrase/transfer mendesak tidak boleh dilemahkan oleh narasi model.
8. Community vote bukan fact-check dan tidak otomatis mengubah verdict atau kelayakan RAG.
9. Tidak ada key provider/internal/service-role di APK atau bundle web.
10. Gangguan remote ditampilkan sebagai kegagalan yang jujur; mock diberi label SIMULASI, tidak diperlakukan sebagai live.
