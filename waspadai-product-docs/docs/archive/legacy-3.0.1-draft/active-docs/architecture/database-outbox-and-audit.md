# Database Outbox dan Audit

[Kembali ke indeks arsitektur database](database-architecture.md)

## 9. Outbox dan audit

### `private.outbox_events`

`id uuid PK`, `aggregate_type text NN`, `aggregate_id uuid NN`, `aggregate_revision bigint NN`, `event_type text NN CHECK COMMUNITY_UPSERT/COMMUNITY_DELETE`, `payload jsonb NN`, `content_hash text NN`, `state text NN CHECK PENDING/PROCESSING/DONE/FAILED/BLOCKED`, `attempts integer NN DEFAULT 0`, `available_at timestamptz NN`, `locked_by text ?`, `lease_until timestamptz ?`, `processed_at timestamptz ?`, `last_error_code text ?`, `created_at timestamptz NN`.

Unique `(aggregate_type,aggregate_id,aggregate_revision,event_type)`. Index `(state,available_at,id)` dan `(aggregate_id,aggregate_revision)`. Payload sanitized; jangan memasukkan raw image/PII/key. Worker menolak stale revision; retract membuat tombstone/revision lebih tinggi. AI perlu revision enforcement agar UPSERT lama tidak menghidupkan kembali record yang dicabut. Tanpa capability itu, indexing belum release-ready.

### `private.audit_logs`

`id bigint GENERATED ALWAYS AS IDENTITY PK`, `actor_id uuid ? FK profiles SET NULL`, `action text NN`, `resource_type text NN`, `resource_id text ?`, `request_id uuid ?`, `trace_id text ?`, `safe_metadata jsonb NN DEFAULT '{}'`, `created_at timestamptz NN`. Index `(resource_type,resource_id,created_at)` dan created_at untuk retensi. Tidak menyimpan alamat IP penuh bila tidak diperlukan; hash pun masih perlu kebijakan privasi. Role runtime insert-only untuk audit; penghapusan retensi oleh role terpisah yang diaudit.
