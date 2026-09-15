# Database Access, RLS, dan Transactions

[Kembali ke indeks arsitektur database](database-architecture.md)

## 10. RLS, grants dan role koneksi

Proposal runtime: direct PostgreSQL connection menggunakan dedicated `product_app` LOGIN, non-superuser, NOBYPASSRLS, bukan tabel owner. Credential disiapkan operator di luar Git. Table owner/migration role terpisah. Role pekerja dibatasi hanya tugasnya. Supabase service-role key bukan credential umum semua request.

Setelah token diverifikasi, setiap transaksi request memasang claims secara transaction-local melalui parameterized `set_config('request.jwt.claims', ..., true)` dan claim sub yang sesuai implementasi `auth.uid()` di runtime. Jangan menjalankan SET global pada pooled connection; user A bisa terbawa ke request B. Test memastikan claims hilang saat transaction berakhir. Jangan menerima claims JSON dari body user.

| Data | User melalui API | Moderator | Direct client/Data API |
|---|---|---|---|
| Profile | Read/update field whitelist sendiri | Tidak mengubah role lewat profile | Default dibatasi; explicit safe grants bila digunakan |
| Verification/result/evidence | Owner saja | Tidak otomatis mendapat akses seluruh history | Tidak ada write langsung |
| Preview/consent | Owner sesuai workflow | Hanya konten submitted | Tidak ada akses mentah |
| Post publik | Sanitized active DTO | Sama + jalur moderation | View aman saja bila diperlukan |
| Votes | Own vote + counts | Tidak expose identitas voter | Tabel raw tidak dibaca publik |
| Contribution | Owner DRAFT/NEEDS_EVIDENCE, submit via service | Queue/review yang diserahkan | Write raw dilarang |
| Decisions | Projection aman terkait kontribusi | Append lewat server | Tidak ada raw write |
| Learning | Published tanpa answer key | Manage via server | View published tanpa quiz keys |
| Progress/attempts | Own read; write via domain service | Agregat terkontrol | Score write dilarang |
| Roles/outbox/audit | Tidak | Jalur terkontrol, bukan akses client umum | Tidak ada |

Contoh policy ilustratif (migration harus menggunakan role/grant nyata):

```sql
alter table public.verification_cases enable row level security;
alter table public.verification_cases force row level security;
create policy own_case_read on public.verification_cases
for select to product_app
using (user_id = (select auth.uid()) and deleted_at is null);
create policy own_case_insert on public.verification_cases
for insert to product_app
with check (user_id = (select auth.uid()));
```

Child policy EXISTS parent owned. UPDATE policy perlu USING dan WITH CHECK agar owner tidak diganti. Gunakan revoke default grants, least privilege, dan schema private tidak exposed. FORCE RLS tidak menghalangi superuser/BYPASSRLS: jangan mengandalkannya jika DATABASE_URL memakai postgres admin.

Role helper trusted `private.has_role(role)` security definer memakai search_path kosong, schema-qualified SQL, pemilik terkendali, EXECUTE hanya role yang membutuhkan; hindari recursion RLS user_roles. Cross-user dan privilege escalation test harus dijalankan memakai runtime role nyata, bukan semua test melalui admin.

## 11. Transaksi dan consistency

### Verification

Claim request operation → commit → call AI → validate result → transaksi singkat insert case/result/evidence/rule snapshots dan update operation terminal → commit. Bila history NOT_REQUIRED, update cache terminal saja. Bila persistence gagal, gunakan hasil tervalidasi dalam cache aman/ephemeral selama mungkin; jangan false saved. Retry dengan key sama hanya mencoba persistence dari hasil cache, bukan inference kedua. Jika tidak ada hasil recoverable, kirim outcome yang jujur; exactly-once inference tidak dijamin tanpa dukungan AI.

### Community

Publish transaction memvalidasi owner/revision/preview/consent, create/update post, consume preview dan update case state. Vote upsert/delete transaction dengan constraint unique dan owner ban. Moderation transaction memverifikasi expected_revision/lease, append decision, mutate canonical state, insert outbox + audit. Network indexing berlangsung setelah commit.

### Quiz

Validasi seluruh question/option/module/version → score server → insert attempt/answers atomic. Unique submission key mencegah duplicate attempt. Client tidak mengirim score/is_correct. Publish modul memastikan minimal satu lesson dan jumlah opsi/jawaban benar sesuai desain single-choice.
