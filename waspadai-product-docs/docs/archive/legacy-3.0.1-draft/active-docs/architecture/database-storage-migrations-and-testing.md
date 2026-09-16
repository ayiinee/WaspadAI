# Database Storage, Migrations, dan Testing

[Kembali ke indeks arsitektur database](database-architecture.md)

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

VerificationResponse dan Evidence mempunyai additionalProperties=false. Evidence mewajibkan seluruh field id, claim_id, source_type, publisher, title, url, published_at, retrieved_at, excerpt, relevance, authority, recency, stance dan verification_status. published_at wajib hadir tetapi boleh null. Evidence tidak mempunyai field provenance pada schema unggahan; provenance lokal harus disimpan terpisah di kolom metadata Product, tidak dimasukkan ke evidence_json seolah-olah field upstream. status evidence menerima VERIFIED/REVIEWED/UNVERIFIED. Kolom nullable pada rancangan DB bukan izin menerima response upstream yang tidak lengkap: validasi penuh sebelum insert. Model/prompt/pipeline version tidak disediakan secara eksplisit sebagai top-level field schema ini; jangan mengarang nilainya. Rulebook menyediakan corpus_versions dan statistik retrieval, bukan daftar lengkap per-rule match; tabel match snapshot hanya diisi bila kontrak baru menyediakan detail tersebut. result_json mempertahankan 25 field schema; execution_mode Product disimpan terpisah agar history simulasi tetap berlabel.
