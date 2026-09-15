# Database Asset, Consent, dan Community

[Kembali ke indeks arsitektur database](database-architecture.md)

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
