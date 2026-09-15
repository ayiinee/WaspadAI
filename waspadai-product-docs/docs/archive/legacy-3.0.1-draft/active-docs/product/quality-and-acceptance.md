# State, Keamanan, Kualitas, dan Acceptance

[Kembali ke indeks PRD](prd.md)

## 8. State yang tidak boleh dicampur

| Domain | State draft |
|---|---|
| Request orchestration | PROCESSING, COMPLETED, FAILED, UNKNOWN_OUTCOME |
| AI truth/risk | Nilai dari kontrak AI; bukan state request |
| History save reason | UNVERIFIED, HUMAN_REVIEW, ALL_POLICY, NOT_REQUIRED, SAVE_FAILED |
| Community | PRIVATE, PUBLISHED_UNVERIFIED, VERIFIED_EVIDENCE, WITHDRAWN; NOT_AVAILABLE hanya envelope kasus tak disimpan |
| Contribution | DRAFT, SUBMITTED, NEEDS_EVIDENCE, VERIFIED, REJECTED, RETRACTED |
| Index sync | NOT_REQUESTED, PENDING, SYNCED, FAILED, BLOCKED, DEINDEX_PENDING |
| Preview | READY, CONSUMED, EXPIRED, INVALIDATED |

Transisi diverifikasi di server dalam transaksi dengan pemeriksaan revision. Moderation claim/lease adalah state operasional, bukan keputusan kebenaran baru. Kata `verified` pada status publikasi tidak sama dengan `source_authenticity=VERIFIED`.

## 9. Keamanan dan retensi produk

- Raw screenshot memory-only default. Opt-in private storage maksimal 24 jam; hapus juga derivative yang tidak lagi berizin. Temp dibersihkan pada success/failure/cancel.
- Sanitized history draft retensi 90 hari atau sampai dihapus, perlu ditampilkan di UI. Perubahan retensi harus diterapkan worker dan kebijakan client bersama.
- Idempotency cache default 10 menit setelah operasi terminal; body cache hanya output aman, bukan upload/raw input.
- Preview expiry draft 15 menit; signed URL draft 5 menit dan tidak melebihi expiry asset.
- Audit log tanpa raw PII/secret; draft retensi 90 hari, konfirmasi sesuai kebutuhan event. Private evidence dan moderation berbeda tingkat aksesnya.
- Publikasi dan RAG mempunyai consent terpisah. Withdrawal harus dipropagasikan ke asset, canonical state dan indeks turunan.
- Query URL harus dibatasi SSRF: skema HTTP(S), public destinations, redirect/DNS/IP divalidasi di service yang benar-benar melakukan fetch. Product tidak men-fetch URL hanya untuk menampilkan.
- Tidak ada token, image bytes, full text privat, signed URL, provider secret atau exception mentah di telemetry.

## 10. Non-functional requirements

| Area | Target dan cara verifikasi |
|---|---|
| Latency | Target historis p50 ≤8s/p95 ≤15s; belum hasil benchmark. Catat teks/gambar terpisah. Timeout bukan SLA |
| Timeout | AI deadline 120s, Product 135s, proxy 145s, Android total 150s sebagai PROPOSED; uji hosting mendukung |
| Reliability | Hasil valid/uncertain dibedakan dari transport error; no fake verdict |
| Consistency | Atomic history result/evidence/snapshot; idempotency dan bounded concurrency |
| Authorization | Token valid + role trusted + owner filter + RLS; negative tests dua user |
| Accessibility | Bahasa sederhana, status dengan teks/ikon bukan warna saja, TalkBack, loading dan retry jelas |
| Compatibility | HP final, Android version/permission behavior, orientation/background diuji |
| Operations | Liveness tanpa dependency, readiness terbatas, logs aman, outbox backlog dan expiry worker terpantau |

## 11. Acceptance criteria yang dapat diuji

| ID | Skenario | Expected |
|---|---|---|
| AC-01 | Tanpa token/expired/issuer salah | 401 sebelum AI; 401 bukan alasan melewati login |
| AC-02 | User A mengakses history B | 404 tanpa konfirmasi keberadaan; tidak ada leak evidence/file |
| AC-03 | Role dimanipulasi melalui profile/body | Ditolak; moderator hanya data server |
| AC-04 | Input 9/25.001 karakter | 422; trim dilakukan sebelum panjang dihitung |
| AC-05 | Upload terlalu besar/tipe salah/dimensi invalid | 413/415/422 sebelum inferensi; file header diverifikasi |
| AC-06 | Consent capture ditolak/dicabut | Tidak merekam; resource release; alternatif manual |
| AC-07 | Satu capture selesai | Tidak ada recording yang terus berjalan |
| AC-08 | Text dan image success | Narasi, enam kategori, evidence, safe action dan disclaimer terbaca |
| AC-09 | Bukti kosong/tidak cukup | Tidak otomatis SAFE/SUPPORTED; uncertainty tampil |
| AC-10 | Raw OCR image tidak didukung upstream | Tidak dikirim sebagai field buatan; review OCR tetap berfungsi |
| AC-11 | Key sama, payload sama | Tidak memulai inferensi kedua; replay/processing response sesuai state |
| AC-12 | Key sama, payload berbeda | 409 IDEMPOTENCY_CONFLICT |
| AC-13 | Timeout setelah bytes terkirim | UNKNOWN_OUTCOME; tidak auto-retry inferensi |
| AC-14 | History REVIEW_REQUIRED | Hanya UNVERIFIED/review tersimpan; UI/envelope konsisten |
| AC-15 | Persistence gagal | SAVE_FAILED, case_id null, tanpa klaim saved; retry persistence-only bila cached result ada |
| AC-16 | Preview expired/konten berubah | 409; publikasi tidak terjadi |
| AC-17 | Tidak memberi consent | Tetap privat, tidak index |
| AC-18 | Vote berulang/ganti/batal | Maksimal satu vote, agregat akurat; owner 403 |
| AC-19 | Banyak vote mendukung | Tidak otomatis VERIFIED_EVIDENCE |
| AC-20 | User biasa melakukan moderasi | 403 |
| AC-21 | Dua moderator membuat keputusan pada revision sama | Satu sukses, satu 409; audit tetap utuh |
| AC-22 | Contribution dicabut | Feed tersembunyi segera; deindex dipantau; tidak claim selesai sebelum acknowledgement |
| AC-23 | Quiz sebelum submit | Tidak ada answer_key/is_correct; setelah submit skor server dan explanation |
| AC-24 | Lesson complete dua kali | Satu completion; progress tidak bertambah ganda |
| AC-25 | Restart/offline/logout | Tidak memunculkan data privat user sebelumnya |
| AC-26 | Production dimulai dalam mock | Startup gagal atau deployment gate menolak |
| AC-27 | Cleanup asset 24 jam | Object dan akses derivative hilang; database status konsisten |
| AC-28 | AI response malformed/unknown critical enum | 502; tidak disimpan sebagai hasil live valid |

## 12. Golden cases

| ID | Input sintetis | Invariant pengujian |
|---|---|---|
| G01 | Pesan bank meminta OTP/PIN segera | Tidak memberi tindakan bagikan OTP; risiko tidak direndahkan; exact enum dikonfirmasi AI |
| G02 | Akun menyerupai instansi + kanal mencurigakan | Tidak menyatakan sender VERIFIED hanya karena nama/logo |
| G03 | Klaim yang dibantah sumber primer fixture | Evidence penyangkal dan provenance tetap ditampilkan; tidak mengarang URL live |
| G04 | Klaim tanpa bukti memadai | UNVERIFIED, uncertainty, history eligible bila policy REVIEW_REQUIRED |
| G05 | Fakta benar tetapi ajakan transfer berisiko | Truth dan scam risk tidak disamakan |
| G06 | AI 429/timeout/malformed response | Error yang jujur, no silent mock, retry aman |
| G07 | Publikasi hasil berisi nomor/email sintetis | Preview akhir sanitized; identity tidak ada di feed |
| G08 | Evidence verified kemudian dicabut | Revision/tombstone mencegah event UPSERT lama menghidupkan kembali index |

Fixtures sintetis untuk test integrasi bukan evidence keberhasilan live. Expected exact AI output harus dibekukan bersama AI engineer pada kontrak dan model/rule version yang sama.
