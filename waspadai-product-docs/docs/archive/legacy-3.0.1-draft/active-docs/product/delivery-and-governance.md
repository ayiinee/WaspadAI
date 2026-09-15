# Status Implementasi, Delivery, dan Governance

[Kembali ke indeks PRD](prd.md)

## 13. Implementation status awal

| Area | Status paket | Bukti sebelum boleh DONE |
|---|---|---|
| Keputusan batas repo | DECIDED | ADR dan dependency checks |
| 7 dokumen | AUTHORED | Link/struktur/consistency checks |
| Product contract | DRAFT | Review OPEN + export parity + client test |
| Upstream AI snapshot | UPLOADED SCHEMA VERIFIED | File unggahan dan SHA-256 tersedia; full commit serta smoke staging belum dikonfirmasi |
| Android/Product API/database | TARGET | Source, build, tests, migration dan E2E |
| AI kemampuan/61 test lama | REPORTED BASELINE | Hasil test baru dari AI engineer, bukan angka arsip |
| Community indexing | OPEN | Endpoint/schema/retract capability dikonfirmasi |

Tambahkan kolom owner, branch/PR, tanggal, blocker dan evidence saat coding dimulai. Jangan mengganti TARGET menjadi CURRENT hanya karena folder dibuat.

## 14. Eksekusi dan pembagian kerja

Milestone A: kontrak/ADR/policy → scaffold → identity/RLS → text end-to-end. Milestone B: image/capture → result/history → golden cases. Milestone C: learning/quiz/progress → consent/community/vote → contribution/moderation. Milestone D: indexing bila dependency siap → deployment → rehearsal/video/PPT.

Jadwal 15–18 September pada arsip merupakan rencana historis, bukan janji waktu yang diverifikasi. Owner AI: Shafwan. Owner Product/Supabase/release: PM/Technical. Designer: flow, states, assets dan materi presentasi. Gunakan satu blocker utama per anggota; pisahkan perubahan kontrak dari polish UI.

## 15. Risiko dan mitigasi

| Risiko | Mitigasi |
|---|---|
| Scope luas | Urutan vertical slice; tetap catat fitur deferred dan label status demo |
| Drift kontrak | Full pin/checksum + contract diff + fixture + review bersama |
| AI unavailable | Health, timeout, error state; mock hanya eksplisit, video cadangan |
| Data pribadi tersimpan | Memory-only, redaksi, consent/expiry dan cleanup tests |
| Community poisoning | Provenance, moderator, consent, verified-only RAG dan retract |
| Retry biaya ganda | Local idempotency tidak dianggap idempotency AI; unknown outcome tidak auto-resubmit |
| Pool DB habis | Pool bounded, transaksi pendek, tidak menunggu AI dalam transaksi |
| Permission OEM berbeda | Uji HP demo sejak awal, fallback input manual |

## 16. Definition of Done

Rilis Product selesai bila login/capture/text/image/result berjalan di HP final; history sesuai policy; learning/quiz/progress tersimpan; community/consent/vote/moderation bekerja; semua privacy/RLS/error tests lulus; OpenAPI upstream benar-benar dipin; deployment reproducible tanpa source AI; mock/debug tidak aktif di production; rehearsal dua kali dan video/build cadangan tersedia. Klaim Verified Community RAG hanya boleh masuk demo jika round-trip indexing/retrieval/retract benar-benar telah diuji.

## 17. Coverage sumber dan change control

Tidak ada detail sumber sengaja dihapus: folder lama, database lama, kontrak Android lama, dan PRD v2.1 tetap utuh dalam [arsip monolitik](../archive/README.md). Modul aktif mengganti konflik dengan keputusan eksplisit; arsip menjaga rumus BM25, hashed-subword, parameter planner, Qdrant payload, daftar modul dan seluruh catatan lama untuk audit.

Perubahan taxonomy, history policy, community visibility, endpoint, request fields, retention, model/rule baseline atau indexing harus mencatat owner/alasan/diff/schema migration/test/rollback. Kontrak Product dan migration aktif diperbarui bersama, bukan menyalin SQL/enum lama dari arsip.

## Penyesuaian kontrak unggahan 0.7.0

Request text mendukung page_context opsional untuk konteks kutipan; question opsional tetapi tidak menerima null. Default output_mode AI STRUCTURED, sehingga Product wajib meminta BOTH. Seluruh 25 field VerificationResponse wajib; nullable tetap wajib hadir jika tercantum required. verdict memiliki delapan nilai tanpa NOT_APPLICABLE, sedangkan factual_status dapat NOT_APPLICABLE. Evidence verification_status mencakup REVIEWED. mode AI konstan LIVE; simulasi ditandai execution_mode=MOCK pada envelope Product, termasuk history/detail komunitas. UI memakai penanda Product tersebut untuk badge SIMULASI. Schema ketat tidak berarti setiap klaim AI benar atau deployment sudah diuji.
