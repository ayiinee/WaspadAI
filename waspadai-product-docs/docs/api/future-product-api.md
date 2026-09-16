# Future Product API

Status: `FUTURE` — belum dapat dipanggil oleh Android MVP.

Product Backend future dapat menjadi gateway ketika aplikasi membutuhkan history, Storage, community, vote, ownership, dan moderation. Pada fase tersebut Android mengirim Bearer access token Supabase ke Product Backend, bukan ke public WaspadAI API.

## Candidate verification contract

Path candidate pada draft lama:

```text
POST /api/v1/verifications/text
POST /api/v1/verifications/image
```

Product Backend dapat membungkus hasil AI dengan metadata `history` dan object `result`. Bentuk ini berbeda dari direct response current dan membutuhkan DTO/version migration Android.

## Candidate product features

```text
GET    /api/v1/history
GET    /api/v1/history/{case_id}
DELETE /api/v1/history/{case_id}
POST   /api/v1/history/{case_id}/community-preview
POST   /api/v1/history/{case_id}/community
DELETE /api/v1/history/{case_id}/community
GET    /api/v1/community
GET    /api/v1/community/{case_id}
PUT    /api/v1/community/{case_id}/vote
DELETE /api/v1/community/{case_id}/vote
```

Daftar di atas mengikuti future section pada kontrak kanonik. Draft OpenAPI legacy memakai beberapa keputusan berbeda (termasuk vote `POST`); perbedaan tersebut wajib diselesaikan sebelum freeze. Sampai saat itu, tidak ada method community/vote yang dianggap current.

## Internal AI boundary

Product Backend future memanggil:

```http
POST /api/internal/v1/verify/text
POST /api/internal/v1/verify/image
X-Waspadai-API-Key: <service-secret>
```

Secret disimpan pada environment/secret manager server dan tidak pernah diteruskan ke Android. Backend meminta `output_mode=BOTH`, memakai timeout 120 detik, dan tidak meneruskan credential pengguna ke AI.

Draft lengkap yang diarsipkan sebagai future berada di [`contracts/future/product-api.openapi.yaml`](../../contracts/future/product-api.openapi.yaml). Status file tersebut bukan bukti implementasi atau kompatibilitas runtime.

