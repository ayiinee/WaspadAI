

# Setup dan Handoff Tim WaspadAI

Baseline: 15 September 2026. Source of truth integrasi AI adalah [`android-api-contract.md`](android-api-contract.md).

> Status saat ini: Android masih scaffold Compose dan backend hanya menyediakan health/readiness awal. Login, verifikasi teks/gambar, history, dan community belum boleh diklaim selesai tanpa bukti implementasi dan test.

## 1. Urutan membaca

1. [`README.md`](README.md) untuk topology dan status repository.
2. [`waspadai-product-docs/README.md`](waspadai-product-docs/README.md) untuk peta dokumentasi.
3. [`waspadai-product-docs/contracts/current/android-api-contract.md`](waspadai-product-docs/contracts/current/android-api-contract.md) untuk wire contract current.
4. [`waspadai-product-docs/docs/architecture/android-client.md`](waspadai-product-docs/docs/architecture/android-client.md) untuk boundary Android.
5. [`waspadai-product-docs/docs/api/README.md`](waspadai-product-docs/docs/api/README.md) untuk panduan integrasi.
6. [`waspadai-product-docs/docs/development/testing.md`](waspadai-product-docs/docs/development/testing.md) untuk test strategy.

Dokumen `future/`, `reference/`, dan `archive/` bukan petunjuk runtime current.

## 2. Tool

Gunakan Git, Node.js/npm, JDK 21, Android Studio/SDK sesuai Gradle project, dan Python 3.11 + `uv` hanya bila mengerjakan backend future. Verifikasi tool yang relevan:

```powershell
git --version
node --version
npm --version
java -version
adb version
uv --version
```

## 3. Verifikasi dokumentasi

Dari root repository:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\waspadai-product-docs\scripts\verify-docs.ps1
```

Gate ini memeriksa checksum kontrak current, fixture, link, JSON, dan isolasi kontrak future.

## 4. Build Android scaffold

```powershell
Set-Location frontend
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
adb devices
```

APK debug berada di `frontend/app/build/outputs/apk/debug/app-debug.apk`. Build sukses hanya membuktikan scaffold dapat dikompilasi; bukan bukti call Supabase atau AI telah berfungsi.

Konfigurasi Android yang boleh diberikan melalui local/build config: Supabase URL, publishable key, dan public AI base URL `https://waspadai.shafwan.digital`. Jangan memasukkan service-role key, database password, atau internal AI key.

## 5. Integrasi AI current

Android wajib:

- memastikan session Supabase aktif sebelum fitur digunakan;
- memanggil public singular path `/api/v1/verify/text` atau `/api/v1/verify/image`;
- tidak mengirim Bearer Supabase ke WaspadAI API;
- mengirim `output_mode=BOTH`;
- memakai JSON untuk teks dan multipart binary untuk screenshot;
- membaca direct response dan menampilkan `presentation.narrative.text`;
- memakai timeout 120 detik serta mencegah duplicate submit.

Lakukan smoke remote hanya dengan data sintetis dan catat request ID/status/durasi tanpa payload sensitif.

## 6. Backend preview future

Backend tidak dibutuhkan untuk call AI MVP current. Preview development/staging saat ini
menyediakan health/readiness serta vertical slice mock `POST /api/v1/verifications/text`,
`GET /api/v1/history`, dan `GET /api/v1/history/{case_id}`. Slice ini memvalidasi
Bearer Supabase, memakai role database `product_app`, mewajibkan `Idempotency-Key`,
dan menyimpan history owner-only sesuai policy `REVIEW_REQUIRED`.

Android tetap tidak memanggil endpoint tersebut. Mode mock harus selalu dilabeli sebagai
simulasi, bukan hasil AI live. Sebelum menjalankan history, isi
`HISTORY_CURSOR_SIGNING_KEY` lokal/deployment dengan secret acak yang berbeda dari
password database dan API key.

Untuk menjalankan backend future:

```powershell
Set-Location backend
uv sync --locked
uv lock --check
uv run ruff check .
uv run pytest
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Tanpa `DATABASE_URL`, `/api/ready` dapat mengembalikan 503. Untuk acceptance test RLS
hosted, buat dua user Auth development sementara lalu set `WASPADAI_RUN_LIVE_DB_TESTS=1`,
`WASPADAI_DB_TEST_USER_A_ID`, dan `WASPADAI_DB_TEST_USER_B_ID` sebelum menjalankan
`uv run pytest tests/test_live_supabase.py`; hapus user test setelahnya. Android tidak
dialihkan ke backend sebelum exit criteria pada dokumentasi future terpenuhi.

## 7. Supabase future (operator)

Migration/database diperlukan untuk fitur server-side future. Ikuti [`supabase/README.md`](supabase/README.md), gunakan project development terpisah, dry-run sebelum push, backup, dan credential operator dari secret manager. Jangan menjalankan reset/push production sebagai langkah onboarding.

## 8. Kriteria handoff

- Semua anggota membaca versi/checksum kontrak current yang sama.
- Quality gate docs dan Android build/unit test lulus.
- Owner Android/AI sepakat terhadap public endpoint, auth boundary, response shape, dan timeout.
- Remote smoke memakai data sintetis dan environment yang benar.
- Fitur future tetap feature-gated/tidak ditampilkan sampai backend, auth, storage, dan test tersedia.
