# Feature Mapping, Migration, dan Definition of Done

[Kembali ke indeks arsitektur sistem](system-and-folder-architecture.md)

## 9. Mapping fitur ke lokasi

| Fitur | Android | Product | Data |
|---|---|---|---|
| Auth | core/auth + feature/auth | dependencies/auth, roles | auth.users, profiles, user_roles |
| Overlay/capture | core/overlay + capture | Hanya validasi upload | Memory/temp, opt-in assets |
| OCR review | core/ocr + privacy | Tidak forward field tak didukung | Sanitized input summary bila perlu |
| Verifikasi | feature/verification | verification_service + AI adapter | request operations, cases, results |
| Detail/evidence | feature/result | safe response projection | evidence/rule match snapshots |
| History | feature/history | history_service | verification_cases |
| Learning/quiz | feature/learning, quiz, progress | learning_service/scoring | modules, lessons, questions, attempts |
| Koneksi/vote | feature/community | community/voting service | community_posts/votes |
| Contribution | feature/contribution | contribution_service | contributions/sources |
| Moderasi | Web opsional | moderation_service | decisions/audit/outbox |
| RAG sync | Tidak ada client index | community_sync_worker→AI | outbox; tidak ada embedding Product |

## 10. Migrasi dari rancangan lama

Tidak memindahkan `apps/api` AI ke backend Product. Jika Product repo baru: buat scaffold baru untuk gateway. Jika sudah ada source: audit working tree/dependency dahulu, pertahankan perubahan pengguna, pindahkan hanya modul yang memang milik Product lewat PR terpisah dengan baseline tests. Tidak melakukan rename besar saat demo path belum stabil.

Komponen yang tidak dibangun pada baseline ini: social friend connections, AI admin Rulebook UI di Product, evidence refresh worker provider, auto capture queue. Rinciannya tetap di arsip untuk roadmap, bukan syarat init.

## 11. Definition of Done arsitektur

Android/Product build lulus; tidak ada import lintas AI repo; dependency direction dites; config tidak berisi secret client; routes sesuai Product OpenAPI; local phone connectivity diuji; RLS tersedia dari migration; cleanup/error paths teruji; ADR, README dan struktur aktual sinkron. Qdrant/index tidak menjadi dependency startup Product.

## Penyesuaian adapter terhadap snapshot unggahan

Product text menerima page_context opsional (title≤300, before/after≤500, masing-masing nullable). question boleh omitted tetapi bukan null; Product menetapkan output_mode=BOTH. Image hanya image/question/output_mode di boundary AI. Validate VerificationResponse beserta seluruh required fields sebelum persistence. Metadata execution_mode=REMOTE/MOCK berada pada envelope Product dan disimpan untuk history; result.mode harus LIVE sesuai schema. Tidak menambahkan field Product ke object AI yang additionalProperties=false.
