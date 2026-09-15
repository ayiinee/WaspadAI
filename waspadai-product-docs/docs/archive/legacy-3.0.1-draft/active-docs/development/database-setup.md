# Database Setup dan Connection Strategy

[Kembali ke indeks setup](setup.md)

## 5. Supabase development hosted

Baseline initial setup tidak memakai local Supabase stack. Tim menyiapkan project hosted development yang terpisah dari staging/production; CLI 2.117.0 tetap dipin sebagai root dev dependency Product bersama `package-lock.json`. `supabase/config.toml` tidak menggantikan pengaturan Auth dan redirect hosted project di dashboard. Dari root Product, operator melakukan pemeriksaan tanpa menerapkan migration:

```bash
npm ci
npx --no-install supabase --version
npx --no-install supabase login
npx --no-install supabase link --project-ref REPLACE_WITH_CONFIRMED_DEV_PROJECT_REF
npx --no-install supabase migration list
npx --no-install supabase db push --linked --dry-run --skip-vault
```

Migration fondasi sudah ada pada `supabase/migrations/`; jangan membuat ulang migration awal. Dry-run hanya menampilkan migration yang akan diterapkan. Periksa project ref, backup, SQL/RLS/grants, dan daftar migration lokal/remote. Setelah review dan hanya pada project development yang memang boleh diubah, operator menjalankan `npx --no-install supabase db push --linked --skip-vault`. Jangan menjalankan `db reset --linked`: itu menghancurkan data remote. Jangan menambahkan seed/test account ke project hosted tanpa persetujuan.

Setelah migration berhasil, `npx --no-install supabase db lint --linked --fail-on error` dapat memeriksa fungsi pada project terhubung. File pgTAP di `supabase/tests/database/` tetap spesifikasi test, tetapi CLI `supabase test db` memakai runner container dan tidak menjadi langkah baseline. Runtime test role/RLS, user A/B, dan claim isolation baru dinyatakan lulus setelah dijalankan pada project test terisolasi dengan client/harness PostgreSQL yang disepakati. Tanpa itu, verifikasi SQL hanya statis dan belum membuktikan schema executable.

### Runtime DB role

Migration awal membuat `product_app` NOLOGIN/NOBYPASSRLS dengan grant terbatas tanpa password. Operator mengaktifkan LOGIN dan memberi credential development melalui secret manager setelah migration berhasil. Migrator menggunakan role berbeda. Aplikasi menempel claims dari token tervalidasi dalam setiap transaksi secara LOCAL. Verifikasi RLS dengan user A/B melalui role aplikasi; menggunakan postgres admin pada semua test tidak membuktikan RLS.

## 6. Supabase staging/production berikutnya

1. Buat project staging/production tersendiri, region sesuai tim; simpan password di secret manager.
2. Aktifkan Auth yang digunakan, email confirmation dan redirect/deep link Android yang benar. Jangan mengubah policy production hanya agar demo lebih mudah.
3. Ambil URL/key/connection string dari dashboard resmi. Jangan merangkai hostname pooler sendiri.
4. Login/link CLI menggunakan akun operator dengan izin dan target yang diperiksa:

```bash
npx --no-install supabase login
npx --no-install supabase link --project-ref REPLACE_WITH_CONFIRMED_PROJECT_REF
npx --no-install supabase migration list
npx --no-install supabase db push --linked --dry-run --skip-vault
```

Placeholder project ref wajib diganti target yang sudah dicek. Review migration lokal/remote, backup dan PR. Hanya operator yang ditunjuk menjalankan perubahan pada target yang diotorisasi:

```bash
npx --no-install supabase db push --linked --skip-vault
```

Push adalah write remote, bukan langkah diagnosis yang boleh dilakukan sembarangan. Seed cloud hanya jika sengaja disetujui dan tidak mengandung akun dummy/PII. Jangan mengedit migration yang sudah diterapkan; buat migration forward-only baru. Jalur tanpa local stack mengurangi verifikasi reproduksi dari nol; kompensasinya adalah test pada project development/preview terisolasi sebelum staging/production.

## 7. Connection strategy database

| Kondisi | Pilihan |
|---|---|
| Product process persisten, direct reachable | Direct DB connection dengan TLS |
| Persitent tetapi direct IPv6 tidak tersedia | Session pooler yang tersedia di dashboard |
| Serverless/short-lived | Transaction pooler, setelah driver/ORM kompatibel diuji |
| Migration/backup | Direct connection bila tersedia; pilih alternatif supported lewat operator bila network tidak mendukung |

Proposal pool aplikasi 5+overflow5, statement timeout15s, pre-ping/recycle sesuai driver, request transaction singkat. Transaction pooler tidak boleh mengandalkan session state permanen; claims LOCAL tiap transaksi, prepared statements/caches disesuaikan driver dan versi pooler. Jangan menunggu AI 120s di dalam transaksi DB.
