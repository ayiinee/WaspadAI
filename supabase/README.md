# Supabase Product schema

Initial setup memakai project Supabase development yang terpisah; tidak membutuhkan local stack atau container runtime. Migration pertama menyiapkan `public.profiles`, `public.user_roles`, dan `private.request_operations`, bersama trigger signup, role `product_app` non-login, grant terbatas, dan RLS pemilik. Domain lainnya akan ditambahkan melalui migration forward-only.

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

Setelah migration benar-benar diterapkan, `npx --no-install supabase db lint --linked --fail-on error` dapat memeriksa fungsi pada database yang terhubung. File pgTAP di `tests/database/` tetap disimpan sebagai spesifikasi test, tetapi `supabase test db` tidak menjadi gate baseline tanpa runner container; validasi runtime RLS dan claim isolation harus dilakukan pada project development terisolasi dengan client PostgreSQL/harness yang disetujui. Sampai itu dilakukan, migration dan RLS belum terverifikasi secara runtime.

Migration membuat `product_app` sebagai NOLOGIN/NOBYPASSRLS tanpa password. Operator mengaktifkan LOGIN dan menaruh credential development pada secret manager hanya sesudah migration berhasil. Runtime Product memakai role tersebut, bukan owner/migrator, dengan trusted claim `sub` transaction-local. `supabase/config.toml` bukan pengganti konfigurasi Auth/redirect hosted project di dashboard.
