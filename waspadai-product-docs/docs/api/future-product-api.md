# Status Product API Lanjutan

Status: `TARGET` — topology Product Backend sudah berlaku, endpoint di bawah belum seluruhnya runtime.

| Area | Path/kontrak target |
| --- | --- |
| Image | `POST /api/v1/verifications/image` |
| History delete | `DELETE /api/v1/history/{case_id}` |
| Preview/publish | `/api/v1/history/{case_id}/community-preview`, `/community` |
| Community | `GET /api/v1/community`, detail, vote |
| Internal AI | `/api/internal/v1/verify/text|image` + `community_evidence` |

Draft lengkap lama berada di [`contracts/future/product-api.openapi.yaml`](../../contracts/future/product-api.openapi.yaml), tetapi bukan bukti implementasi. Wire target yang berlaku harus mengikuti kontrak kanonik dan exported OpenAPI terbaru dari kode.

