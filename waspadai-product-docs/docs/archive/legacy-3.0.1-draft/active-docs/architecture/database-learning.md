# Database Learning dan Quiz

[Kembali ke indeks arsitektur database](database-architecture.md)

## 8. Learning dan quiz

| Tabel | Kolom lengkap target | Constraint/index utama |
|---|---|---|
| learning_modules | id uuid PK; slug text NN; title text NN; summary text NN; cover_asset_id uuid?; difficulty smallint NN; display_order smallint NN; version integer NN; status text NN DRAFT/PUBLISHED/ARCHIVED; created_at/updated_at timestamptz NN | UNIQUE(slug,version); difficulty 1–5; version >0; index status/order |
| learning_lessons | id uuid PK; module_id uuid NN FK CASCADE; title text NN; body_md text NN; duration_minutes smallint NN; display_order smallint NN; is_published boolean NN; created_at/updated_at NN | UNIQUE(module_id,display_order); duration >0; sanitized rendering |
| quiz_questions | id uuid PK; module_id uuid NN FK CASCADE; lesson_id uuid? FK SET NULL; question_text text NN; explanation text NN; version integer NN; display_order smallint NN; is_active boolean NN; created_at/updated_at NN | UNIQUE(module_id,display_order,version); no answer disclosure API |
| quiz_options | id uuid PK; question_id uuid NN FK CASCADE; option_text text NN; display_order smallint NN; is_correct boolean NN | UNIQUE(question_id,display_order); canonical single-correct checked at publish/transaction trigger |
| lesson_progress | user_id uuid NN FK profiles CASCADE; lesson_id uuid NN FK lessons CASCADE; completed_at timestamptz NN | PK(user_id,lesson_id); idempotent completion |
| quiz_attempts | id uuid PK; user_id uuid NN FK profiles CASCADE; module_id uuid NN FK modules RESTRICT; module_version integer NN; submission_key uuid NN; total_questions integer NN; correct_answers integer NN; score numeric(5,2) NN; question_snapshot jsonb NN; completed_at/created_at NN | UNIQUE(user_id,module_id,submission_key); total>0; 0≤correct≤total; score 0–100 |
| quiz_answers | attempt_id uuid NN FK attempts CASCADE; question_id uuid NN FK questions RESTRICT; selected_option_id uuid NN FK options RESTRICT; is_correct boolean NN; answered_at timestamptz NN | PK(attempt_id,question_id); selected option harus milik question, question milik module/version attempt |

`learning_progress` adalah response/view agregasi, bukan tabel kedua yang menduplikasi lesson_progress. Persentase = completed published lessons / total published lessons ×100, zero bila belum ada lesson. Skor terbaik MAX(score), skor terakhir berdasarkan timestamp/id. Jika dibuat materialized cache, data sumber tetap progress/attempts dan rekonsiliasi wajib.

Jawaban benar sebaiknya disimpan di schema privat atau tidak diberikan SELECT langsung kepada authenticated. Bahkan jika API menyembunyikan `is_correct`, grant tabel yang terlalu luas masih bisa membocorkannya. Gunakan view/projection tanpa answer key dan jangan expose private schema ke PostgREST. Explanation quiz diberikan setelah submit sesuai workflow.
