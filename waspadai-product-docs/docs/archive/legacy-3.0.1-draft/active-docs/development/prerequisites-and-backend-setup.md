# Prerequisites, Environment, dan Backend Setup

[Kembali ke indeks setup](setup.md)

## 1. Prasyarat dan versi

| Kebutuhan | Baseline kerja | Verifikasi |
|---|---|---|
| Git | Versi stabil tim | git --version |
| Python | 3.11; `backend/.python-version` dan `uv.lock` dipin | `uv run python --version` |
| Android Studio/JDK/Gradle/AGP/Kotlin | JDK 17; Gradle wrapper dan version catalog di `frontend/` menentukan versi AGP/Kotlin | `gradlew.bat testDebugUnitTest` dan `assembleDebug` dari `frontend/` |
| Android SDK | compileSdk 36, targetSdk 36, minSdk 26 | Uji permission pada HP demo sebenarnya |
| Device/ADB | USB debugging pada HP demo | adb devices |
| Supabase development project | Hosted project terpisah dari production, tanpa local stack wajib | Project ref, Auth URL, publishable key, dan koneksi DB dari dashboard resmi |
| Node/npm | Node22 baseline lama, bila web/CLI dipakai | node --version; npm --version |
| Supabase CLI | 2.117.0, project dev dependency dan `package-lock.json` | `npx --no-install supabase --version` |
| AI Service | Remote URL+key+snapshot asli, atau mock | Bukan clone AI repo |

Dependency lock, version catalog, dan Gradle wrapper yang ada di Product menjadi rujukan reproduksi; hanya satu package manager/lock per komponen. Versi berikutnya dinaikkan melalui review kompatibilitas dan pengujian, bukan tebakan.

## 2. Environment dan credential

Gunakan `.env.example` root sebagai kontrak nama config backend. Salin ke `.env` lokal dan isi hanya credential development. `.env`/signing keys/database password tidak di-commit, tidak ditempel di chat, tidak dicetak saat debugging. Product tidak membutuhkan key Groq/Tavily/Qdrant, weights atau Tesseract.

| Variabel | Fungsi | Tempat |
|---|---|---|
| APP_ENV | development/test/staging/production | Backend |
| AI_SERVICE_MODE | mock/remote; production remote wajib | Backend |
| AI_SERVICE_BASE_URL | Base URL tanpa suffix API | Backend |
| AI_SERVICE_API_KEY | Internal service key Shafwan | Secret backend |
| AI_SERVICE_*TIMEOUT* | Timeout phase/absolute sesuai integrasi | Backend |
| SUPABASE_URL | URL hosted project dev/staging | Backend/Android sesuai environment |
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
2. Simpan dokumentasi modular dan kontrak penuh pada repository `waspadai-product-docs`. Product hanya mencatat URL/hash bundle rilis pada `contracts/documentation-artifact.lock.json`; CI mengekstraknya ke `.artifacts/` yang di-ignore.
3. Review ADR dan OPEN items. Isi README project dengan URL repo Product yang sebenarnya, owner, environment dan evidence status.
4. Buka Android Gradle root `frontend/` dan backend Python 3.11 `backend/`; package Android `id.waspadai.app` serta SDK 36 dipin pada source.
5. Pertahankan `.gitignore`, `.env.example`, `backend/uv.lock`, `package-lock.json`, tests dan CI Product.

## 4. Scaffold backend dan run lokal

Dengan `backend/pyproject.toml`, `.python-version`, `uv.lock`, dan app yang tersedia, dari root Product:

```bash
cd backend
uv sync --locked
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
uv run pytest
```

Windows PowerShell:

```powershell
Set-Location backend
py -m uv sync --locked
py -m uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
py -m uv run pytest
```

`uv` memilih Python 3.11 dari file pin, dan `.venv` tidak di-commit. Dependency awal: FastAPI, Uvicorn, pydantic-settings, HTTPX, Psycopg 3 pool, multipart parser, pytest, dan Ruff. Export OpenAPI implementasi baru masih jauh lebih kecil dari kontrak Product draft; diff kontrak harus direview seiring endpoint dibangun. Tidak ada AI SDK. Endpoint health sudah tersedia tanpa credential remote; readiness mengembalikan 503 sampai koneksi database benar-benar berhasil.

Lifespan memuat config, fail-fast bila secret wajib hilang pada remote, membuat client/pool, dan menutup semuanya saat shutdown. `.env` harus dimuat eksplisit oleh config; command tidak diasumsikan otomatis membaca file tanpa implementasi.
