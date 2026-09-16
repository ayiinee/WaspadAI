# Supabase Product schema

Initial setup memakai project Supabase development yang terpisah; tidak membutuhkan local stack atau container runtime. Migration fondasi menyiapkan `public.profiles`, `public.user_roles`, dan `private.request_operations`; migration forward berikutnya menambahkan verification history, asset/consent, community, moderation/outbox/audit, learning/quiz, foreign key lintas domain, dan bucket Storage privat. Semua migration dapat menemukan role yang sudah diprovisioning; credential runtime tetap dikelola operator di luar migration.

CLI 2.117.0 tetap dipin untuk mengelola migration hosted project. Dari root Product, operator yang memiliki akses development menjalankan:

```powershell
npm ci
npx --no-install supabase --version
npx --no-install supabase login
npx --no-install supabase link --project-ref REPLACE_WITH_CONFIRMED_DEV_PROJECT_REF
npx --no-install supabase migration list
npx --no-install supabase db push --linked --dry-run --skip-vault
```

Perintah di atas belum menerapkan migration. Pastikan project ref, daftar migration, backup, dan izin operator benar. Hanya setelah review pada project development yang memang boleh diubah, operator menjalankan `npx --no-install supabase db push --linked --skip-vault`. Jangan gunakan `db reset --linked`: itu menghapus data pada project remote. Jangan menjalankan `db push` ke production sebagai langkah setup.

Setelah migration benar-benar diterapkan, lint dijalankan dengan password database migrator yang tersedia hanya pada environment operator. CLI membaca nama environment `SUPABASE_DB_PASSWORD`; jangan menaruh nilainya pada commit atau mencetaknya:

```powershell
$env:SUPABASE_DB_PASSWORD = '<value injected from the secret manager>'
npx --no-install supabase db lint --linked --fail-on error
Remove-Item Env:SUPABASE_DB_PASSWORD
```

File pgTAP di `tests/database/` tetap disimpan sebagai spesifikasi schema dan privilege, tetapi `supabase test db` tidak menjadi gate baseline tanpa runner container; validasi runtime RLS dan claim isolation dilakukan dengan harness Psycopg opt-in `backend/tests/test_live_supabase.py` pada project development terisolasi. Buat dua user Auth sementara setelah migration, set `WASPADAI_RUN_LIVE_DB_TESTS=1`, `WASPADAI_DB_TEST_USER_A_ID`, dan `WASPADAI_DB_TEST_USER_B_ID` pada environment operator, lalu hapus user tersebut setelah test.

Migration membuat atau mempertahankan `product_app` tanpa bypass RLS, superuser, atau hak membuat role. Jika role masih NOLOGIN, operator mengaktifkan LOGIN dan menaruh credential development pada secret manager hanya sesudah migration berhasil. Runtime Product memakai role tersebut, bukan owner/migrator, dengan trusted claim `sub` transaction-local. `supabase/config.toml` bukan pengganti konfigurasi Auth/redirect hosted project di dashboard.
