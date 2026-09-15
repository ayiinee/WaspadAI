# User Flows

[Kembali ke indeks PRD](prd.md)

## 7. User flows lengkap

### 7.1 Login dan token

User membuka onboarding → register/login Supabase → sesi disimpan menggunakan mekanisme aman platform → profile dan capability Product dimuat → akses fitur. Jika verifikasi email diperlukan, tampilkan pending state dan resend sesuai SDK. Logout membersihkan session, cache privat, input sementara dan menghentikan overlay. Pada 401 Product, refresh terkoordinasi satu kali lalu retry aksi yang sama dengan idempotency key yang sama. Jika masih gagal, minta login; jangan loop.

### 7.2 Verifikasi teks

Input/paste → trim dan validasi → review teks/sumber/konteks → submit → loading → Product auth/input/idempotency → AI internal → schema validation → history policy/persistence → hasil. Batal sebelum kirim tidak memanggil AI. Batal setelah terkirim tidak menjamin komputasi server berhenti; duplicate retry tidak membuat inferensi kedua secara otomatis.

### 7.3 Verifikasi screenshot

Aktifkan overlay → izin overlay → pilih verify → consent MediaProjection yang berlaku untuk target Android → ambil satu frame → release capture resource → preview/crop/redact → OCR lokal untuk review → unggah multipart → tunggu hasil → hapus temp.

Jika izin ditolak: jelaskan cara mengaktifkannya, sediakan input manual/import gambar. Jika frame hitam/secure screen: beri pesan konten tidak dapat ditangkap, jangan mencoba melewati proteksi. Jika OCR kosong: pengguna dapat crop ulang atau lanjut image bila valid. Jika internet hilang: pertahankan input hanya selama layar/sesi aman dan izin pengguna; jangan simpan screenshot ke work queue permanen tanpa consent.

OCR lokal bukan field yang otomatis dapat diteruskan: baseline image AI yang tersedia hanya mendokumentasikan image/question/output_mode. Jangan memasukkan `ocr_text`, `source_url`, atau `sender_context` sebagai multipart field upstream sebelum kontraknya mendukung. Jalur text dari OCR harus menjadi aksi eksplisit tersendiri.

### 7.4 History

**OPEN — proposal draft:** `HISTORY_POLICY=REVIEW_REQUIRED`, mengikuti aturan eksplisit lampiran kontrak: simpan jika `verdict == UNVERIFIED OR requires_human_review == true`. Hasil lain tetap tampil tetapi `history.saved=false`, `case_id=null`, `save_reason=NOT_REQUIRED`.

Keputusan “simpan semua pemeriksaan” pada PRD lama dipertahankan sebagai opsi ALL, bukan diabaikan. Tim harus memilih sebelum freeze; policy tidak boleh berubah per instance. UI menjelaskan history sebagai “Kasus yang perlu ditinjau” saat REVIEW_REQUIRED. Daftar hasil sukses seluruhnya bukan janji baseline draft ini.

History list → detail → evidence/safe action → hapus jika belum VERIFIED_EVIDENCE. Untuk kasus terkunci, tersedia informasi alasan dan proses permintaan pencabutan; lock teknis bukan pengganti kebijakan penghapusan data yang sah. Jika penyimpanan gagal, jangan menyatakan tersimpan; respons memuat SAVE_FAILED dan hasil tetap dapat ditampilkan jika aman. Perilaku retry rinci ada di integrasi.

### 7.5 Community, preview, consent dan voting

Kasus eligible tersimpan PRIVATE → pengguna meminta preview → Product sanitasi ulang dan membuat versi final + expiry → UI menampilkan teks/image final → consent eksplisit → publish `PUBLISHED_UNVERIFIED` → feed berlabel belum diverifikasi → user lain memberi DIDUKUNG/DIBANTAH → moderator menilai evidence.

Preview terikat owner, kasus, content hash, versi redaksi dan expiry. Perubahan konten mengharuskan preview/consent baru. Tidak ada publikasi otomatis karena AI menganggap eligible. Tanpa screenshot tersimpan, preview teks-only valid; jangan membuat janji signed URL gambar yang sudah dihapus.

Vote maksimal satu per user/kasus, dapat diganti/dibatalkan, pemilik tidak boleh vote kasus sendiri, identitas voter tidak dipublikasikan. Aggregat vote tidak mengubah hasil AI atau membuat RAG evidence. Feed PROPOSED membedakan PUBLISHED_UNVERIFIED dan VERIFIED_EVIDENCE; hanya yang kedua dapat ditawarkan sebagai verified knowledge.

### 7.6 Contribution, moderasi dan indexing

User membuat DRAFT berisi URL evidence/ringkasan/alasan → SUBMITTED → moderator claim → NEEDS_EVIDENCE / REJECTED / VERIFIED. NEEDS_EVIDENCE dapat diedit dan disubmit ulang. VERIFIED hanya dengan alasan, sumber, sanitasi dan consent yang sah. Koreksi/verifikasi ulang menambahkan keputusan baru, bukan mengubah log lama.

VERIFIED + allow_rag + consent_rag → outbox UPSERT → AI memvalidasi payload/version → index → acknowledgement. RETRACTED/consent ditarik → segera sembunyikan dari Product feed, naikkan revision, outbox DELETE → AI deindex. Saat indexing tidak tersedia, status jujur PENDING/BLOCKED; jangan mengklaim kontribusi sudah dipakai RAG.

### 7.7 Learning dan progress

User membuka modul published → lesson → complete idempotent → quiz tanpa kunci → submit jawaban → scoring server terhadap versi soal → pembahasan → progress/skor terbaik. Input answer tidak boleh membawa skor/is_correct yang dipercaya server. Perubahan modul tidak merusak audit attempt lama; simpan version snapshot.
