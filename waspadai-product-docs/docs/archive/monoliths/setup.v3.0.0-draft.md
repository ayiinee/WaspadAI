# WaspadAI — Setup, Testing, Deployment and Demo Runbook

Versi 3.0.0-draft. Runbook TARGET. Paket berisi dokumentasi dan draft kontrak, **bukan scaffold aplikasi yang langsung runnable**. Perintah backend/Android/test/Compose di bawah hanya berlaku setelah file target diimplementasikan. Tidak ada database/deployment yang dibuat atau diubah saat menyusun paket ini.

## 1. Prasyarat dan versi

| Kebutuhan | Baseline kerja | Verifikasi |
|---|---|---|
| Git | Versi stabil tim | git --version |
| Python | 3.11 sesuai PRD lama | python --version |
| Android Studio/JDK/Gradle/AGP/Kotlin | Pilih kombinasi kompatibel lalu pin | Gradle wrapper + version catalog committed |
| Android SDK | compileSdk/targetSdk/minSdk diputuskan tim | Uji permission pada HP demo sebenarnya |
| Device/ADB | USB debugging pada HP demo | adb devices |
| Docker | Untuk local Supabase/container | docker version |
| Node/npm | Node22 baseline lama, bila web/CLI dipakai | node --version; npm --version |
| Supabase CLI | Pin versi yang diuji tim via dev dependency | npx supabase --version |
| AI Service | Remote URL+key+snapshot asli, atau mock | Bukan clone AI repo |

Versi Android/CLI/provider SDK mutakhir tidak dapat dicek lewat web pada penyusunan ini. Jangan memasukkan angka version terbaru hasil tebakan. Dependency lock dan Gradle wrapper yang dihasilkan project menjadi rujukan reproduksi; hanya satu package manager/lock per komponen.

## 2. Environment dan credential

Gunakan `.env.example` root sebagai kontrak nama config backend. Salin ke `.env` lokal dan isi hanya credential development. `.env`/signing keys/database password tidak di-commit, tidak ditempel di chat, tidak dicetak saat debugging. Product tidak membutuhkan key Groq/Tavily/Qdrant, weights atau Tesseract.

| Variabel | Fungsi | Tempat |
|---|---|---|
| APP_ENV | development/test/staging/production | Backend |
| AI_SERVICE_MODE | mock/remote; production remote wajib | Backend |
| AI_SERVICE_BASE_URL | Base URL tanpa suffix API | Backend |
| AI_SERVICE_API_KEY | Internal service key Shafwan | Secret backend |
| AI_SERVICE_*TIMEOUT* | Timeout phase/absolute sesuai integrasi | Backend |
| SUPABASE_URL | URL project dev/staging | Backend/Android sesuai environment |
| SUPABASE_PUBLISHABLE_KEY | Public client key; bukan secret server | Backend Auth helper/Android |
| SUPABASE_SERVICE_ROLE_KEY | Hanya operasi admin/storage yang dibatasi | Secret backend/worker |
| SUPABASE_AUTH_MODE | get_user atau jwks | Backend |
| SUPABASE_JWT_ISSUER/AUDIENCE | Claim validation untuk project yang benar | Backend |
| DATABASE_URL | DSN role product_app, bukan postgres admin | Secret backend |
| MIGRATION_DATABASE_URL | Role migration direct terpisah | Operator/CI migration terproteksi |
| HISTORY_POLICY | REVIEW_REQUIRED draft; ALL perlu review | Backend dan UI metadata |
| COMMUNITY_RAG_SYNC_ENABLED | False sampai kontrak indexing tersedia | Backend/worker |
| STORE_SCREENSHOTS_ENABLED | False default; consent per request tetap wajib bila true | Backend |
| MAX_IMAGE_BYTES | Draft konservatif8.000.000 | Backend |
| PRODUCT_API_BASE_URL | URL gateway Product | Android config public |

Alias lama WASPADAI_AI_* / WASPADAI_BASE_URL / SUPABASE_ANON_KEY harus dimigrasikan secara eksplisit jika code existing memakainya. Jangan memasang dua nama berbeda yang menunjuk environment berbeda tanpa validasi. Key publishable/anon dipilih sesuai SDK/runtime, bukan dipertukarkan berdasarkan nama saja.

Draft OpenAPI belum menyediakan transport consent untuk menyimpan screenshot. Oleh sebab itu STORE_SCREENSHOTS_ENABLED wajib tetap false sampai kontrak consent/asset diperluas dan diuji. Mengubah flag server saja tidak mengizinkan penyimpanan gambar. Kebijakan opt-in di database adalah rancangan lanjutan yang detailnya tetap dipertahankan.

## 3. Mulai repository Product

1. Pilih/create repo Product milik tim. Jika sudah ada, review working tree dan AGENTS/contribution rules terlebih dahulu; jangan overwrite source atau reset perubahan orang lain.
2. Salin tujuh dokumen dan kontrak ke path yang sama. ZIP ini menggunakan root `waspadai-product-docs/`; isi root itu ditempatkan ke root Product, bukan membuat nested repo.
3. Review ADR dan OPEN items. Isi README project dengan URL repo Product yang sebenarnya, owner, environment dan evidence status.
4. Scaffold Android dan Product FastAPI baru. Tidak menjalankan git clone waspadAI-demo untuk setup Product.
5. Tambahkan `.gitignore`, `.env.example`, dependency locks, tests dan CI sesuai struktur target.

## 4. Scaffold backend dan run lokal

Setelah `backend/pyproject.toml`/requirements.lock dan app ada, dari root repo:

```bash
python3 -m venv backend/.venv
source backend/.venv/bin/activate
python -m pip install -r backend/requirements.lock
uvicorn app.main:app --app-dir backend --host 127.0.0.1 --port 8001 --reload
```

Windows PowerShell:

```powershell
py -3.11 -m venv backend/.venv
.\backend\.venv\Scripts\Activate.ps1
python -m pip install -r backend/requirements.lock
uvicorn app.main:app --app-dir backend --host 127.0.0.1 --port 8001 --reload
```

Jangan mematikan execution policy organisasi untuk aktivasi; bila diblokir, jalankan interpreter `.venv` melalui path langsung sesuai izin. Dependency minimal target: FastAPI, Uvicorn, pydantic/settings, HTTPX, PostgreSQL driver, multipart parser, library JWT bila JWKS, validator image, pytest dan contract validator. Tidak ada AI SDK. Pilih dan lock versi sebelum command install; requirements.lock belum disertakan karena source scaffold belum dibangun.

Lifespan memuat config, fail-fast bila secret wajib hilang pada remote, membuat client/pool, dan menutup semuanya saat shutdown. `.env` harus dimuat eksplisit oleh config; command tidak diasumsikan otomatis membaca file tanpa implementasi.

## 5. Supabase lokal

Syarat: Docker berjalan dan project npm/CLI dependency dikelola tim. Instal `supabase` dev dependency versi yang disepakati lalu commit package-lock. Perintah contoh setelah dependency tersedia:

```bash
npx supabase --version
npx supabase init
npx supabase start
npx supabase status
```

`init` hanya jika config belum ada. Jangan mengganti `supabase/config.toml` existing tanpa review. Status menampilkan local credentials; jangan unggah output mentah atau screenshot-nya.

### Migration dan seed

```bash
npx supabase migration new profiles_roles
npx supabase migration up
npx supabase db lint --local
npx supabase test db
```

Isi migration menurut dependency order pada database doc. Setiap tabel dibuat bersama RLS/grants. Snapshot draft tidak sama dengan migration executable. Seed modul/lesson/quiz terkurasi; role moderator/test accounts tidak dimasukkan sembarangan ke produksi.

Perintah berikut **menghapus dan membangun ulang data database lokal**. Gunakan hanya setelah memastikan CLI menunjuk local disposable instance dan tidak ada data penting:

```bash
npx supabase db reset
```

Tujuannya membuktikan migration+seed dapat membangun schema dari nol. Jangan menjalankan reset terhadap cloud atau menggunakan credential production. Backup bila ragu; command tidak dijalankan oleh paket ini.

`seed.sql` harus SQL yang didukung runner. Hindari `\ir` kecuali runner memang psql. Alternatif beberapa file hanya melalui konfigurasi sql_paths yang diverifikasi pada versi CLI yang dipin.

### Runtime DB role

Operator menyiapkan `product_app` non-owner/NOBYPASSRLS dengan credential development. Migrator menggunakan role berbeda. Aplikasi menempel claims dari token tervalidasi dalam setiap transaksi secara LOCAL. Verifikasi RLS dengan user A/B melalui role aplikasi; menggunakan postgres admin pada semua test tidak membuktikan RLS.

## 6. Supabase cloud development/staging

1. Buat project development/staging tersendiri, region sesuai tim; simpan password di secret manager.
2. Aktifkan Auth yang digunakan, email confirmation dan redirect/deep link Android yang benar. Jangan mengubah policy production hanya agar demo lebih mudah.
3. Ambil URL/key/connection string dari dashboard resmi. Jangan merangkai hostname pooler sendiri.
4. Login/link CLI menggunakan akun operator dengan izin:

```bash
npx supabase login
npx supabase link --project-ref REPLACE_WITH_CONFIRMED_PROJECT_REF
npx supabase migration list
```

Placeholder project ref wajib diganti target yang sudah dicek. Review migration lokal/remote, backup dan PR. Hanya operator yang ditunjuk menjalankan perubahan cloud:

```bash
npx supabase db push
```

Push adalah write remote, bukan langkah diagnosis yang boleh dilakukan sembarangan. Seed cloud hanya jika sengaja disetujui dan tidak mengandung akun dummy/PII. Jangan mengedit migration yang sudah diterapkan; buat migration forward-only baru.

## 7. Connection strategy database

| Kondisi | Pilihan |
|---|---|
| Container persisten, direct reachable | Direct DB connection dengan TLS |
| Persitent tetapi direct IPv6 tidak tersedia | Session pooler yang tersedia di dashboard |
| Serverless/short-lived | Transaction pooler, setelah driver/ORM kompatibel diuji |
| Migration/backup | Direct connection bila tersedia; pilih alternatif supported lewat operator bila network tidak mendukung |

Proposal pool aplikasi 5+overflow5, statement timeout15s, pre-ping/recycle sesuai driver, request transaction singkat. Transaction pooler tidak boleh mengandalkan session state permanen; claims LOCAL tiap transaksi, prepared statements/caches disesuaikan driver dan versi pooler. Jangan menunggu AI 120s di dalam transaksi DB.

## 8. Auth validation implementation

MVP get_user: Product memakai Supabase Auth untuk memverifikasi access token dan memperoleh user trusted. Token invalid→401; Auth service unreachable→503, bukan menganggap token valid. Gunakan metadata server untuk role; jangan mengambil role dari raw_user_meta_data.

JWKS target: pin issuer/audience/algorithm allowlist, validate signature/exp/nbf/sub UUID, cache JWKS, refresh terbatas saat kid unknown, fail closed. Jangan membiarkan header alg menentukan algoritma tanpa allowlist. Status disabled/deleted user memerlukan policy pemeriksaan server, bukan sekadar JWT signature. HS256 legacy jika memang dipakai memerlukan keputusan/config terpisah; `SUPABASE_JWT_SECRET` tidak diasumsikan selalu dibutuhkan.

Android refresh single-flight satu kali pada401 Product. Refresh token tidak dikirim ke Product atau AI. Test expired/wrong issuer/audience/signature, revoked/disabled behavior yang dipilih, dan pemisahan 401 user vs503 internal AI key.

## 9. Remote/mock AI setup

Mock: mode mock, fixtures sintetis, UI SIMULASI, tidak butuh AI URL/key. Production startup harus menolak mock.

Remote: isi confirmed URL/key, salin OpenAPI asli ke contracts/upstream, isi full SHA/hash/version/source reference, validasi mappings dan pin. Snapshot lock masih UNVERIFIED pada paket ini.

Health GET tanpa key bila kontrak mengizinkan dapat diperiksa operator. Internal verification smoke harus menggunakan key server dari secret manager; hindari key literal dalam command history. Script test membaca env dan tidak mencetak header. Smoke dilakukan dengan input sintetis, batas biaya dan izin tim; paket ini tidak mengirim request inferensi live.

Tidak mengirim Supabase token kepada AI. Jangan menguji internal key dengan memasangnya di URL/browser/Android. AI401 berarti perbaiki konfigurasi server, bukan meminta user login ulang.

## 10. Android setup dan physical device

Setelah Android project tersedia, buka `frontend/android` di Android Studio, sync Gradle, pilih SDK/JDK sesuai pin. Buat local.properties tanpa commit credential/private paths:

```properties
PRODUCT_API_BASE_URL=http://127.0.0.1:8001
SUPABASE_URL=https://REPLACE_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=REPLACE_PUBLIC_KEY
```

Implementasi Gradle harus memuat properti ke BuildConfig/config sesuai variant; menulis properties sendiri tidak membuatnya otomatis tersedia. Jangan overwrite sdk.dir yang dibuat Android Studio.

USB debugging:

```bash
adb devices
adb reverse tcp:8001 tcp:8001
```

Device fisik melalui reverse dapat mengakses backend laptop127.0.0.1:8001. Emulator umumnya memakai10.0.2.2 untuk host; verifikasi environment emulator tim. Jangan memakai localhost device tanpa reverse dan mengira itu laptop.

Jika Supabase juga lokal, expose port Auth local yang benar via reverse yang spesifik, lalu arahkan Supabase URL Android sesuai port tersebut. Jangan membuka seluruh layanan DB ke jaringan publik. Alternatif gunakan Supabase cloud development untuk HP dan backend laptop.

Build/test dari folder Android:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Windows menggunakan gradlew.bat. Task names mengikuti plugin/variant nyata. Signing keystore/password tetap di secret manager, release upload artifact dikontrol tim. Periksa izin overlay, foreground capture, sistem consent, revoke, rotation/background, secure screen, stop dan cleanup pada perangkat demo.

## 11. Web opsional

Setelah web scaffold dan package-lock tersedia:

```bash
npm ci --prefix frontend/web
npm run dev --prefix frontend/web
```

Public config: NEXT_PUBLIC_PRODUCT_API_BASE_URL, NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY. Server secrets tidak memakai prefix NEXT_PUBLIC. Production CORS origin allowlist, tidak wildcard dengan credentials. Auth redirect harus sama dengan environment. Web AI existing tidak perlu dipindah untuk menjalankan Android Product.

## 12. Test strategy lengkap

| Lapisan | Cakupan | Gate |
|---|---|---|
| Unit Product | auth, roles, input, privacy projection, history policy, state machine, scoring | Setiap PR |
| AI adapter mock | field mapping, BOTH, image multipart, no unsupported OCR, unknown enums/error/deadline | Setiap PR |
| Contract | Product schema, examples, upstream hash/full pin, generated client compatibility | Setiap perubahan API |
| DB | PK/FK/check, clean migration/seed, runtime role/RLS, claims isolation, atomic transactions | Setiap schema PR |
| Security | cross-user, self-role, quiz key leak, self-vote, path traversal, file bombs, secret logs | Sebelum merge/rilis |
| Workflow | idempotency concurrent/replay/unknown/persistence-only retry; preview stale; moderation race; outbox stale version | Sebelum rilis |
| Android | permissions, capture cleanup, network/session, history, learning/community | HP final |
| Remote AI | staging contract and known golden cases | Setelah upstream pin/config berubah |
| Operations | retention/storage cleanup, outbox retry/deindex, restart/pool drain, rollback | Sebelum feature aktif |

Contoh setelah tests diimplementasikan:

```bash
python -m pytest backend/tests/unit backend/tests/contract
python -m pytest backend/tests/integration backend/tests/security
npx supabase test db
```

Negative scenarios wajib: key sama payload beda; process death setelah AI dispatch; DB commit gagal; terminal cache expired; delete history lalu replay; unknown dimension; upstream401/429; image valid header tetapi corrupt; preview PII; revoked RAG consent; quiz question dari modul lain; two-user connection reuse; object upload berhasil tetapi DB gagal; stale event UPSERT sesudah retract.

## 13. CI dan contract freeze

CI target: format/lint/typecheck→unit/contract→ephemeral DB migrate/seed/RLS→Android build/test→web lint/typecheck/build bila ada→secret scan→build immutable image. Remote AI smoke tidak otomatis pada semua PR yang belum dipercaya karena credential/cost; protected environment job hanya trusted branch/operator.

Export OpenAPI dari FastAPI yang sudah ada melalui `backend/scripts/export_openapi.py`, bandingkan normalized schema dengan kontrak draft yang disepakati. Jika mismatch, review perubahan; jangan overwrite baseline otomatis tanpa diff. Upstream snapshot immutable per rilis, original bytes hash dicatat. Jangan menghasilkan Kotlin dari schema AI internal; hanya Product contract.

Release gate awal dari paket: status upstream UNVERIFIED dan critical OPEN items harus diselesaikan. Lolos parser/examples dalam paket ini bukan lulus runtime/remote contract.

## 14. Deployment topology

Product deployment: reverse proxy→Product API, web opsional, worker, Supabase managed. AI deployment milik Shafwan terpisah. Jika satu VPS, gunakan private Docker network antar stack yang disepakati; jangan publish port internal AI tanpa kebutuhan. Jika beda host, gunakan HTTPS/internal VPN/allowlist sesuai operator; key tetap wajib.

Container Product proposal Python3.11 slim, non-root, read-only filesystem bila mungkin + tmpfs upload, no AI dependencies. Worker image sama entrypoint berbeda. Inference concurrency4/maxHTTP10 awal, DB pool terbatas. Proxy max request body9m sebagai contoh untuk image8.000.000+multipart; application limit tetap utama. Proxy timeout145s harus lebih besar Product135s; target hosting harus mendukung.

Urutan rilis:

1. Freeze contract/policy/config dan dapatkan candidate AI yang disepakati.
2. Backup DB, review expand-only migration, satu operator apply.
3. Seed learning content publik idempotent sesuai approval.
4. Deploy immutable Product image dengan secret staging/production masing-masing.
5. Run DB/health/auth/integration smoke dari jaringan Product.
6. Deploy Android candidate pointing Product staging; uji HP final.
7. Enable community workflows bertahap; indexing off sampai capability/tests selesai.
8. Run retention worker dan pastikan expiry sebelum storage opt-in diaktifkan.
9. Tag release dan catat Product version, migration head, AI full SHA/hash/API/model/rule versions.

## 15. Observability minimum

Structured logs: request_started, auth_validated, ai_request_started/completed/failed, history_persisted/failed, preview_created/published, moderation_decided, sync_requested/retry/completed, retention_cleanup. Field: timestamp, severity, route, request_id, AI request/trace ID bila aman, duration, safe error code. User ID dapat dipseudonimkan; jangan log input penuh, token, image, provider response debug, signed URL.

Metrics: request/error/latency per route, auth failures, upstream duration/status, schema invalid, concurrency saturation, DB pool wait, persistence failures, UNKNOWN_OUTCOME count, outbox backlog/oldest age/retries/deindex lag, expired assets not deleted. Alert berbasis nilai awal yang tim ukur; jangan mengarang SLO sudah tercapai.

Health `/api/health` liveness ringan tanpa memanggil provider. `/api/ready` minimal ready/degraded dengan detail internal terlindungi. AI down dapat membuat capability verification degraded sementara history tetap bisa dibaca; jangan mematikan seluruh proses karena health dependency flapping. Debug/prompt trace production off.

## 16. Troubleshooting

| Gejala | Diagnosis aman | Tindakan |
|---|---|---|
| HP gagal koneksi | Cek adb device/reverse dan base URL Product | Perbaiki routing debug, bukan disable TLS production |
| Semua Product401 | Cek issuer/audience/SDK session/clock | Refresh sekali; verifikasi project config |
| AI401/403 | Key/header/internal route/network | Operator rotasi/perbaiki secret server; jangan public fallback |
| AI504 | Deadline, dispatch outcome, provider latency | Tandai unknown, jangan blind inferensi ulang |
| History saved false | Policy NOT_REQUIRED vsSAVE_FAILED | Tampilkan alasan; persistence-only retry bila hasil cached |
| Cross-user read | Runtime role/grants/claims LOCAL/owner filter | Stop release, perbaiki dan negative test |
| Quiz key bocor | Data API/table grants/projection | Revoke raw access, gunakan safe DTO/view |
| Vote counts ganda | Unique/upsert transaction | Reconcile counts, regression concurrency |
| Community index stale | Outbox revision/ack/retract worker | Sembunyikan canonical, deindex/reconcile; disable unsafe retrieval |
| Asset lewat24h | Cleanup job/permission/storage API | Disable new opt-in sampai deletion berhasil |

## 17. Rollback dan incident minimum

Product rollback ke image immutable sebelumnya, bukan menghapus DB. Gunakan expand/contract migration; destructive down migration bukan rollback default. AI rollback dikelola Shafwan sendiri selama kontrak compatible. Feature flags dapat menonaktifkan publikasi/index/storage baru tanpa menghapus data.

Jika key bocor: batasi akses, tambah key baru AI, update Product secret, smoke, cabut key lama, periksa log tanpa menyebarkan credential. Jika data bocor: hentikan jalur terdampak, batasi akses, pertahankan audit minimum, koordinasi operator dan pemilik kebijakan; paket ini bukan prosedur hukum final.

Jika outcome inference tak diketahui: jangan restart worker untuk mengulang semua operasi secara buta. Rekonsiliasi berdasarkan ID/capability upstream; tanpa status API, laporkan keterbatasan dan biarkan pengguna memulai aksi baru secara sadar bila dipilih.

## 18. Demo runbook

Sebelum demo: HP/charger/kabel/USB, Wi-Fi dan hotspot cadangan, akun uji dua user/moderator tanpa data pribadi, confirm AI health/key dengan input sintetis, pastikan branch/tag/schema/config yang sama, clear private inputs, review empat golden cases, timeout labels, history policy dan mock badge.

Urutan: login→overlay→consent→crop→verifikasi OTP sintetis→narasi/dimensi/safe action→kasus insufficient evidence→history→lesson/quiz/progress→preview sanitized/consent→vote→moderator decision→index evidence **hanya jika nyata teruji**. Jangan memasukkan kredensial/OTP nyata dalam screenshot.

Fallback: video lokal dari rehearsal, screenshot hasil yang disanitasi, mock berlabel jelas jika dipilih. Tunjukkan bahwa simulasi bukan output live. Jika remote gagal, jelaskan kegagalan dan gunakan cadangan, tidak mengubah response mode diam-diam.

Rehearsal dua kali berturut pada HP final; rekam durasi end-to-end, version metadata, known limitations dan action owner. Setelah demo: revoke akun/key sementara bila diperlukan, bersihkan screenshot sesuai consent/retensi, pastikan asset/index demo tidak tertinggal di feed production.

## 19. Checklist release final

- [ ] Full upstream SHA/OpenAPI/hash telah diverifikasi; draft schema diganti/diuji.
- [ ] History policy, vote method, image field limit dan community visibility disepakati.
- [ ] Product source/scaffold/locks/migrations/tests benar-benar tersedia, bukan hanya dokumen.
- [ ] Runtime bukan postgres admin; RLS dan claims isolation lulus.
- [ ] Production remote, debug off, TLS verify on; APK/bundle/log bebas secret.
- [ ] Idempotency unknown/persistence-only/delete-replay tests lulus.
- [ ] Permission/capture/OCR/image/result/history berjalan di HP final.
- [ ] Learning/quiz/progress dan community consent/moderation sesuai scope.
- [ ] Storage cleanup teruji; RAG sync hanya aktif dengan kontrak/ack/retract yang nyata.
- [ ] Deployment rollback dan backup disiapkan.
- [ ] Dua rehearsal, video cadangan, signed build dan dokumen versi rilis tersedia.

## 20. Referensi verifikasi tim

Tautan berikut diwarisi dari sumber terdahulu dan belum dibuka ulang pada penyusunan paket: [Supabase local development](https://supabase.com/docs/guides/local-development), [migration](https://supabase.com/docs/guides/deployment/database-migrations), [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [database connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [JWT](https://supabase.com/docs/guides/auth/jwts), [Storage access control](https://supabase.com/docs/guides/storage/security/access-control). Gunakan docs resmi yang cocok dengan versi pinned saat implementasi; jangan menjalankan perintah arsip sebagai pengganti review versi.
