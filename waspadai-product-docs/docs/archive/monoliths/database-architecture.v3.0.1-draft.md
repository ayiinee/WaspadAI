# WaspadAI — Database Architecture, Dictionary, and Setup

Versi 3.0.1-draft. Status PROPOSED schema Product; belum migration yang dijalankan. Dokumen menjabarkan desain hingga kolom, constraint, index, RLS dan lifecycle. SQL ilustratif harus diterapkan melalui migration dan diuji pada Supabase versi yang dipakai tim. Arsip database lama utuh di akhir bersifat NON-NORMATIVE.

## 1. Keputusan dasar

- Supabase Auth memiliki akun; PostgreSQL Product memiliki data transaksional; Storage hanya artefak opt-in/private; Product API jalur operasi bisnis.
- Product tidak menyimpan corpus Rulebook, embedding, BM25 index, `rulebook_chunks`, atau `community_chunks`. Tidak perlu extension vector.
- Product **boleh dan perlu** menyimpan snapshot evidence/rule matches, canonical kontribusi sanitized, keputusan moderator, consent, outbox dan version provenance. Memisahkan AI tidak berarti memindahkan semua data komunitas ke AI.
- Koneksi adalah feed kasus, bukan tabel friend request. Six dimensions disimpan kategori, bukan enam skor numerik buatan.
- Migration SQL yang direview adalah sumber kebenaran schema setelah implementasi; kamus ini diperbarui pada PR schema yang sama.
- History policy draft REVIEW_REQUIRED; tabel idempotency/operasi bukan history publik. Hasil tidak eligible tidak berubah menjadi history hanya karena cache retry diperlukan.

## 2. Konvensi

ID internal UUID, default `gen_random_uuid()` bila tersedia pada runtime Supabase yang dipilih. ID AI adalah `text`, karena contoh upstream memakai prefix request/case; jangan memaksa format UUID upstream. API `case_id` Product dipetakan ke UUID `verification_cases.id` dalam draft ini.

Timestamp UTC `timestamptz`. Kolom `created_at` default `now()` not null; `updated_at` trigger pada tabel mutable. Catatan append-only tidak memiliki update rutin. `NN` berarti NOT NULL; `?` berarti nullable. JSONB bukan tempat menyembunyikan password/PII atau menghindari validasi.

Status Product menggunakan `text CHECK (...)` untuk kemudahan evolusi; enum AI mengikuti pinned schema dan divalidasi aplikasi. Enum AI kini dapat mengikuti snapshot unggahan API 0.7.0 yang disertakan; full commit/deployment tetap belum dikonfirmasi. Audit/projection tetap menyimpan `schema_version`.

## 3. Relasi inti

```mermaid
erDiagram
  PROFILES ||--o{ VERIFICATION_CASES : owns
  VERIFICATION_CASES ||--|| VERIFICATION_RESULTS : has
  VERIFICATION_CASES ||--o{ VERIFICATION_EVIDENCE : cites
  VERIFICATION_CASES ||--o{ VERIFICATION_RULEBOOK_MATCHES : traces
  VERIFICATION_CASES ||--o{ COMMUNITY_PREVIEWS : previews
  VERIFICATION_CASES ||--o| COMMUNITY_POSTS : publishes
  COMMUNITY_POSTS ||--o{ COMMUNITY_VOTES : receives
```

```mermaid
erDiagram
  PROFILES ||--o{ CONTRIBUTIONS : submits
  CONTRIBUTIONS ||--o{ CONTRIBUTION_SOURCES : supports
  CONTRIBUTIONS ||--o{ MODERATION_DECISIONS : reviewed
  CONTRIBUTIONS ||--o{ OUTBOX_EVENTS : syncs
  LEARNING_MODULES ||--o{ LEARNING_LESSONS : contains
  LEARNING_MODULES ||--o{ QUIZ_QUESTIONS : asks
  PROFILES ||--o{ QUIZ_ATTEMPTS : answers
  QUIZ_ATTEMPTS ||--o{ QUIZ_ANSWERS : records
```

Semua child privat menentukan ownership melalui FK parent, bukan `user_id` client. Outbox aggregate dapat menunjuk contribution atau community case melalui `(aggregate_type, aggregate_id)`; validitas diperiksa service dan transaction, bukan FK polimorfik yang tidak dapat ditegakkan.

## 4. Identity domain

### 4.1 `public.profiles`

| Kolom | Tipe | Aturan |
|---|---|---|
| id | uuid NN | PK/FK auth.users(id) ON DELETE CASCADE |
| display_name | text NN | Trim 1–80 karakter |
| avatar_asset_id | uuid ? | FK stored_assets, nullable, hanya avatar yang diizinkan |
| bio | text ? | Maksimum 500 karakter; jangan tampilkan di komunitas secara otomatis |
| locale | text NN | Default id-ID; allowlist aplikasi |
| is_active | boolean NN | Default true; tidak dapat diubah user biasa |
| created_at, updated_at | timestamptz NN | Lifecycle |

Email tidak diduplikasi. Trigger profile setelah signup memakai security definer dengan search_path kosong dan identifier schema-qualified. Jangan percaya metadata `role`. Panjang display name dari metadata dinormalisasi agar signup tidak gagal akibat nilai invalid; fallback “Pengguna WaspadAI”. Circular FK avatar ditambahkan sesudah stored_assets dibuat.

### 4.2 `public.user_roles`

`user_id uuid NN FK profiles ON DELETE CASCADE`, `role text NN CHECK USER/MODERATOR/ADMIN`, `granted_by uuid ? FK profiles ON DELETE SET NULL`, `created_at timestamptz NN`. PK `(user_id, role)`. Signup memberi USER. Hanya admin melalui jalur server yang dapat menambah/mencabut role. Tabel ini bukan editable profile property.

## 5. Verification dan idempotency

### 5.1 `private.request_operations`

| Kolom | Tipe | Aturan |
|---|---|---|
| id | uuid NN | PK; ID operasi Product |
| user_id | uuid NN | FK profiles CASCADE |
| route_key | text NN | Method + canonical path untuk scope key |
| idempotency_key | uuid NN | Header aksi; unique bersama user_id dan route_key |
| payload_hash | text NN | SHA-256 payload canonical, termasuk image digest dan metadata efektif |
| state | text NN | PROCESSING, COMPLETED, FAILED, UNKNOWN_OUTCOME |
| upstream_started_at | timestamptz ? | Memisahkan belum terkirim dan mungkin sudah diproses |
| lease_until | timestamptz ? | Expiry lease orchestration; bukan izin inferensi ulang |
| response_json | jsonb ? | Cache response aman/redacted; tidak mengandung screenshot/token/signed URL panjang umur |
| http_status | smallint ? | Response cache terminal |
| ai_result_cache | jsonb ? | Hasil aman untuk retry persistence-only bila diperlukan |
| persistence_state | text NN | NOT_REQUIRED, PENDING, SAVED, FAILED |
| error_code | text ? | Safe code, bukan exception body provider |
| expires_at | timestamptz NN | 10 menit setelah terminal sebagai proposal; processing tidak dibersihkan sembarang |
| created_at, updated_at | timestamptz NN | Lifecycle |

Unique `(user_id, route_key, idempotency_key)`. Index `(state, lease_until)` dan `expires_at`. Cache hanya dapat dibaca trusted backend untuk principal yang cocok. Jangan menyimpan raw request text/image; hashing bukan redaksi bila raw input tetap disimpan di kolom lain.

### 5.2 `public.verification_cases`

| Kolom | Tipe | Aturan |
|---|---|---|
| id | uuid NN | PK, API case_id |
| user_id | uuid NN | FK profiles CASCADE, immutable |
| operation_id | uuid ? | FK private.request_operations ON DELETE SET NULL; cache boleh expired tanpa menghapus history |
| product_request_id | uuid NN | ID korelasi Product; unique |
| ai_request_id, ai_trace_id | text ? | ID upstream, tidak direka bila absent |
| input_type | text NN | TEXT/IMAGE |
| input_source | text NN | MANUAL/OVERLAY/SHARE_INTENT/WEB |
| sanitized_text | text ? | Input ringkas/sanitized, max 25.000; tidak wajib menyimpan OCR penuh |
| input_hash | text NN | SHA-256 input efektif |
| headline | text NN | Ringkasan aman dari response |
| verdict | text NN | Nilai upstream sesuai schema pinned |
| risk_level | text NN | Nilai upstream |
| requires_human_review | boolean NN | Nilai upstream |
| save_reason | text NN | UNVERIFIED/HUMAN_REVIEW/ALL_POLICY |
| community_state | text NN | PRIVATE/PUBLISHED_UNVERIFIED/VERIFIED_EVIDENCE/WITHDRAWN |
| revision | bigint NN | Default 1, optimistic concurrency |
| retention_expires_at | timestamptz NN | Default policy 90 hari; immutable hanya via retention service |
| deleted_at | timestamptz ? | Tombstone cleanup singkat, bukan janji penghapusan selesai |
| created_at, updated_at | timestamptz NN | Lifecycle |

Kasus tersimpan mewakili hasil AI valid; kegagalan transport berada di operation table, tidak diisi verdict palsu. Index `(user_id, created_at DESC, id DESC)` WHERE deleted_at IS NULL; `retention_expires_at`; `(community_state, id)`. RLS owner; API mengembalikan 404 untuk non-owner.

### 5.3 `public.verification_results`

| Kolom | Tipe | Aturan |
|---|---|---|
| case_id | uuid NN | PK/FK verification_cases CASCADE; tepat satu row per stored case |
| factual_status | text NN | Enum kategori pinned |
| source_authenticity | text NN | Enum kategori pinned |
| sender_identity | text NN | Enum kategori pinned |
| channel_status | text NN | Enum kategori pinned |
| scam_risk | text NN | Enum kategori pinned |
| content_authenticity | text NN | Enum kategori pinned |
| evidence_sufficiency | numeric(5,4) ? | CHECK 0–1; missing tetap null |
| uncertainty | text ? | Penjelasan aman |
| result_json | jsonb NN | Snapshot response AI yang sudah divalidasi dan diproyeksikan aman |
| upstream_contract_version | text ? | Versi hasil lock/header jika tersedia |
| execution_mode | text NN | REMOTE/MOCK dari Product; bukan kolom di result_json AI |
| model_version, pipeline_version, rulebook_version, prompt_version | text ? | Tidak mengisi angka dugaan |
| created_at | timestamptz NN | Snapshot immutable |

Kolom terpilih untuk query, result_json untuk fidelity. Pembuatan keduanya dalam satu mapper/transaction; test memverifikasi dimensi kolom sama dengan snapshot. Tidak membuat assessment_dimension_definitions atau enam row skor; jika nanti AI menambah rationale/score per dimensi, simpan field tambahan mengikuti versi kontrak, bukan membuat angka sekarang.

### 5.4 `public.verification_evidence`

`id uuid PK`, `case_id uuid NN FK cases CASCADE`, `upstream_evidence_id text NN`, `display_order smallint NN`, `source_type text ?`, `publisher text ?`, `title text ?`, `source_url text ?`, `source_domain text ?`, `excerpt text ?`, `stance text ?`, `verification_status text ?`, `published_at timestamptz ?`, `retrieved_at timestamptz ?`, `relevance numeric ?`, `authority numeric ?`, `recency numeric ?`, `provenance jsonb NN DEFAULT '{}'`, `evidence_json jsonb NN`, `created_at timestamptz NN`.

Unique `(case_id, upstream_evidence_id)`. Score numeric jika tersedia harus 0–1; null bukan zero. Index case_id. URL hanya HTTP(S), max 2.048. Jika upstream tidak mempunyai stable evidence id, mapper membuat ID lokal deterministik dari index/hash dan mencatat `id_origin=PRODUCT`, tidak mengaku ID AI. Tidak menyimpan artikel penuh.

### 5.5 `public.verification_rulebook_matches`

`id uuid PK`, `case_id uuid NN FK CASCADE`, `rule_id text NN`, `rule_version text ?`, `rank smallint NN`, `match_type text ?`, `phase text ?`, `score numeric ?`, `score_components jsonb ?`, `match_snapshot jsonb NN`, `created_at timestamptz NN`. Unique `(case_id, rank)`; index case_id. Tidak ada FK `rulebook_chunks`; rule ID/version adalah external reference. Isi hanya jika response menyediakan trace yang aman; jangan menyalin corpus/prompt internal demi melengkapi kolom.

### 5.6 `private.verification_events` (P2)

`id bigint GENERATED ALWAYS AS IDENTITY PK`, `operation_id uuid ?`, `event_type text NN`, `stage text ?`, `duration_ms integer ? CHECK >=0`, `safe_metadata jsonb NN DEFAULT '{}'`, `created_at timestamptz NN`. Hanya event yang benar-benar diketahui Product; jangan mengarang OCR/planner stage jika upstream synchronous tidak melaporkannya. Retensi terbatas; tidak user-readable.

## 6. Asset, preview dan consent

### 6.1 `private.stored_assets`

`id uuid PK`, `user_id uuid NN FK profiles CASCADE`, `case_id uuid ? FK cases CASCADE`, `bucket text NN`, `object_path text NN`, `purpose text NN CHECK SCREENSHOT_OPT_IN/REDACTED_PREVIEW/CONTRIBUTION_EVIDENCE/AVATAR/LEARNING`, `mime_type text NN`, `size_bytes bigint NN CHECK >=0`, `sha256 text NN`, `consent_id uuid ?`, `expires_at timestamptz ?`, `deleted_at timestamptz ?`, `created_at timestamptz NN`. Unique `(bucket, object_path)`; index `(expires_at)` WHERE deleted_at IS NULL. Avatar/learning mempunyai kebijakan retensi terpisah, tidak otomatis 24 jam.

### 6.2 `public.community_previews`

`id uuid PK`, `case_id uuid NN FK cases CASCADE`, `user_id uuid NN FK profiles CASCADE`, `case_revision bigint NN`, `redacted_text text NN`, `redacted_asset_id uuid ? FK stored_assets SET NULL`, `content_hash text NN`, `redaction_version text NN`, `redactions jsonb NN DEFAULT '[]'`, `state text NN CHECK READY/CONSUMED/EXPIRED/INVALIDATED`, `expires_at timestamptz NN`, `created_at timestamptz NN`, `consumed_at timestamptz ?`.

Index `(case_id, created_at DESC)` dan expires_at. TTL proposal 15 menit. Publish transaction memeriksa user/case/revision/hash/expiry dan state READY, lalu menandai CONSUMED agar preview tidak dipakai ulang setelah konten berubah.

### 6.3 `private.consent_records`

`id uuid PK`, `user_id uuid NN FK profiles CASCADE`, `scope text NN CHECK TEMP_SCREENSHOT_STORAGE/COMMUNITY_PUBLICATION/RAG_REUSE`, `case_id uuid ?`, `contribution_id uuid ?`, `preview_id uuid ?`, `content_hash text NN`, `policy_version text NN`, `granted_at timestamptz NN`, `revoked_at timestamptz ?`, `expires_at timestamptz ?`.

FK case/contribution/preview nullable dengan deletion strategy direview agar tidak menyimpan isi yang sudah harus dihapus. Record audit consent hanya metadata minimum; raw content tidak ditanamkan. Constraint minimal satu target case/contribution. Consent ditarik dengan revoke timestamp dan event, bukan diabaikan karena konten telah terverifikasi.

## 7. Community dan contribution

### 7.1 `public.community_posts`

`id uuid PK`, `case_id uuid NN UNIQUE FK cases ON DELETE RESTRICT`, `owner_id uuid NN FK profiles`, `preview_id uuid ? FK previews SET NULL`, `title text NN`, `redacted_text text NN`, `redacted_asset_id uuid ? FK stored_assets SET NULL`, `status text NN CHECK PUBLISHED_UNVERIFIED/VERIFIED_EVIDENCE/WITHDRAWN`, `verification_contribution_id uuid ?`, `publication_consent_id uuid NN`, `rag_consent_id uuid ?`, `content_hash text NN`, `revision bigint NN DEFAULT 1`, `published_at timestamptz NN`, `withdrawn_at timestamptz ?`, `verified_at timestamptz ?`, `created_at/updated_at timestamptz NN`.

API tidak menampilkan owner_id, consent id, private paths atau original screenshot. Index `(status, published_at DESC, case_id DESC)` WHERE withdrawn_at IS NULL. Status case dan post diperbarui dalam transaksi yang sama. Feed list adalah DTO sanitized, bukan SELECT * serialisasi tabel.

### 7.2 `public.community_votes`

`post_id uuid NN FK community_posts CASCADE`, `user_id uuid NN FK profiles CASCADE`, `vote text NN CHECK DIDUKUNG/DIBANTAH`, `created_at/updated_at timestamptz NN`. PK `(post_id,user_id)`. Index `(post_id,vote)`.

Upsert atomic. Larangan owner vote membutuhkan service/policy/trigger dengan lookup post; CHECK constraint biasa tidak boleh dipakai untuk cross-table query. Cancel DELETE idempotent; aggregate dihitung dari committed rows atau materialized counter yang konsistensinya diuji. Tidak mengekspos voter identity lewat Data API; API mengembalikan user_vote milik principal dan counts saja.

### 7.3 `public.contributions`

`id uuid PK`, `user_id uuid NN FK profiles`, `case_id uuid ? FK cases ON DELETE SET NULL`, `title text NN max 200`, `summary text NN max 5.000`, `reasoning text NN max 5.000`, `sanitized_content text ?`, `status text NN CHECK DRAFT/SUBMITTED/NEEDS_EVIDENCE/VERIFIED/REJECTED/RETRACTED`, `content_hash text NN`, `revision bigint NN DEFAULT 1`, `publication_consent_id uuid ?`, `rag_consent_id uuid ?`, `claimed_by uuid ? FK profiles SET NULL`, `claim_expires_at timestamptz ?`, `submitted_at/verified_at/retracted_at timestamptz ?`, `created_at/updated_at timestamptz NN`.

Owner hanya edit DRAFT/NEEDS_EVIDENCE. Target case bila ada harus milik user atau community case yang boleh diberi evidence, sesuai endpoint policy. Index `(user_id,created_at DESC)` dan `(status,submitted_at,id)`; optimistic concurrency revision. Standalone contribution tidak otomatis menjadi post tanpa case pada draft; boleh menjadi canonical verified knowledge setelah consent/moderation.

### 7.4 `public.contribution_sources`

`id uuid PK`, `contribution_id uuid NN FK contributions CASCADE`, `source_url text NN max 2.048`, `title text ? max 300`, `publisher text ? max 200`, `note text ? max 2.000`, `asset_id uuid ? FK stored_assets SET NULL`, `source_hash text NN`, `created_at timestamptz NN`. Unique `(contribution_id,source_hash)`; index parent. URL tidak di-fetch server tanpa SSRF guard. Attachment bukan bukti valid otomatis.

### 7.5 `public.moderation_decisions`

`id uuid PK`, `contribution_id uuid NN FK contributions ON DELETE RESTRICT`, `moderator_id uuid ? FK profiles SET NULL`, `expected_revision bigint NN`, `previous_status text NN`, `action text NN CHECK VERIFY/REJECT/NEEDS_EVIDENCE/RETRACT`, `new_status text NN`, `reason text NN max 5.000`, `evidence_ids jsonb NN DEFAULT '[]'`, `sanitized_snapshot jsonb NN`, `publish_to_connection boolean NN DEFAULT false`, `allow_rag boolean NN DEFAULT false`, `created_at timestamptz NN`.

Append-only untuk runtime role; tidak UPDATE/DELETE. Snapshot hanya konten yang memang diserahkan untuk moderasi. FK RESTRICT melindungi audit teknis; user deletion/revocation tetap membutuhkan workflow retention/anonymization yang ditetapkan, bukan error database tanpa jalur penyelesaian.

## 8. Learning dan quiz

| Tabel | Kolom lengkap target | Constraint/index utama |
|---|---|---|
| learning_modules | id uuid PK; slug text NN; title text NN; summary text NN; cover_asset_id uuid?; difficulty smallint NN; display_order smallint NN; version integer NN; status text NN DRAFT/PUBLISHED/ARCHIVED; created_at/updated_at timestamptz NN | UNIQUE(slug,version); difficulty 1–5; version >0; index status/order |
| learning_lessons | id uuid PK; module_id uuid NN FK CASCADE; title text NN; body_md text NN; duration_minutes smallint NN; display_order smallint NN; is_published boolean NN; created_at/updated_at NN | UNIQUE(module_id,display_order); duration >0; sanitized rendering |
| quiz_questions | id uuid PK; module_id uuid NN FK CASCADE; lesson_id uuid? FK SET NULL; question_text text NN; explanation text NN; version integer NN; display_order smallint NN; is_active boolean NN; created_at/updated_at NN | UNIQUE(module_id,display_order,version); no answer disclosure API |
| quiz_options | id uuid PK; question_id uuid NN FK CASCADE; option_text text NN; display_order smallint NN; is_correct boolean NN | UNIQUE(question_id,display_order); canonical single-correct checked at publish/transaction trigger |
| lesson_progress | user_id uuid NN FK profiles CASCADE; lesson_id uuid NN FK lessons CASCADE; completed_at timestamptz NN | PK(user_id,lesson_id); idempotent completion |
| quiz_attempts | id uuid PK; user_id uuid NN FK profiles CASCADE; module_id uuid NN FK modules RESTRICT; module_version integer NN; submission_key uuid NN; total_questions integer NN; correct_answers integer NN; score numeric(5,2) NN; question_snapshot jsonb NN; completed_at/created_at NN | UNIQUE(user_id,module_id,submission_key); total>0; 0≤correct≤total; score 0–100 |
| quiz_answers | attempt_id uuid NN FK attempts CASCADE; question_id uuid NN FK questions RESTRICT; selected_option_id uuid NN FK options RESTRICT; is_correct boolean NN; answered_at timestamptz NN | PK(attempt_id,question_id); selected option harus milik question, question milik module/version attempt |

`learning_progress` adalah response/view agregasi, bukan tabel kedua yang menduplikasi lesson_progress. Persentase = completed published lessons / total published lessons ×100, zero bila belum ada lesson. Skor terbaik MAX(score), skor terakhir berdasarkan timestamp/id. Jika dibuat materialized cache, data sumber tetap progress/attempts dan rekonsiliasi wajib.

Jawaban benar sebaiknya disimpan di schema privat atau tidak diberikan SELECT langsung kepada authenticated. Bahkan jika API menyembunyikan `is_correct`, grant tabel yang terlalu luas masih bisa membocorkannya. Gunakan view/projection tanpa answer key dan jangan expose private schema ke PostgREST. Explanation quiz diberikan setelah submit sesuai workflow.

## 9. Outbox dan audit

### `private.outbox_events`

`id uuid PK`, `aggregate_type text NN`, `aggregate_id uuid NN`, `aggregate_revision bigint NN`, `event_type text NN CHECK COMMUNITY_UPSERT/COMMUNITY_DELETE`, `payload jsonb NN`, `content_hash text NN`, `state text NN CHECK PENDING/PROCESSING/DONE/FAILED/BLOCKED`, `attempts integer NN DEFAULT 0`, `available_at timestamptz NN`, `locked_by text ?`, `lease_until timestamptz ?`, `processed_at timestamptz ?`, `last_error_code text ?`, `created_at timestamptz NN`.

Unique `(aggregate_type,aggregate_id,aggregate_revision,event_type)`. Index `(state,available_at,id)` dan `(aggregate_id,aggregate_revision)`. Payload sanitized; jangan memasukkan raw image/PII/key. Worker menolak stale revision; retract membuat tombstone/revision lebih tinggi. AI perlu revision enforcement agar UPSERT lama tidak menghidupkan kembali record yang dicabut. Tanpa capability itu, indexing belum release-ready.

### `private.audit_logs`

`id bigint GENERATED ALWAYS AS IDENTITY PK`, `actor_id uuid ? FK profiles SET NULL`, `action text NN`, `resource_type text NN`, `resource_id text ?`, `request_id uuid ?`, `trace_id text ?`, `safe_metadata jsonb NN DEFAULT '{}'`, `created_at timestamptz NN`. Index `(resource_type,resource_id,created_at)` dan created_at untuk retensi. Tidak menyimpan alamat IP penuh bila tidak diperlukan; hash pun masih perlu kebijakan privasi. Role runtime insert-only untuk audit; penghapusan retensi oleh role terpisah yang diaudit.

## 10. RLS, grants dan role koneksi

Proposal runtime: direct PostgreSQL connection menggunakan dedicated `product_app` LOGIN, non-superuser, NOBYPASSRLS, bukan tabel owner. Credential disiapkan operator di luar Git. Table owner/migration role terpisah. Role pekerja dibatasi hanya tugasnya. Supabase service-role key bukan credential umum semua request.

Setelah token diverifikasi, setiap transaksi request memasang claims secara transaction-local melalui parameterized `set_config('request.jwt.claims', ..., true)` dan claim sub yang sesuai implementasi `auth.uid()` di runtime. Jangan menjalankan SET global pada pooled connection; user A bisa terbawa ke request B. Test memastikan claims hilang saat transaction berakhir. Jangan menerima claims JSON dari body user.

| Data | User melalui API | Moderator | Direct client/Data API |
|---|---|---|---|
| Profile | Read/update field whitelist sendiri | Tidak mengubah role lewat profile | Default dibatasi; explicit safe grants bila digunakan |
| Verification/result/evidence | Owner saja | Tidak otomatis mendapat akses seluruh history | Tidak ada write langsung |
| Preview/consent | Owner sesuai workflow | Hanya konten submitted | Tidak ada akses mentah |
| Post publik | Sanitized active DTO | Sama + jalur moderation | View aman saja bila diperlukan |
| Votes | Own vote + counts | Tidak expose identitas voter | Tabel raw tidak dibaca publik |
| Contribution | Owner DRAFT/NEEDS_EVIDENCE, submit via service | Queue/review yang diserahkan | Write raw dilarang |
| Decisions | Projection aman terkait kontribusi | Append lewat server | Tidak ada raw write |
| Learning | Published tanpa answer key | Manage via server | View published tanpa quiz keys |
| Progress/attempts | Own read; write via domain service | Agregat terkontrol | Score write dilarang |
| Roles/outbox/audit | Tidak | Jalur terkontrol, bukan akses client umum | Tidak ada |

Contoh policy ilustratif (migration harus menggunakan role/grant nyata):

```sql
alter table public.verification_cases enable row level security;
alter table public.verification_cases force row level security;
create policy own_case_read on public.verification_cases
for select to product_app
using (user_id = (select auth.uid()) and deleted_at is null);
create policy own_case_insert on public.verification_cases
for insert to product_app
with check (user_id = (select auth.uid()));
```

Child policy EXISTS parent owned. UPDATE policy perlu USING dan WITH CHECK agar owner tidak diganti. Gunakan revoke default grants, least privilege, dan schema private tidak exposed. FORCE RLS tidak menghalangi superuser/BYPASSRLS: jangan mengandalkannya jika DATABASE_URL memakai postgres admin.

Role helper trusted `private.has_role(role)` security definer memakai search_path kosong, schema-qualified SQL, pemilik terkendali, EXECUTE hanya role yang membutuhkan; hindari recursion RLS user_roles. Cross-user dan privilege escalation test harus dijalankan memakai runtime role nyata, bukan semua test melalui admin.

## 11. Transaksi dan consistency

### Verification

Claim request operation → commit → call AI → validate result → transaksi singkat insert case/result/evidence/rule snapshots dan update operation terminal → commit. Bila history NOT_REQUIRED, update cache terminal saja. Bila persistence gagal, gunakan hasil tervalidasi dalam cache aman/ephemeral selama mungkin; jangan false saved. Retry dengan key sama hanya mencoba persistence dari hasil cache, bukan inference kedua. Jika tidak ada hasil recoverable, kirim outcome yang jujur; exactly-once inference tidak dijamin tanpa dukungan AI.

### Community

Publish transaction memvalidasi owner/revision/preview/consent, create/update post, consume preview dan update case state. Vote upsert/delete transaction dengan constraint unique dan owner ban. Moderation transaction memverifikasi expected_revision/lease, append decision, mutate canonical state, insert outbox + audit. Network indexing berlangsung setelah commit.

### Quiz

Validasi seluruh question/option/module/version → score server → insert attempt/answers atomic. Unique submission key mencegah duplicate attempt. Client tidak mengirim score/is_correct. Publish modul memastikan minimal satu lesson dan jumlah opsi/jawaban benar sesuai desain single-choice.

## 12. Storage dan retensi

| Bucket | Akses | Purpose | Retensi draft |
|---|---|---|---|
| verification-inputs | Private | Raw screenshot opt-in | ≤24 jam; default tidak upload |
| community-previews | Private | Derivative sanitized preview | 15 menit bila belum dipublikasi; published derivative mengikuti consent/post |
| contribution-evidence | Private | Lampiran bukti | Sesuai status/consent; tidak unlimited tanpa policy |
| profile-assets | Private | Avatar | Sampai diganti/dihapus |
| learning-assets | Public hanya bila konten kurasi memang publik | Materi | Mengikuti versi modul |

Object path dibentuk server: `{user_id}/{case_id}/{asset_id}.webp` untuk private verification; client tidak bebas memilih bucket/path. Signed URL TTL maksimal 5 menit dan tidak melebihi remaining asset life. URL tidak disimpan sebagai canonical path atau ditulis ke log. Signed URL yang sudah terbit dapat berlaku sampai expiry kecuali object dihapus; withdrawal gambar perlu penghapusan object/revocation strategy yang diuji.

Deletion flow: tandai inaccessible/tombstone + enqueue cleanup → hapus object derivative/raw → revoke consent/sync delete bila perlu → hapus children/parent sesuai retention → audit metadata minimum. Storage API dipakai untuk object deletion, bukan hanya SQL DELETE metadata bucket. Retry idempotent; orphan cleanup membandingkan referensi database dengan object terkelola tanpa menyapu file lain.

Background cleanup aktif **sebelum** mengaktifkan opt-in storage. Screenshot bukan “boleh simpan sekarang, retention nanti”. Idempotency terminal expires 10 menit, history draft 90 hari; jobs/test clock memverifikasi keduanya. Verified locks/retention exception memerlukan policy eksplisit; tidak ada retensi tanpa batas yang tersirat.

## 13. Folder migration dan urutan dependency

```text
supabase/
├── config.toml
├── migrations/
│   ├── <timestamp>_private_schema_runtime_grants.sql
│   ├── <timestamp>_profiles_roles.sql
│   ├── <timestamp>_request_operations.sql
│   ├── <timestamp>_verification_results_evidence.sql
│   ├── <timestamp>_assets_previews_consent.sql
│   ├── <timestamp>_community_votes_contributions.sql
│   ├── <timestamp>_moderation_outbox_audit.sql
│   ├── <timestamp>_learning_quiz_progress.sql
│   ├── <timestamp>_cross_domain_foreign_keys.sql
│   └── <timestamp>_storage_policies.sql
├── seeds/{learning_modules,learning_lessons,quiz_questions,quiz_options}.sql
├── seed.sql
└── tests/{schema,ownership,roles,quiz,community,retention}.test.sql
```

Setiap timestamp unik yang dibuat CLI; `<timestamp>` bukan nama final. RLS/grants diterapkan bersamaan dengan tabel terkait, bukan menunggu semua tabel terbuka. Circular refs ditambahkan setelah tabel tersedia. Private roles dibuat operator/migration sesuai izin environment; password tidak di SQL repository. pgcrypto/pg_trgm opsional hanya bila fungsi diperlukan; vector tidak ditambahkan.

Seed utama SQL standar: jangan memakai `\ir` psql meta-command secara membabi buta pada runner yang tidak mendukungnya. Pilih `seed.sql` terkonsolidasi atau `sql_paths` yang didukung versi Supabase CLI; verifikasi pada clean reset. Seed idempotent dengan ID/slug stabil; tidak menambah pengguna lewat INSERT manual auth.users. Test users dibuat via Auth admin API lokal/test dengan cleanup terkontrol.

## 14. Setup database ringkas dan referensi operasional

Langkah executable terpusat di [setup](../development/setup.md): install/pin CLI → local init/start → tulis migration → lint/reset pada DB lokal disposable → seed/test runtime role → buat dev/staging project → link → review migration diff → backup → satu operator push → smoke. Cloud tidak dimodifikasi oleh paket ini.

Connection URL harus dari dashboard Connect, bukan hostname rekaan. Runtime persisten direct atau session pooler; migration direct bila dapat dijangkau. Transaction pooler memerlukan konfigurasi prepared statement/session-state yang kompatibel dengan driver; jangan menganggap `statement_cache_size=0` sendirian menyelesaikan semua kombinasi ORM/driver. TLS verification, bounded pool dan transaction-local claims diuji.

## 15. Test dan definition of done

Wajib menguji: clean schema from zero; FK/check/unique; profile signup; role escalation; cross-user history/child/file; RLS via non-admin role; quiz keys tidak bocor; votes no self/duplicate; stale preview; concurrent moderation revision; stale UPSERT setelah DELETE; retention/asset cleanup; cache user isolation; claims reset pada pooled connection; database rollback setelah child insert gagal; restore backup rehearsal.

Schema tidak dinyatakan selesai hanya karena diagram ada. Bukti selesai adalah SQL migration dapat dijalankan dari database kosong, seed dan test lulus, grants/role runtime benar, application contract sesuai kolom dan lifecycle, serta cleanup berjalan. Dokumen ini tidak mengklaim SQL tersebut sudah dieksekusi.

## Penyesuaian persistence untuk schema AI unggahan

VerificationResponse dan Evidence mempunyai additionalProperties=false. Evidence mewajibkan seluruh field id, claim_id, source_type, publisher, title, url, published_at, retrieved_at, excerpt, relevance, authority, recency, stance dan verification_status. published_at wajib hadir tetapi boleh null. Evidence tidak mempunyai field provenance pada schema unggahan; provenance lokal harus disimpan terpisah di kolom metadata Product, tidak dimasukkan ke evidence_json seolah-olah field upstream. status evidence menerima VERIFIED/REVIEWED/UNVERIFIED. Kolom nullable pada rancangan DB bukan izin menerima response upstream yang tidak lengkap: validasi penuh sebelum insert. Model/prompt/pipeline version tidak disediakan secara eksplisit sebagai top-level field schema ini; jangan mengarang nilainya. Rulebook menyediakan corpus_versions dan statistik retrieval, bukan daftar lengkap per-rule match; tabel match snapshot hanya diisi bila kontrak baru menyediakan detail tersebut. result_json mempertahankan 26 field schema; execution_mode Product disimpan terpisah agar history simulasi tetap berlabel.

## Lampiran historis utuh — Pasted markdown(3).md

**NON-NORMATIVE / ARSIP.** Isi berikut dipertahankan tanpa pemangkasan untuk audit detail sumber. Ini bukan spesifikasi aktif. Arsitektur monorepo AI, endpoint direct, status CURRENT, enum, nama tabel, resep SQL, metode vote, policy history/retensi dan perintah setup di dalamnya dapat bertentangan dengan keputusan terbaru; gunakan bagian aktif dokumen dan ADR. Jangan menjalankan perintah arsip atau mengklaim angka benchmark/test sebagai hasil pemeriksaan saat ini.

<details>
<summary>Buka seluruh isi sumber terdahulu</summary>

~~~~text
**# WaspadAI — Database Architecture and Setup**

**\*\*Status:\*\*** Target Architecture  

**\*\*Database:\*\*** Supabase PostgreSQL  

**\*\*Authentication:\*\*** Supabase Auth  

**\*\*File Storage:\*\*** Supabase Storage  

**\*\*Vector Storage:\*\*** PostgreSQL pgvector  

**\*\*Primary Backend:\*\*** FastAPI  

**\*\*Last Updated:\*\*** 14 September 2026  

**---**

**## 1. Tujuan**

Dokumen ini menjelaskan rancangan database WaspadAI, meliputi:

\- Struktur tabel.

\- Relasi antarentitas.

\- Authentication dan authorization.

\- Row Level Security.

\- Penyimpanan screenshot dan evidence.

\- History verifikasi.

\- Rulebook dan evidence retrieval.

\- Six-dimension assessment.

\- Materi Pelajari.

\- Quiz dan progress.

\- Koneksi antarpengguna.

\- Contribution dan moderation.

\- Verified Community RAG.

\- Migration, seed, testing, dan deployment.

\- Setup Supabase lokal dan cloud.

Database digunakan sebagai sumber data terstruktur. Seluruh orkestrasi AI tetap dijalankan melalui backend FastAPI.

**---**

**# 2. Keputusan Arsitektur**

**## 2.1 Supabase digunakan untuk**

\- PostgreSQL database.

\- Authentication.

\- Row Level Security.

\- File storage.

\- Audit data.

\- Vector dan text-search index.

\- Database migration.

**## 2.2 FastAPI digunakan untuk**

\- Validasi Supabase JWT.

\- Verification orchestration.

\- Vision processing.

\- Rulebook retrieval.

\- Evidence retrieval.

\- Six-dimension assessment.

\- Moderation workflow.

\- Community RAG indexing.

\- Akses menggunakan service-role untuk proses internal.

**## 2.3 Android tidak boleh**

\- Menggunakan Supabase service-role key.

\- Menyimpan \`WASPADAI\_API\_KEYS\`.

\- Menyimpan Groq, Tavily, atau provider AI API key.

\- Menulis langsung ke tabel moderation.

\- Menambahkan data langsung ke Verified Community RAG.

\- Membaca screenshot milik pengguna lain.

**---**

**# 3. Folder Database**

\`\`\`text

supabase/

├── config.toml

├── seed.sql

│

├── migrations/

│   ├── 202609140001\_extensions\_and\_types.sql

│   ├── 202609140002\_profiles\_and\_roles.sql

│   ├── 202609140003\_verification.sql

│   ├── 202609140004\_rulebook\_and\_evidence.sql

│   ├── 202609140005\_learning\_and\_quiz.sql

│   ├── 202609140006\_connections.sql

│   ├── 202609140007\_contributions\_and\_moderation.sql

│   ├── 202609140008\_community\_rag.sql

│   ├── 202609140009\_storage.sql

│   ├── 202609140010\_rls.sql

│   └── 202609140011\_indexes\_and\_functions.sql

│

├── seeds/

│   ├── assessment\_dimensions.sql

│   ├── rulebook.sql

│   ├── learning\_modules.sql

│   ├── quiz\_questions.sql

│   └── development\_users.sql

│

├── tests/

│   ├── 001\_schema.test.sql

│   ├── 002\_rls.test.sql

│   ├── 003\_verification.test.sql

│   ├── 004\_moderation.test.sql

│   └── 005\_community\_rag.test.sql

│

└── README.md

\`\`\`

Semua perubahan schema wajib dibuat sebagai migration. Jangan melakukan perubahan langsung pada database cloud tanpa mencatatnya dalam migration.

**---**

**# 4. Database Domain**

Database dibagi menjadi enam domain.

\| Domain | Tanggung jawab |

\|---|---|

\| Identity | Profile dan role pengguna |

\| Verification | Kasus verifikasi, assessment, evidence, dan history |

\| Knowledge | Rulebook dan sumber retrieval |

\| Learning | Pelajari, quiz, dan progress |

\| Social | Koneksi pengguna |

\| Community | Contribution, moderation, dan Verified Community RAG |

**---**

**# 5. Entity Relationship Diagram**

**## 5.1 Identity dan Verification**

\`\`\`mermaid

erDiagram

    AUTH\_USERS ||--|| PROFILES : owns

    PROFILES ||--o{ USER\_ROLES : has

    PROFILES ||--o{ VERIFICATION\_CASES : creates

    VERIFICATION\_CASES ||--o{ ASSESSMENT\_SCORES : contains

    VERIFICATION\_CASES ||--o{ VERIFICATION\_EVIDENCE : references

    VERIFICATION\_CASES ||--o{ RULEBOOK\_MATCHES : uses

    RULEBOOK\_CHUNKS ||--o{ RULEBOOK\_MATCHES : matched

\`\`\`

**## 5.2 Learning dan Social**

\`\`\`mermaid

erDiagram

    LEARNING\_MODULES ||--o{ LEARNING\_LESSONS : contains

    LEARNING\_MODULES ||--o{ QUIZ\_QUESTIONS : contains

    QUIZ\_QUESTIONS ||--o{ QUIZ\_OPTIONS : provides

    PROFILES ||--o{ QUIZ\_ATTEMPTS : completes

    QUIZ\_ATTEMPTS ||--o{ QUIZ\_ANSWERS : records

    PROFILES ||--o{ USER\_PROGRESS : owns

    PROFILES ||--o{ CONNECTIONS : participates

\`\`\`

**## 5.3 Contribution dan Community RAG**

\`\`\`mermaid

erDiagram

    PROFILES ||--o{ CONTRIBUTIONS : submits

    CONTRIBUTIONS ||--o{ CONTRIBUTION\_SOURCES : provides

    CONTRIBUTIONS ||--o{ MODERATION\_DECISIONS : reviewed

    CONTRIBUTIONS ||--o| COMMUNITY\_DOCUMENTS : becomes

    COMMUNITY\_DOCUMENTS ||--o{ COMMUNITY\_CHUNKS : contains

    PROFILES ||--o{ MODERATION\_DECISIONS : decides

\`\`\`

**---**

**# 6. PostgreSQL Extensions**

Migration pertama mengaktifkan extension yang dibutuhkan.

\`\`\`sql

create schema if not exists extensions;

create schema if not exists private;

create extension if not exists pgcrypto

with schema extensions;

create extension if not exists vector

with schema extensions;

create extension if not exists pg\_trgm

with schema extensions;

\`\`\`

Fungsi extension:

\| Extension | Fungsi |

\|---|---|

\| \`pgcrypto\` | Membantu pembuatan UUID dan fungsi kriptografi |

\| \`vector\` | Menyimpan embedding untuk Community RAG |

\| \`pg\_trgm\` | Fuzzy search dan pencarian teks |

Vector dimension harus mengikuti embedding model yang dipilih AI Engineer. Jangan membuat vector index sebelum dimension embedding ditetapkan.

**---**

**# 7. Enum Types**

\`\`\`sql

create type public.app\_role as enum (

    'user',

    'moderator',

    'admin'

);

create type public.verification\_input\_kind as enum (

    'text',

    'image',

    'screenshot'

);

create type public.verification\_source as enum (

    'manual',

    'overlay',

    'share\_intent',

    'web'

);

create type public.verification\_status as enum (

    'queued',

    'processing',

    'completed',

    'failed'

);

create type public.risk\_status as enum (

    'safe',

    'caution',

    'danger',

    'unknown'

);

create type public.evidence\_stance as enum (

    'supports',

    'refutes',

    'context',

    'unclear'

);

create type public.contribution\_status as enum (

    'draft',

    'submitted',

    'under\_review',

    'needs\_revision',

    'rejected',

    'verified',

    'archived'

);

create type public.connection\_status as enum (

    'pending',

    'accepted',

    'rejected',

    'blocked'

);

\`\`\`

**---**

**# 8. Identity Domain**

**## 8.1 \`profiles\`**

Menyimpan informasi aplikasi untuk pengguna Supabase Auth.

\| Kolom | Tipe | Aturan |

\|---|---|---|

\| \`id\` | \`uuid\` | Primary key dan FK ke \`auth.users.id\` |

\| \`display\_name\` | \`text\` | Nama pengguna |

\| \`avatar\_path\` | \`text\` | Path Storage, bukan public URL permanen |

\| \`bio\` | \`text\` | Opsional |

\| \`is\_active\` | \`boolean\` | Default \`true\` |

\| \`created\_at\` | \`timestamptz\` | Default \`now()\` |

\| \`updated\_at\` | \`timestamptz\` | Diperbarui trigger |

\`\`\`sql

create table public.profiles (

    id uuid primary key

        references auth.users(id)

        on delete cascade,

    display\_name text

        check (char\_length(display\_name) between 1 and 80),

    avatar\_path text,

    bio text check (char\_length(bio) <= 500),

    is\_active boolean not null default true,

    created\_at timestamptz not null default now(),

    updated\_at timestamptz not null default now()

);

\`\`\`

Email tidak perlu diduplikasi ke \`profiles\` karena sudah dikelola Supabase Auth.

**## 8.2 \`user\_roles\`**

\| Kolom | Tipe | Aturan |

\|---|---|---|

\| \`user\_id\` | \`uuid\` | FK ke profile |

\| \`role\` | \`app\_role\` | User, moderator, atau admin |

\| \`granted\_by\` | \`uuid\` | Admin pemberi role |

\| \`created\_at\` | \`timestamptz\` | Waktu pemberian |

Primary key:

\`\`\`text

(user\_id, role)

\`\`\`

Pengguna tidak boleh dapat mengubah role-nya sendiri.

**## 8.3 Trigger profile**

\`\`\`sql

create or replace function public.handle\_new\_user()

returns trigger

language plpgsql

security definer

set search\_path = ''

as $$

begin

    insert into public.profiles (

        id,

        display\_name

    )

    values (

        new\.id,

        coalesce(

            new\.raw\_user\_meta\_data ->> 'display\_name',

            'Pengguna WaspadAI'

        )

    );

    insert into public.user\_roles (

        user\_id,

        role

    )

    values (

        new\.id,

        'user'

    );

    return new;

end;

$$;

create trigger on\_auth\_user\_created

after insert on auth.users

for each row execute procedure public.handle\_new\_user();

\`\`\`

**---**

**# 9. Verification Domain**

**## 9.1 \`verification\_cases\`**

Tabel utama untuk History dan proses verifikasi.

\| Kolom | Tipe | Keterangan |

\|---|---|---|

\| \`id\` | \`uuid\` | Primary key |

\| \`user\_id\` | \`uuid\` | Pemilik kasus |

\| \`input\_kind\` | enum | Text, image, atau screenshot |

\| \`source\` | enum | Manual, overlay, share intent, atau web |

\| \`status\` | enum | Status pipeline |

\| \`input\_text\_redacted\` | \`text\` | Input yang sudah disanitasi |

\| \`ocr\_text\` | \`text\` | Hasil OCR |

\| \`storage\_object\_path\` | \`text\` | Lokasi gambar privat |

\| \`claim\_summary\` | \`text\` | Klaim utama yang diekstrak |

\| \`overall\_score\` | \`numeric\` | Nilai antara 0–1 |

\| \`risk\_status\` | enum | Safe, caution, danger, unknown |

\| \`result\_summary\` | \`text\` | Ringkasan hasil |

\| \`safe\_action\` | \`text\` | Tindakan aman |

\| \`model\_version\` | \`text\` | Model AI |

\| \`pipeline\_version\` | \`text\` | Versi pipeline |

\| \`trace\_id\` | \`text\` | Korelasi log |

\| \`error\_code\` | \`text\` | Error yang aman ditampilkan |

\| \`created\_at\` | \`timestamptz\` | Waktu dibuat |

\| \`completed\_at\` | \`timestamptz\` | Waktu selesai |

Raw screenshot tidak disimpan di kolom database. Database hanya menyimpan object path menuju Supabase Storage.

Constraint:

\`\`\`sql

check (

    overall\_score is null

    or overall\_score between 0 and 1

)

\`\`\`

Index utama:

\`\`\`sql

create index verification\_cases\_user\_created\_idx

on public.verification\_cases(user\_id, created\_at desc);

create index verification\_cases\_status\_idx

on public.verification\_cases(status);

\`\`\`

**## 9.2 \`assessment\_dimension\_definitions\`**

Menyimpan definisi enam dimensi resmi yang digunakan AI Engineer.

\| Kolom | Tipe |

\|---|---|

\| \`code\` | \`text\` primary key |

\| \`name\` | \`text\` |

\| \`description\` | \`text\` |

\| \`display\_order\` | \`smallint\` |

\| \`is\_active\` | \`boolean\` |

Tabel ini harus mempunyai tepat enam definition aktif untuk versi assessment yang digunakan.

Nama dimensi tidak boleh dibuat sendiri oleh frontend. Gunakan definisi resmi dari kontrak AI Shafwan.

**## 9.3 \`assessment\_scores\`**

Menyimpan hasil masing-masing dimensi.

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`verification\_case\_id\` | \`uuid\` |

\| \`dimension\_code\` | \`text\` |

\| \`score\` | \`numeric(5,4)\` |

\| \`risk\_status\` | \`risk\_status\` |

\| \`rationale\` | \`text\` |

\| \`created\_at\` | \`timestamptz\` |

Unique constraint:

\`\`\`text

(verification\_case\_id, dimension\_code)

\`\`\`

Satu verification case yang selesai harus mempunyai enam baris assessment sesuai versi pipeline.

**## 9.4 \`verification\_evidence\`**

Menyimpan evidence yang dipakai pada satu hasil verifikasi.

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`verification\_case\_id\` | \`uuid\` |

\| \`source\_url\` | \`text\` |

\| \`source\_domain\` | \`text\` |

\| \`title\` | \`text\` |

\| \`excerpt\` | \`text\` |

\| \`stance\` | \`evidence\_stance\` |

\| \`credibility\_score\` | \`numeric(5,4)\` |

\| \`published\_at\` | \`timestamptz\` |

\| \`retrieved\_at\` | \`timestamptz\` |

\| \`metadata\` | \`jsonb\` |

Jangan menyimpan seluruh isi artikel. Simpan metadata, URL, dan excerpt yang memang diperlukan.

**## 9.5 \`verification\_events\`**

Opsional tetapi direkomendasikan untuk debugging.

\`\`\`text

queued

processing\_started

ocr\_completed

claim\_extracted

rulebook\_retrieved

evidence\_retrieved

assessment\_completed

completed

failed

\`\`\`

Tabel ini tidak boleh dibaca langsung oleh pengguna biasa.

**---**

**# 10. Rulebook Domain**

**## 10.1 \`rulebook\_documents\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`title\` | \`text\` |

\| \`source\_name\` | \`text\` |

\| \`source\_url\` | \`text\` |

\| \`version\` | \`text\` |

\| \`is\_active\` | \`boolean\` |

\| \`published\_at\` | \`timestamptz\` |

\| \`created\_at\` | \`timestamptz\` |

**## 10.2 \`rulebook\_chunks\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`document\_id\` | \`uuid\` |

\| \`chunk\_index\` | \`integer\` |

\| \`content\` | \`text\` |

\| \`section\_title\` | \`text\` |

\| \`metadata\` | \`jsonb\` |

\| \`search\_vector\` | \`tsvector\` |

\`\`\`sql

search\_vector tsvector generated always as (

    to\_tsvector(

        'simple',

        coalesce(section\_title, '') || ' ' || content

    )

) stored

\`\`\`

Index:

\`\`\`sql

create index rulebook\_chunks\_search\_idx

on public.rulebook\_chunks

using gin(search\_vector);

\`\`\`

**## 10.3 \`verification\_rulebook\_matches\`**

Join table antara verification case dan rulebook chunk.

\| Kolom | Tipe |

\|---|---|

\| \`verification\_case\_id\` | \`uuid\` |

\| \`rulebook\_chunk\_id\` | \`uuid\` |

\| \`rank\` | \`integer\` |

\| \`relevance\_score\` | \`numeric(5,4)\` |

\| \`match\_reason\` | \`text\` |

Primary key:

\`\`\`text

(verification\_case\_id, rulebook\_chunk\_id)

\`\`\`

**---**

**# 11. Learning dan Quiz Domain**

**## 11.1 \`learning\_modules\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`slug\` | \`text unique\` |

\| \`title\` | \`text\` |

\| \`summary\` | \`text\` |

\| \`cover\_path\` | \`text\` |

\| \`difficulty\` | \`smallint\` |

\| \`display\_order\` | \`integer\` |

\| \`is\_published\` | \`boolean\` |

**## 11.2 \`learning\_lessons\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`module\_id\` | \`uuid\` |

\| \`title\` | \`text\` |

\| \`content\` | \`jsonb\` |

\| \`duration\_minutes\` | \`integer\` |

\| \`display\_order\` | \`integer\` |

\| \`is\_published\` | \`boolean\` |

Content dapat menggunakan JSON block:

\`\`\`json

[

  {

    "type": "heading",

    "text": "Kenali Social Engineering"

  },

  {

    "type": "paragraph",

    "text": "Materi pembelajaran..."

  }

]

\`\`\`

**## 11.3 \`quiz\_questions\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`module\_id\` | \`uuid\` |

\| \`lesson\_id\` | \`uuid nullable\` |

\| \`question\_text\` | \`text\` |

\| \`explanation\` | \`text\` |

\| \`difficulty\` | \`smallint\` |

\| \`is\_active\` | \`boolean\` |

**## 11.4 \`quiz\_options\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`question\_id\` | \`uuid\` |

\| \`option\_text\` | \`text\` |

\| \`is\_correct\` | \`boolean\` |

\| \`display\_order\` | \`smallint\` |

Kolom \`is\_correct\` jangan dikirim melalui public API sebelum jawaban pengguna disubmit.

**## 11.5 \`quiz\_attempts\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`user\_id\` | \`uuid\` |

\| \`module\_id\` | \`uuid\` |

\| \`score\` | \`numeric\` |

\| \`correct\_answers\` | \`integer\` |

\| \`total\_questions\` | \`integer\` |

\| \`started\_at\` | \`timestamptz\` |

\| \`completed\_at\` | \`timestamptz\` |

**## 11.6 \`quiz\_answers\`**

\| Kolom | Tipe |

\|---|---|

\| \`attempt\_id\` | \`uuid\` |

\| \`question\_id\` | \`uuid\` |

\| \`selected\_option\_id\` | \`uuid\` |

\| \`is\_correct\` | \`boolean\` |

\| \`answered\_at\` | \`timestamptz\` |

**## 11.7 \`user\_progress\`**

\| Kolom | Tipe |

\|---|---|

\| \`user\_id\` | \`uuid\` |

\| \`module\_id\` | \`uuid\` |

\| \`completed\_lesson\_count\` | \`integer\` |

\| \`progress\_percent\` | \`numeric(5,2)\` |

\| \`last\_lesson\_id\` | \`uuid\` |

\| \`completed\_at\` | \`timestamptz\` |

\| \`updated\_at\` | \`timestamptz\` |

Unique constraint:

\`\`\`text

(user\_id, module\_id)

\`\`\`

**---**

**# 12. Connections Domain**

**## 12.1 \`connections\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`requester\_id\` | \`uuid\` |

\| \`addressee\_id\` | \`uuid\` |

\| \`status\` | \`connection\_status\` |

\| \`created\_at\` | \`timestamptz\` |

\| \`responded\_at\` | \`timestamptz\` |

Constraint:

\`\`\`sql

check (requester\_id <> addressee\_id)

\`\`\`

Aplikasi harus mencegah duplicate connection meskipun posisi requester dan addressee dibalik.

Untuk MVP, pengecekan duplicate dapat dilakukan melalui backend transaction.

**---**

**# 13. Contribution dan Moderation Domain**

**## 13.1 \`contributions\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`submitted\_by\` | \`uuid\` |

\| \`title\` | \`text\` |

\| \`content\` | \`text\` |

\| \`category\` | \`text\` |

\| \`status\` | \`contribution\_status\` |

\| \`moderation\_note\` | \`text\` |

\| \`submitted\_at\` | \`timestamptz\` |

\| \`verified\_at\` | \`timestamptz\` |

\| \`created\_at\` | \`timestamptz\` |

\| \`updated\_at\` | \`timestamptz\` |

Pengguna hanya boleh mengubah contribution ketika berstatus:

\`\`\`text

draft

needs\_revision

\`\`\`

**## 13.2 \`contribution\_sources\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`contribution\_id\` | \`uuid\` |

\| \`source\_url\` | \`text\` |

\| \`title\` | \`text\` |

\| \`publisher\` | \`text\` |

\| \`attachment\_path\` | \`text\` |

\| \`metadata\` | \`jsonb\` |

**## 13.3 \`moderation\_decisions\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`contribution\_id\` | \`uuid\` |

\| \`moderator\_id\` | \`uuid\` |

\| \`previous\_status\` | \`contribution\_status\` |

\| \`decision\_status\` | \`contribution\_status\` |

\| \`reason\` | \`text\` |

\| \`created\_at\` | \`timestamptz\` |

History moderation tidak boleh dihapus ketika keputusan berubah.

Moderator harus membuat keputusan baru, bukan mengubah keputusan lama.

**---**

**# 14. Verified Community RAG**

**## 14.1 Prinsip**

Contribution tidak langsung menjadi knowledge.

\`\`\`text

Contribution

    ↓

Submitted

    ↓

Under Review

    ↓

Moderator Decision

    ↓

Verified

    ↓

Community Document

    ↓

Chunking

    ↓

Embedding dan Text Index

    ↓

Available for Retrieval

\`\`\`

**## 14.2 \`community\_documents\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`contribution\_id\` | \`uuid unique\` |

\| \`title\` | \`text\` |

\| \`content\` | \`text\` |

\| \`source\_metadata\` | \`jsonb\` |

\| \`is\_active\` | \`boolean\` |

\| \`verified\_by\` | \`uuid\` |

\| \`verified\_at\` | \`timestamptz\` |

\| \`indexed\_at\` | \`timestamptz\` |

\| \`index\_version\` | \`text\` |

**## 14.3 \`community\_chunks\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`uuid\` |

\| \`document\_id\` | \`uuid\` |

\| \`chunk\_index\` | \`integer\` |

\| \`content\` | \`text\` |

\| \`token\_count\` | \`integer\` |

\| \`metadata\` | \`jsonb\` |

\| \`embedding\` | \`extensions.vector\` |

\| \`search\_vector\` | \`tsvector\` |

\| \`created\_at\` | \`timestamptz\` |

Unique constraint:

\`\`\`text

(document\_id, chunk\_index)

\`\`\`

Full-text index:

\`\`\`sql

create index community\_chunks\_search\_idx

on public.community\_chunks

using gin(search\_vector);

\`\`\`

Vector index dibuat setelah dimension embedding dipastikan.

Contoh apabila model menghasilkan 1024 dimension:

\`\`\`sql

alter table public.community\_chunks

alter column embedding

type extensions.vector(1024);

create index community\_chunks\_embedding\_hnsw\_idx

on public.community\_chunks

using hnsw (embedding extensions.vector\_cosine\_ops);

\`\`\`

Jangan menggunakan angka \`1024\` sebelum dikonfirmasi oleh AI Engineer.

**## 14.4 Retrieval filter**

Backend hanya boleh mengambil community chunk apabila:

\`\`\`text

community\_documents.is\_active = true

contributions.status = verified

community\_documents.indexed\_at is not null

\`\`\`

Community evidence harus ditandai sebagai sumber komunitas agar tidak dianggap sama dengan sumber resmi.

**---**

**# 15. Audit Log**

**## 15.1 \`audit\_logs\`**

\| Kolom | Tipe |

\|---|---|

\| \`id\` | \`bigint identity\` |

\| \`actor\_id\` | \`uuid nullable\` |

\| \`action\` | \`text\` |

\| \`entity\_type\` | \`text\` |

\| \`entity\_id\` | \`uuid nullable\` |

\| \`request\_id\` | \`text\` |

\| \`ip\_hash\` | \`text\` |

\| \`metadata\` | \`jsonb\` |

\| \`created\_at\` | \`timestamptz\` |

Jangan menyimpan:

\- Raw access token.

\- API key.

\- Password.

\- Seluruh screenshot pengguna.

\- Informasi pribadi yang tidak dibutuhkan.

\- Full request body yang mengandung data sensitif.

Pengguna biasa tidak boleh membaca \`audit\_logs\`.

**---**

**# 16. Row Level Security**

RLS wajib aktif pada seluruh tabel yang dapat diakses frontend.

\`\`\`sql

alter table public.profiles enable row level security;

alter table public.verification\_cases enable row level security;

alter table public.assessment\_scores enable row level security;

alter table public.verification\_evidence enable row level security;

alter table public.quiz\_attempts enable row level security;

alter table public.quiz\_answers enable row level security;

alter table public.user\_progress enable row level security;

alter table public.connections enable row level security;

alter table public.contributions enable row level security;

alter table public.contribution\_sources enable row level security;

\`\`\`

**## 16.1 Role helper**

\`\`\`sql

create or replace function private.has\_role(required\_role public.app\_role)

returns boolean

language sql

stable

security definer

set search\_path = ''

as $$

    select exists (

        select 1

        from public.user\_roles

        where user\_id = (select auth.uid())

          and role = required\_role

    );

$$;

revoke all

on function private.has\_role(public.app\_role)

from public;

grant usage on schema private to authenticated;

grant execute

on function private.has\_role(public.app\_role)

to authenticated;

\`\`\`

Jangan menggunakan \`raw\_user\_meta\_data\` untuk menyimpan authorization role karena metadata tersebut dapat diperbarui pengguna.

**## 16.2 RLS matrix**

\| Resource | User | Moderator | Backend service |

\|---|---|---|---|

\| Profile sendiri | Read/Update | Read/Update sendiri | Full |

\| Verification sendiri | Read/Create | Read sendiri | Full |

\| Evidence kasus sendiri | Read | Read sendiri | Full |

\| Learning published | Read | Read | Full |

\| Quiz attempt sendiri | Read/Create | Read/Create sendiri | Full |

\| Progress sendiri | Read/Update | Read/Update sendiri | Full |

\| Connections terkait | Read/Update terbatas | Sama | Full |

\| Contribution sendiri | Read/Create/Update terbatas | Read semua | Full |

\| Moderation decisions | Tidak | Read/Create | Full |

\| Verified Community RAG | Read hasil published | Read | Full |

\| Audit logs | Tidak | Terbatas | Full |

**## 16.3 Contoh policy History**

\`\`\`sql

create policy "Users can read their own verification cases"

on public.verification\_cases

for select

to authenticated

using (

    (select auth.uid()) is not null

    and user\_id = (select auth.uid())

);

\`\`\`

**## 16.4 Contoh policy Contribution**

\`\`\`sql

create policy "Users can create their own contribution"

on public.contributions

for insert

to authenticated

with check (

    submitted\_by = (select auth.uid())

    and status in ('draft', 'submitted')

);

\`\`\`

**## 16.5 Contoh moderator policy**

\`\`\`sql

create policy "Moderators can read contributions"

on public.contributions

for select

to authenticated

using (

    (select private.has\_role('moderator'))

    or (select private.has\_role('admin'))

);

\`\`\`

Kolom yang digunakan dalam RLS seperti \`user\_id\`, \`submitted\_by\`, dan \`moderator\_id\` harus memiliki index.

**---**

**# 17. Supabase Storage**

**## 17.1 Bucket**

\| Bucket | Public | Isi |

\|---|---:|---|

\| \`verification-inputs\` | Tidak | Screenshot dan gambar verifikasi |

\| \`contribution-evidence\` | Tidak | Attachment contribution |

\| \`profile-assets\` | Tidak | Avatar pengguna |

\| \`learning-assets\` | Ya atau signed URL | Media pembelajaran |

**## 17.2 Object path**

Gunakan user ID sebagai folder pertama.

\`\`\`text

verification-inputs/

└── {user\_id}/

    └── {verification\_case\_id}/

        └── input.webp

\`\`\`

\`\`\`text

contribution-evidence/

└── {user\_id}/

    └── {contribution\_id}/

        └── evidence.pdf

\`\`\`

**## 17.3 Upload policy**

\`\`\`sql

create policy "Users upload their own verification files"

on storage.objects

for insert

to authenticated

with check (

    bucket\_id = 'verification-inputs'

    and (storage.foldername(name))[1] = (select auth.uid())::text

);

\`\`\`

**## 17.4 Read policy**

\`\`\`sql

create policy "Users read their own verification files"

on storage.objects

for select

to authenticated

using (

    bucket\_id = 'verification-inputs'

    and (storage.foldername(name))[1] = (select auth.uid())::text

);

\`\`\`

**## 17.5 Retention**

Untuk demo dan MVP:

\- Hapus temporary local screenshot setelah upload selesai.

\- Gunakan private bucket.

\- Gunakan signed URL dengan masa berlaku pendek.

\- Tentukan retention, misalnya 7–30 hari.

\- Berikan opsi pengguna menghapus history beserta file.

\- Backend cleanup worker menghapus orphan object.

**---**

**# 18. Environment Configuration**

**## 18.1 Backend \`.env\`**

\`\`\`dotenv

APP\_ENV=development

SUPABASE\_URL=https\://your-project.supabase.co

SUPABASE\_ANON\_KEY=replace-with-public-anon-key

SUPABASE\_SERVICE\_ROLE\_KEY=replace-with-server-only-key

SUPABASE\_JWT\_AUDIENCE=authenticated

DATABASE\_URL=postgresql://postgres\:password\@host:5432/postgres

WASPADAI\_API\_KEYS=replace-with-internal-service-key

\`\`\`

Rules:

\- \`SUPABASE\_SERVICE\_ROLE\_KEY\` hanya di backend.

\- \`DATABASE\_URL\` tidak boleh ada di Android atau web.

\- \`WASPADAI\_API\_KEYS\` bukan Supabase key.

\- Jangan memasukkan key asli ke \`.env.example\`.

\- Jangan mengirim \`.env\` melalui chat atau screenshot.

**## 18.2 Android \`local.properties\`**

\`\`\`properties

WASPADAI\_BASE\_URL=http\://127.0.0.1:8001

SUPABASE\_URL=https\://your-project.supabase.co

SUPABASE\_ANON\_KEY=replace-with-public-anon-key

\`\`\`

Supabase anon/publishable key memang dapat dipakai frontend jika seluruh RLS sudah benar. Keamanan tetap bergantung pada RLS, bukan dengan menyembunyikan anon key.

**## 18.3 Web \`.env.local\`**

\`\`\`dotenv

NEXT\_PUBLIC\_WASPADAI\_API\_URL=http\://localhost:8001

NEXT\_PUBLIC\_SUPABASE\_URL=https\://your-project.supabase.co

NEXT\_PUBLIC\_SUPABASE\_ANON\_KEY=replace-with-public-anon-key

\`\`\`

**---**

**# 19. Local Supabase Setup**

**## 19.1 Prerequisite**

Install:

\- Git.

\- Node.js.

\- Docker Desktop.

\- Supabase CLI sebagai project dependency.

Pastikan Docker Desktop sudah berjalan.

\`\`\`powershell

docker version

node --version

npm --version

\`\`\`

**## 19.2 Install Supabase CLI**

Jalankan dari root repository:

\`\`\`powershell

npm install supabase --save-dev

\`\`\`

Periksa versi:

\`\`\`powershell

npx supabase --version

\`\`\`

**## 19.3 Initialize**

Jalankan hanya jika folder \`supabase/\` belum tersedia.

\`\`\`powershell

npx supabase init

\`\`\`

Perintah tersebut membuat:

\`\`\`text

supabase/

└── config.toml

\`\`\`

**## 19.4 Start local stack**

\`\`\`powershell

npx supabase start

\`\`\`

Proses pertama dapat memerlukan waktu karena Docker mengunduh image.

Setelah selesai, CLI menampilkan:

\- API URL.

\- Database URL.

\- Studio URL.

\- Anon key lokal.

\- Service-role key lokal.

Jangan memasukkan output tersebut ke Git.

Supabase Studio lokal biasanya dapat dibuka melalui:

\`\`\`text

http\://localhost:54323

\`\`\`

**## 19.5 Check status**

\`\`\`powershell

npx supabase status

\`\`\`

**## 19.6 Apply migration**

Untuk menjalankan pending migration tanpa reset:

\`\`\`powershell

npx supabase migration up

\`\`\`

Untuk membangun ulang database lokal dari seluruh migration:

\`\`\`powershell

npx supabase db reset

\`\`\`

\`db reset\` bersifat destruktif terhadap database lokal. Jangan menjalankannya pada database cloud.

**## 19.7 Stop local stack**

\`\`\`powershell

npx supabase stop

\`\`\`

**---**

**# 20. Creating Migrations**

Buat migration:

\`\`\`powershell

npx supabase migration new profiles\_and\_roles

\`\`\`

Output:

\`\`\`text

supabase/migrations/

└── {timestamp}\_profiles\_and\_roles.sql

\`\`\`

Setelah mengisi SQL:

\`\`\`powershell

npx supabase db reset

\`\`\`

Periksa:

\`\`\`powershell

npx supabase db lint --local

\`\`\`

Aturan migration:

1\. Satu migration memiliki satu tujuan.

2\. Jangan mengubah migration yang sudah diterapkan ke cloud.

3\. Buat migration baru untuk perubahan berikutnya.

4\. Semua migration harus dapat dijalankan dari database kosong.

5\. Nama migration harus deskriptif.

6\. Tambahkan RLS bersamaan dengan tabelnya atau segera setelah tabel dibuat.

**---**

**# 21. Cloud Supabase Setup**

**## 21.1 Create project**

1\. Login ke Supabase Dashboard.

2\. Buat organization jika belum tersedia.

3\. Pilih **\*\*New Project\*\***.

4\. Gunakan nama seperti \`waspadai-demo\`.

5\. Pilih region yang dekat dengan pengguna.

6\. Buat database password yang kuat.

7\. Simpan password di password manager.

8\. Jangan mengirim password melalui chat.

**## 21.2 Authentication**

Untuk MVP:

\- Aktifkan email and password.

\- Tentukan apakah email confirmation diperlukan untuk demo.

\- Tambahkan redirect URL Android atau web jika memakai OAuth.

\- Jangan aktifkan provider yang tidak digunakan.

**## 21.3 Login CLI**

\`\`\`powershell

npx supabase login

\`\`\`

Gunakan personal access token dari akun Supabase. Token tidak boleh masuk Git.

**## 21.4 Link repository**

\`\`\`powershell

npx supabase link --project-ref YOUR\_PROJECT\_REF

\`\`\`

\`PROJECT\_REF\` adalah identifier project, bukan API key.

**## 21.5 Review migration**

\`\`\`powershell

npx supabase migration list

\`\`\`

Pastikan migration lokal dan remote tidak bertentangan.

**## 21.6 Push migration**

Hanya satu anggota tim yang menjalankan deployment database.

\`\`\`powershell

npx supabase db push

\`\`\`

Untuk memasukkan seed secara sengaja:

\`\`\`powershell

npx supabase db push --include-seed

\`\`\`

Jangan memasukkan development user atau data dummy sensitif ke production seed.

**---**

**# 22. Seed Data**

File utama:

\`\`\`text

supabase/seed.sql

\`\`\`

Contoh:

\`\`\`sql

\ir ./seeds/assessment\_dimensions.sql

\ir ./seeds/rulebook.sql

\ir ./seeds/learning\_modules.sql

\ir ./seeds/quiz\_questions.sql

\`\`\`

Seed harus bersifat idempotent bila memungkinkan.

\`\`\`sql

insert into public.assessment\_dimension\_definitions (

    code,

    name,

    description,

    display\_order

)

values

    ('dimension\_1', 'Replace from AI contract', 'Replace description', 1)

on conflict (code)

do update set

    name = excluded.name,

    description = excluded.description,

    display\_order = excluded.display\_order;

\`\`\`

Ganti placeholder enam dimensi setelah AI Engineer mengonfirmasi kontrak output final.

Jangan membuat nama enam dimensi hanya berdasarkan asumsi.

**---**

**# 23. Backend Integration**

Backend tidak boleh memakai Supabase service-role untuk semua request secara otomatis.

Gunakan dua jenis client:

\`\`\`text

Authenticated user client

└── Mengikuti JWT dan RLS pengguna

Service-role client

└── Hanya untuk worker, indexing, moderation, dan proses internal

\`\`\`

Recommended files:

\`\`\`text

backend/app/clients/

├── supabase\_user\_client.py

└── supabase\_admin\_client.py

\`\`\`

Backend flow:

\`\`\`text

Android

    ↓ Bearer JWT

FastAPI

    ↓ validate JWT

Verification Service

    ↓ AI pipeline

Repository

    ↓ controlled database operation

Supabase

\`\`\`

\`WASPADAI\_API\_KEYS\` tetap dipakai hanya pada internal API:

\`\`\`text

/api/internal/v1/verify/text

/api/internal/v1/verify/image

\`\`\`

Key tersebut tidak digunakan untuk menggantikan Supabase authentication pengguna.

**---**

**# 24. Android Integration**

Android menggunakan Supabase untuk:

\- Login.

\- Register.

\- Logout.

\- Refresh session.

\- Mengambil profile.

\- Opsional: membaca materi published.

Android sebaiknya menggunakan FastAPI untuk:

\- Verification.

\- History orchestration.

\- Quiz submission.

\- Contribution submission.

\- Connections.

\- Moderation-sensitive operations.

Walaupun Supabase menyediakan Data API langsung, business process WaspadAI tetap lebih aman dan konsisten melalui FastAPI.

Physical device menuju backend lokal:

\`\`\`powershell

adb reverse tcp:8001 tcp:8001

\`\`\`

Android API base URL:

\`\`\`text

http\://127.0.0.1:8001

\`\`\`

Supabase cloud tetap diakses melalui URL HTTPS project.

**---**

**# 25. Database Testing**

**## 25.1 Schema test**

Pastikan:

\- Semua tabel tersedia.

\- Semua foreign key tersedia.

\- Enum sesuai.

\- Unique constraint bekerja.

\- Check constraint menolak data invalid.

**## 25.2 RLS test**

Test minimal:

\- Pengguna A tidak dapat membaca history pengguna B.

\- Pengguna tidak dapat menjadikan dirinya moderator.

\- Pengguna tidak dapat membaca audit log.

\- Pengguna hanya dapat mengubah contribution miliknya.

\- Contribution rejected tidak masuk Community RAG.

\- Moderator dapat membaca moderation queue.

\- Anon user tidak dapat mengakses private table.

\- Storage object pengguna A tidak dapat dibaca pengguna B.

**## 25.3 Run test**

\`\`\`powershell

npx supabase test db

\`\`\`

Lint:

\`\`\`powershell

npx supabase db lint --local

\`\`\`

Reset and verify:

\`\`\`powershell

npx supabase db reset

npx supabase test db

\`\`\`

**---**

**# 26. Database Deployment Workflow**

\`\`\`text

Create branch

    ↓

Create migration

    ↓

Local db reset

    ↓

Database and RLS test

    ↓

Pull request

    ↓

Review

    ↓

Merge

    ↓

One owner runs db push

    ↓

Smoke test cloud

\`\`\`

Branch example:

\`\`\`text

feat/database-verification

feat/database-learning

feat/database-community-rag

fix/rls-verification-history

\`\`\`

Commit example:

\`\`\`text

feat(database): add verification and assessment schema

feat(database): add learning quiz and progress schema

feat(database): add contribution moderation workflow

feat(database): add verified community rag storage

fix(database): restrict verification history with rls

\`\`\`

**---**

**# 27. Team Ownership**

\| Aktivitas | Owner |

\|---|---|

\| Membuat Supabase project | PM/Technical |

\| Menyimpan project credential | PM/Technical |

\| Membuat migration | PM/Technical |

\| Menentukan assessment dimensions | AI Engineer |

\| Menentukan embedding dimension | AI Engineer |

\| Integrasi AI result ke database | AI Engineer |

\| RLS review | PM/Technical dan AI Engineer |

\| Moderation UI state | Product Designer |

\| \`db push\` ke cloud | PM/Technical |

\| Database smoke test | PM/Technical |

\| Validasi Community RAG | AI Engineer |

Product Designer tidak membutuhkan service-role key atau database password.

**---**

**# 28. Initial Setup Order**

Urutan setup paling aman:

1\. Install Docker Desktop.

2\. Install Supabase CLI sebagai dev dependency.

3\. Jalankan \`npx supabase init\`.

4\. Tambahkan migration extensions dan enums.

5\. Tambahkan profiles dan roles.

6\. Tambahkan verification schema.

7\. Tambahkan learning, quiz, dan progress.

8\. Tambahkan connections.

9\. Tambahkan contribution dan moderation.

10\. Tambahkan Community RAG.

11\. Tambahkan Storage bucket dan policy.

12\. Aktifkan seluruh RLS.

13\. Tambahkan seed.

14\. Jalankan \`npx supabase db reset\`.

15\. Jalankan database test.

16\. Buat Supabase cloud project.

17\. Link repository ke project.

18\. Review seluruh migration.

19\. Jalankan \`npx supabase db push\`.

20\. Lakukan smoke test Android dan backend.

Jangan mulai dari membuat tabel secara manual di cloud Dashboard.

**---**

**# 29. MVP Database Priority**

**## P0 — Wajib untuk verification demo**

\- \`profiles\`

\- \`user\_roles\`

\- \`verification\_cases\`

\- \`assessment\_dimension\_definitions\`

\- \`assessment\_scores\`

\- \`verification\_evidence\`

\- \`rulebook\_documents\`

\- \`rulebook\_chunks\`

\- \`verification\_rulebook\_matches\`

\- Storage \`verification-inputs\`

\- RLS

\- Audit minimum

**## P1 — Wajib untuk proposal completeness**

\- \`learning\_modules\`

\- \`learning\_lessons\`

\- \`quiz\_questions\`

\- \`quiz\_options\`

\- \`quiz\_attempts\`

\- \`quiz\_answers\`

\- \`user\_progress\`

\- \`connections\`

\- \`contributions\`

\- \`contribution\_sources\`

\- \`moderation\_decisions\`

\- \`community\_documents\`

\- \`community\_chunks\`

**## P2 — Setelah alur utama stabil**

\- Detailed verification events.

\- Vector HNSW optimization.

\- Evidence refresh scheduler.

\- Advanced audit dashboard.

\- Database analytics.

\- Automated data retention.

P1 tetap bagian dari MVP proposal, tetapi implementasinya dapat dibuat lebih tipis dibandingkan protection loop.

**---**

**# 30. Setup Validation Checklist**

**## Local**

\- [ ] Docker berjalan.

\- [ ] \`npx supabase --version\` berhasil.

\- [ ] \`npx supabase start\` berhasil.

\- [ ] Supabase Studio lokal dapat dibuka.

\- [ ] Semua migration dapat diterapkan dari database kosong.

\- [ ] Seed berhasil.

\- [ ] RLS test berhasil.

\- [ ] Storage policy berhasil.

\- [ ] Tidak ada credential asli di Git.

**## Cloud**

\- [ ] Supabase project dibuat.

\- [ ] Region project sesuai.

\- [ ] Authentication dikonfigurasi.

\- [ ] Repository terhubung dengan project yang benar.

\- [ ] Migration list sesuai.

\- [ ] \`db push\` berhasil.

\- [ ] RLS aktif.

\- [ ] Service-role key hanya berada di backend.

\- [ ] Android hanya memakai anon/publishable key.

\- [ ] Storage bucket verification bersifat private.

\- [ ] Backup credential disimpan secara aman.

**## Feature**

\- [ ] User dapat register dan login.

\- [ ] Profile otomatis dibuat.

\- [ ] Verification case tersimpan.

\- [ ] Enam assessment score tersimpan.

\- [ ] Evidence tersimpan dan terhubung ke case.

\- [ ] History hanya dapat dilihat pemilik.

\- [ ] Quiz attempt tersimpan.

\- [ ] Progress diperbarui.

\- [ ] Connection request tersimpan.

\- [ ] Contribution dapat dikirim.

\- [ ] Moderator dapat membuat keputusan.

\- [ ] Hanya contribution verified yang masuk Community RAG.

\- [ ] Screenshot milik pengguna lain tidak dapat diakses.

**---**

**# 31. Security Rules**

1\. Jangan commit database password.

2\. Jangan commit service-role key.

3\. Jangan mengirim key melalui chat.

4\. Jangan menggunakan service-role key di Android.

5\. Jangan menonaktifkan RLS untuk mempercepat demo.

6\. Jangan menyimpan raw screenshot tanpa retention policy.

7\. Jangan menggunakan user metadata sebagai sumber role.

8\. Jangan memasukkan unverified contribution ke Community RAG.

9\. Jangan mencatat JWT atau API key ke audit log.

10\. Rotasi key apabila pernah terpublikasi.

11\. Gunakan HTTPS untuk seluruh environment selain localhost.

12\. Batasi moderator dan admin berdasarkan kebutuhan.

**---**

**# 32. Definition of Done**

Database architecture dianggap selesai apabila:

\- [ ] Schema tersedia sebagai migration.

\- [ ] Database dapat dibuat ulang menggunakan \`db reset\`.

\- [ ] Authentication menghasilkan profile otomatis.

\- [ ] RLS mencegah cross-user access.

\- [ ] Verification menyimpan enam dimension score.

\- [ ] Rulebook dan evidence terhubung dengan hasil verifikasi.

\- [ ] History dapat ditampilkan per pengguna.

\- [ ] Learning, quiz, dan progress memiliki relasi yang benar.

\- [ ] Contribution memiliki moderation history.

\- [ ] Community RAG hanya berasal dari verified contribution.

\- [ ] Storage private policy telah diuji.

\- [ ] Backend berhasil menggunakan Supabase.

\- [ ] Android dapat login dan memanggil authenticated API.

\- [ ] Tidak ditemukan secret pada repository.

\- [ ] Local dan cloud migration berada dalam kondisi sinkron.

**---**

**# 33. Referensi Resmi**

\- Supabase Local Development:  

  https\://supabase.com/docs/guides/local-development

\- Supabase Database Migrations:  

  https\://supabase.com/docs/guides/deployment/database-migrations

\- Supabase Row Level Security:  

  https\://supabase.com/docs/guides/database/postgres/row-level-security

\- Supabase Vector Columns:  

  https\://supabase.com/docs/guides/ai/vector-columns

\- Supabase Storage Access Control:  

  https\://supabase.com/docs/guides/storage/security/access-control
~~~~

</details>
