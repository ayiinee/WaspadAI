# Database Verification dan Idempotency

[Kembali ke indeks arsitektur database](database-architecture.md)

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
