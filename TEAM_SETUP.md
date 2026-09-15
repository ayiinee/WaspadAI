# Panduan setup WaspadAI Product untuk tim

Panduan ini dimulai dari checkout repository sampai backend dan Android **scaffold** dapat dijalankan untuk development. Baseline: Android `id.waspadai.app`/SDK 36, Product API Python 3.11 dengan `uv`, Supabase hosted development, dan AI sebagai service HTTP terpisah. Tidak perlu Docker atau menjalankan repository AI.

> Batas saat ini: backend baru menyediakan `/api/health` dan `/api/ready`; Android masih template Compose. Login, pengiriman teks/gambar, history, dan integrasi AI end-to-end belum tersedia. Build yang berhasil bukan berarti fitur Product sudah bisa dipakai pengguna.

## 0. Yang harus disiapkan maintainer sebelum handoff penuh

| Kebutuhan | Kondisi/penyerahan yang benar |
|---|---|
| Repository Product | Beri tim akses baca ke `https://github.com/ayiinee/WaspadAI.git` dan tentukan branch/commit baseline yang disepakati. |
| Dokumentasi sumber kebenaran | Terbitkan artifact rilis `waspadai-product-docs` versi `3.0.1-draft`, lalu pin URL asset dan SHA-256 ZIP di `contracts/documentation-artifact.lock.json`. Saat file ini ditulis, kedua nilai tersebut masih kosong; CI dokumentasi belum bisa lulus. Paket dokumentasi tidak dimasukkan ke commit Product. |
| Supabase | Siapkan project **development** terpisah, project ref, Auth URL, publishable key, dan koneksi PostgreSQL untuk role runtime `product_app`; berikan secret hanya melalui secret manager tim. Tentukan operator migration. |
| Android | Beri akses ke HP demo atau emulator untuk smoke perangkat. Unit test/build tidak membutuhkan perangkat. |
| AI remote | Baru diperlukan ketika adapter Product benar-benar diimplementasikan: base URL internal, key backend, full commit SHA, dan hash kontrak dari owner AI. Jangan clone atau jalankan repository AI untuk setup Product. |

Jika dokumentasi belum dirilis atau Supabase belum disiapkan, anggota tim tetap dapat menjalankan scaffold offline pada langkah 1–5. Jangan mengisi placeholder dengan URL/key tebakan.

## 1. Temukan sumber informasi yang tepat

Setelah clone, mulai dari `README.md` ini dan panduan ini. Source code dan konfigurasi yang *executable* berada di Product; keputusan produk/kontrak lengkap berada pada artifact dokumentasi yang dipin, diekstrak ke `.artifacts/documentation/` (ignored). Baca dokumen aktif di dalam artifact dengan urutan berikut:

1. `README.md` dan `docs/README.md` untuk peta dokumentasi.
2. `docs/product/prd.md` untuk kebutuhan dan acceptance criteria.
3. `docs/adr/0001-product-ai-boundary.md` dan `0002-initial-product-baseline.md` untuk keputusan arsitektur/toolchain.
4. `docs/architecture/` untuk batas komponen, data, RLS, dan Android.
5. `docs/integration/ai-service.md` untuk HTTP Product→AI, timeout, dan batas kontrak upstream.
6. `docs/development/setup.md` untuk runbook lanjutan.
7. `contracts/product-api.openapi.yaml` dan `contracts/upstream/waspadai-ai.openapi.json` untuk kontrak **draft/snapshot**, bukan bukti endpoint sudah terimplementasi.

Arsip di `docs/archive/` adalah sumber historis non-normatif; jika berbeda, ikuti dokumen aktif dan ADR. Jangan menyalin folder dokumentasi atau kontrak lengkap ke repository Product.

## 2. Pasang dan verifikasi tool

Contoh perintah di bawah untuk Windows PowerShell. Pasang Git, `uv` **0.12.15** (versi CI saat ini), Node.js **22**/npm, JDK **21**, Android Studio dan Android SDK Platform **36**. Android Studio dapat mengelola SDK/ADB; Gradle **9.5.0** datang dari wrapper `frontend/gradlew.bat`, bukan instalasi Gradle global. Backend mengunci Python **3.11** melalui `backend/.python-version`; `uv` dapat menyiapkan environment project sesuai pin tersebut. Ikuti [instalasi uv resmi](https://docs.astral.sh/uv/getting-started/installation/) dan [instalasi Android Studio resmi](https://developer.android.com/studio/install.html), lalu periksa:

```powershell
git --version
uv --version
node --version
npm --version
java -version
adb version
```

Jika `uv` diinstal lewat pip tetapi executable belum ada di PATH, `py -m uv` dapat menggantikan `uv` pada perintah backend. Untuk macOS/Linux gunakan `./gradlew` dan shell setempat; fetch artifact tetap membutuhkan PowerShell (`pwsh`) atau langkah unduh/verifikasi ekuivalen yang disetujui tim.

## 3. Clone Product dan cek baseline

```powershell
git clone https://github.com/ayiinee/WaspadAI.git
Set-Location WaspadAI
git status --short
```

Working tree hasil clone seharusnya bersih. Jangan menambahkan `.env`, keystore, `.artifacts/`, `.venv/`, `node_modules/`, atau output build ke commit. Struktur yang penting: `backend/` untuk API dan test, `frontend/` sebagai **Gradle root** Android, `supabase/migrations/` untuk SQL Product, dan `contracts/documentation-artifact.lock.json` untuk pin dokumentasi.

## 4. Ambil dokumentasi versi yang dipin

Periksa `contracts/documentation-artifact.lock.json`. Jika `release_asset_url` atau `archive_sha256` masih `null`, berhenti di langkah ini dan minta maintainer menerbitkan rilis; jangan memakai ZIP/branch `main` tanpa pin. Setelah lock berisi URL dan hash rilis yang benar, dari root Product jalankan:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\fetch-documentation-artifact.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\.artifacts\documentation\scripts\verify-docs.ps1
```

Fetcher menolak overwrite destination yang sudah ada, memeriksa SHA-256 ZIP, versi, serta checksum kontrak/fixture yang dipin. Jika rilis dokumentasi bersifat private, maintainer harus memastikan mekanisme fetch terautentikasi tersedia sebelum langkah ini digunakan oleh tim; script sekarang tidak menerima token. Folder `.artifacts/` hanya cache lokal ignored.

## 5. Jalankan backend tanpa credential remote

Di root Product, salin `.env.example` ke `.env` **hanya jika `.env` belum ada**; jangan menimpa konfigurasi lokal orang lain. Untuk smoke offline, pertahankan `APP_ENV=development` dan `AI_SERVICE_MODE=mock`. Jangan taruh secret AI/Supabase di Android atau commit.

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item -LiteralPath .env.example -Destination .env }
Set-Location backend
uv sync --locked
uv lock --check
uv run ruff check .
uv run pytest
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload --loop app.uvicorn_loop:selector_loop_factory
```

Di terminal PowerShell kedua, periksa `Invoke-RestMethod http://127.0.0.1:8001/api/health`; respons `status=ok` membuktikan proses API hidup. Tanpa `DATABASE_URL`, `/api/ready` **wajar 503**. Saat database dapat dikoneksi, readiness bisa 200, tetapi `select 1` itu belum membuktikan migration/RLS atau fitur bisnis. Stop server dengan Ctrl+C. Jangan mencari/install dari file requirements terpisah: project dan CI memakai `backend/pyproject.toml` + `backend/uv.lock`.

## 6. Build dan jalankan Android scaffold

Buka folder `frontend/` (bukan root Product) sebagai project di Android Studio. Pastikan JDK 21 dan SDK 36 tersedia; namespace/applicationId ada di `frontend/app/build.gradle.kts`. Dari PowerShell:

```powershell
Set-Location frontend
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
adb devices
```

APK debug di `frontend/app/build/outputs/apk/debug/app-debug.apk`. Jika HP/emulator muncul sebagai `device`, Android Studio dapat menjalankan app atau `adb install .\app\build\outputs\apk\debug\app-debug.apk` dapat memasang APK. Jika daftar ADB kosong, build/unit test tetap valid tetapi smoke perangkat belum dilakukan. UI saat ini masih template, sehingga belum memanggil Product API/Supabase; jangan menafsirkan layar template sebagai alur aplikasi selesai.

## 7. Hubungkan Supabase development (operator saja)

Langkah ini **bukan** untuk setiap anggota tim dan membutuhkan target development yang disetujui. Baca `supabase/README.md` dan runbook database pada artifact. Dari root Product, operator memeriksa target tanpa mengubah schema:

```powershell
npm ci
npx --no-install supabase --version
npx --no-install supabase login
npx --no-install supabase link --project-ref REPLACE_WITH_CONFIRMED_DEV_PROJECT_REF
npx --no-install supabase migration list
npx --no-install supabase db push --linked --dry-run --skip-vault
```

Ganti placeholder dengan project ref **yang sudah diverifikasi**. Setelah backup dan review migration/RLS/grants, operator yang berwenang boleh menerapkan migration pada project development dengan `npx --no-install supabase db push --linked --skip-vault`, lalu lint dengan `npx --no-install supabase db lint --linked --fail-on error`. Jangan menjalankan `db reset --linked` atau push ke production untuk setup: reset remote menghapus data. Alur dry-run/push remote dijelaskan oleh [Supabase](https://supabase.com/docs/guides/local-development/cli-workflows).

Migration awal membuat role `product_app` **NOLOGIN** tanpa password. Operator menyiapkan LOGIN/credential runtime secara terpisah setelah migration berhasil; `DATABASE_URL` backend harus memakai role tersebut, sedangkan migrator/owner memakai credential lain. Isi `.env` lokal melalui secret manager dengan `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, dan `DATABASE_URL` development; jangan mencetak atau membagikan nilainya. `supabase/config.toml` tidak otomatis mengatur Auth/redirect pada project hosted. File pgTAP tetap ada, tetapi test migration/RLS runtime belum dijalankan oleh CI tanpa project test dan harness yang terisolasi.

## 8. Kriteria selesai untuk handoff saat ini

- Semua anggota bisa clone commit baseline, membaca panduan ini, dan memperoleh artifact dokumentasi **versi/hash yang sama**.
- `uv sync --locked`, Ruff, pytest, `/api/health`, `testDebugUnitTest`, dan `assembleDebug` lulus pada mesin tim.
- Operator memiliki project Supabase development yang tepat; migration head, role runtime, dan hasil test RLS user A/B dicatat sebelum tim mengklaim database siap.
- CI Product lulus setelah artifact dokumentasi dipublikasikan dan lock diisi. Saat ini CI tidak memiliki gate database runtime.
- Perangkat Android dan AI remote ditambahkan untuk smoke terpisah ketika fitur Product yang memakainya tersedia.

Jika syarat dokumentasi/Supabase belum dipenuhi, statusnya **scaffold development siap**, bukan aplikasi Product end-to-end siap. Catat blocker dan owner-nya pada handoff; jangan menyamarkan mock sebagai hasil AI live.
