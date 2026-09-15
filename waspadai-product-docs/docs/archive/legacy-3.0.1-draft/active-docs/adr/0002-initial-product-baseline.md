# ADR-0002 - Baseline initial setup Product

Tanggal: 15 September 2026. Status: DECIDED untuk initial development. ADR ini memperinci ADR-0001 dan menggantikan label OPEN terdahulu untuk keputusan yang disebut eksplisit di bawah. Kontrak Product tetap `3.0.1-draft` sampai perubahan wire berikutnya memang diperlukan.

## Konteks

Repository Product aktual bernama `waspadai-products` dan Android sudah dibuat sebagai Gradle root langsung pada `frontend/`. Dokumentasi berada pada sibling directory `waspadai-product-docs` dan tidak boleh dimasukkan utuh ke commit Product. Initial setup memerlukan baseline dependency, auth, database, policy, dan perilaku aman ketika capability AI belum terbukti.

## Keputusan repository dan toolchain

1. Android Gradle root adalah `frontend/` dengan application module `frontend/app/`; tidak ada lapisan `frontend/android/`.
2. Namespace, Kotlin root package, dan applicationId Android adalah `id.waspadai.app`.
3. Baseline Android menggunakan `compileSdk=36`, `targetSdk=36`, dan `minSdk=26`. Minor SDK 36.1 tidak diperlukan sampai ada fitur yang membutuhkannya dan lolos review kompatibilitas perangkat.
4. Product backend menggunakan Python 3.11, metadata dependency pada `backend/pyproject.toml`, dan lock lintas platform `backend/uv.lock`. `uv.lock` di-commit; virtual environment tidak.
5. Akses database runtime menggunakan Psycopg 3 async pool dan SQL eksplisit pada repository. Migration SQL Supabase tetap sumber kebenaran; tidak menambah ORM pada baseline.
6. Auth MVP menggunakan Supabase Auth `get_user` dari Product backend. Product mengirim bearer user hanya ke Supabase Auth untuk validasi dan tidak meneruskannya ke AI. JWKS lokal dapat menjadi optimasi berikutnya setelah issuer/audience/revocation policy dibekukan dan diuji.
7. Dokumentasi lengkap dan kontrak tidak disalin permanen ke Product. CI mengambil artifact dokumentasi versi `3.0.1-draft`, memverifikasi hash setiap kontrak/fixture yang dipin, dan menyimpannya hanya pada directory artifact yang di-ignore.
8. Initial setup tidak mewajibkan Docker atau Supabase local stack. Database development memakai project Supabase hosted yang terpisah; migration remote hanya diterapkan oleh operator setelah target, dry-run, dan backup direview. CI PR tidak mengklaim migration/RLS runtime lulus sampai tersedia project test terisolasi dan runner yang tidak membutuhkan container.

## Keputusan policy Product

1. `HISTORY_POLICY=REVIEW_REQUIRED`: hanya verdict UNVERIFIED atau `requires_human_review=true` yang disimpan. Ini meminimalkan data privat dan sesuai fokus review.
2. Vote memakai `POST` sebagai atomic upsert dan `DELETE` untuk pembatalan. Perubahan ke PUT adalah breaking contract change.
3. `MAX_IMAGE_BYTES=8_000_000` decimal bytes. Proxy memberi ruang multipart di atas angka ini, tetapi validator aplikasi menegakkan batas byte dan pixel.
4. User dapat menghapus kasus privat. Kasus yang pernah menjadi verified community evidence harus lebih dulu withdrawn, asset disembunyikan/dihapus, dan DELETE/deindex acknowledgement direkonsiliasi. Audit minimum dapat dianonimkan sesuai retensi; 403 lock permanen bukan kebijakan final.
5. Trace upstream diperlakukan tidak tepercaya. Product hanya menyimpan/menampilkan allowlist ID korelasi dan metadata versi yang dikonfirmasi; prompt, raw provider body, input penuh, token, signed URL, dan detail pipeline sensitif tidak disimpan di log.
6. `STORE_SCREENSHOTS_ENABLED=false` dan `COMMUNITY_RAG_SYNC_ENABLED=false` tetap fail-closed sampai transport consent/cleanup serta indexing/retract contract tersedia dan teruji.

## Sikap terhadap gap upstream

1. Remote release memerlukan full commit SHA, OpenAPI byte hash, deployment version, reachable internal base URL, dan key dari secret manager. Short SHA atau branch `main` tidak cukup.
2. Product selalu mengirim `X-Waspadai-API-Key`. Upstream diminta membuat header/security scheme required dalam kontrak; sampai itu terjadi enforcement dibuktikan oleh negative runtime smoke.
3. Product menganggap endpoint inference upstream tidak idempotent. Kegagalan setelah dispatch menjadi `UNKNOWN_OUTCOME`; tidak ada automatic inference retry.
4. Synchronous verification dipakai untuk MVP dengan deadline terdokumentasi. Jika proxy/hosting tidak mampu, tim membuat versi Product API baru untuk async job; tidak mengubah semantik diam-diam.
5. Community indexing tetap off sampai tersedia PUT/DELETE versioned dengan event ID, canonical ID, revision, content hash, duplicate/stale rejection, acknowledgement, status/reconciliation, dan deletion guarantee.
6. Hanya output upstream yang lolos schema ketat dan privacy projection boleh dipersist sebagai hasil remote. Remote error tidak pernah dialihkan otomatis ke mock.

## Konsekuensi

Baseline menjadi sederhana dan eksplisit: satu Android project, satu workflow lock Python, satu driver database, dan Supabase-hosted auth validation. Biayanya adalah auth request tambahan pada MVP, CI membutuhkan lokasi artifact dokumentasi dan checksum archive yang dikonfigurasi operator, serta verifikasi migration/RLS runtime belum otomatis tanpa project test terisolasi. Tidak menjalankan local stack tidak sama dengan migration berhasil. Perubahan atas keputusan di ADR ini harus memperbarui dokumen aktif, contract/test yang terdampak, serta rollback plan.
