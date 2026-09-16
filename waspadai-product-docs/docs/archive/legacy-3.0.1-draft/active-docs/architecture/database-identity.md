# Database Identity Domain

[Kembali ke indeks arsitektur database](database-architecture.md)

## 4. Identity domain

### 4.1 `public.profiles`

| Kolom | Tipe | Aturan |
|---|---|---|
| id | uuid NN | PK/FK auth.users(id) ON DELETE CASCADE |
| display_name | text NN | Trim 1–80 karakter |
| avatar_asset_id | uuid ? | FK stored_assets, nullable, hanya avatar yang diizinkan |
| bio | text ? | Maksimum 500 karakter; jangan tampilkan di komunitas secara otomatis |
| locale | text NN | Default id-ID; allowlist aplikasi |
| is_active | boolean NN | Default true; tidak dapat diubah user biasa |
| created_at, updated_at | timestamptz NN | Lifecycle |

Email tidak diduplikasi. Trigger profile setelah signup memakai security definer dengan search_path kosong dan identifier schema-qualified. Jangan percaya metadata `role`. Panjang display name dari metadata dinormalisasi agar signup tidak gagal akibat nilai invalid; fallback “Pengguna WaspadAI”. Circular FK avatar ditambahkan sesudah stored_assets dibuat.

### 4.2 `public.user_roles`

`user_id uuid NN FK profiles ON DELETE CASCADE`, `role text NN CHECK USER/MODERATOR/ADMIN`, `granted_by uuid ? FK profiles ON DELETE SET NULL`, `created_at timestamptz NN`. PK `(user_id, role)`. Signup memberi USER. Hanya admin melalui jalur server yang dapat menambah/mencabut role. Tabel ini bukan editable profile property.
