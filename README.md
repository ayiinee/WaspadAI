# WaspadAI Product

Repository ini berisi aplikasi Android dan Product API untuk WaspadAI. Product API memegang workflow pengguna, validasi Supabase Auth, dan data Product; service AI adalah layanan HTTP terpisah dan tidak di-clone atau dijalankan dari repository ini.

Anggota tim baru mulai dari [panduan setup dan handoff](TEAM_SETUP.md). Panduan tersebut menjelaskan tool, akses dokumentasi/secret, perintah verifikasi backend dan Android, serta batas fitur yang belum diimplementasikan.

## Struktur awal

- `frontend/`: Android Gradle root, package `id.waspadai.app`.
- `backend/`: FastAPI Python 3.11, dikelola dengan `uv`.
- `supabase/`: migration SQL Product yang forward-only.
- `contracts/documentation-artifact.lock.json`: pin untuk artifact dokumentasi, bukan salinan dokumentasi atau kontrak sumber.

## Menjalankan backend

1. Jika `.env` belum ada, salin `.env.example` menjadi `.env`; jika sudah ada, review nilainya tanpa menimpa file lokal.
2. Masuk ke `backend/`, lalu jalankan `uv sync --locked` (Python 3.11 dipilih dari `.python-version`). Pada PowerShell mesin ini, `py -m uv sync --locked` juga tersedia.
3. Jalankan `uv run python run_server.py` atau `py -m uv run python run_server.py`.
4. Periksa `http://127.0.0.1:8001/api/health`.

Mode default AI adalah `mock`; production menolak mode tersebut. Credential Supabase dan AI tidak boleh dimasukkan ke commit atau diteruskan ke Android.

## Dokumentasi sebagai artifact

Dokumentasi lengkap berada di repository `waspadai-product-docs`. Product CI mengunduh bundle versi yang dipin secara eksplisit, memeriksa SHA-256 bundle dan checksum file kontrak di dalamnya, lalu mengekstraknya hanya ke `.artifacts/`. Sebelum publish artifact CI pertama, lengkapi URL dan checksum pada `contracts/documentation-artifact.lock.json`; placeholder sengaja membuat fetch gagal daripada memakai versi dokumentasi yang tidak diketahui.

## Supabase

`supabase/migrations/` adalah sumber schema yang dapat dieksekusi. Initial setup tidak mewajibkan Docker atau local Supabase stack: gunakan project Supabase development yang terpisah, setelah target dan credential dikonfirmasi. CLI 2.117.0 dipin pada `package-lock.json`; gunakan `npm ci` dari root sebelum `npx --no-install supabase`. Jangan menjalankan `supabase db push` ke cloud tanpa dry-run, project ref, backup, dan operator yang disetujui. Langkah dan batasannya ada di `supabase/README.md`.
