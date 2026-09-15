# WaspadAI — Product API and Remote AI Integration

Versi 3.0.0-draft. Batas Product–AI DECIDED; wire detail PROPOSED/REPORTED BASELINE hingga upstream OpenAPI asli dipin. Arsip kontrak Android lama di akhir dipertahankan utuh tetapi mode direct Android→AI **tidak berlaku** untuk Product ini.

## 1. Provenance dan release blocker

Percakapan melaporkan audit commit `b456057`, OpenAPI `0.7.0`; PRD v2.1 mengacu `a84ad57`; lampiran kontrak Android yang tersedia masih direct mode. Akses GitHub tidak berhasil karena web dinonaktifkan saat penyusunan. Oleh sebab itu tidak ada file `waspadai-ai.openapi.json` palsu di paket.

Sebelum remote integration dianggap siap, AI engineer memberikan: full commit SHA (bukan 7 karakter saja), salinan `contracts/openapi.json` asli, versi API/model/rulebook bila tersedia, URL internal yang bisa dijangkau deployment Product, key melalui secret manager, batas request/error, dan capabilities yang benar-benar aktif. Tim menghitung SHA-256 file asli, mengisi `contracts/upstream/contract-lock.json`, lalu menguji schema/runtime.

Tautan referensi untuk tim, belum diverifikasi ulang: [repo AI](https://github.com/ShafwanAdhi/waspadAI-demo), [kontrak Android](https://github.com/ShafwanAdhi/waspadAI-demo/blob/main/docs/android-api-contract.md), [OpenAPI AI](https://github.com/ShafwanAdhi/waspadAI-demo/blob/main/contracts/openapi.json). `main` bukan pin rilis; ubah referensi release ke full SHA setelah mendapatkannya.

## 2. Ownership kontrak

| Boundary | Pemilik | Auth | Kontrak |
|---|---|---|---|
| Android/Web→Product | Tim Product | Bearer Supabase access token | product-api.openapi.yaml |
| Product→AI | Shafwan/AI | X-Waspadai-API-Key | upstream file asli |
| Product→Supabase | Tim Product | JWT context/restricted DB role | Migration + repository policy |
| AI→provider/index | AI engineer | Provider-specific server secrets | Di repo AI, bukan Product |

Product tidak mengirim user_id/email/role/JWT/refresh token ke AI. `X-Request-ID` dipakai hanya jika upstream mendukung/mentoleransi header korelasi; ID AI yang dikembalikan tetap dipertahankan. Product tidak menggunakan public AI endpoint sebagai fallback jika key internal gagal.

## 3. Endpoint AI yang dilaporkan tersedia

| Method | Path AI | Pengguna |
|---|---|---|
| GET | /api/health | Health, bukan jaminan pipeline/provider siap |
| POST | /api/internal/v1/verify/text | Product backend dengan internal key |
| POST | /api/internal/v1/verify/image | Product backend dengan internal key |
| POST | /api/v1/verify/text | Public demo AI lama; tidak dipakai Android/Product normal |
| POST | /api/v1/verify/image | Public demo AI lama; tidak dipakai Android/Product normal |

Reported gap audit: header API key mungkin tertulis `required:false` dan belum `securitySchemes apiKey` pada snapshot lama meskipun runtime mewajibkan key. Jangan mengubah file snapshot asli untuk menutupi gap. Catat test requirement internal key di adapter/contract lock, dan minta upstream memperbaiki kontrak asli.

## 4. Request text dan mapping

Android mengirim `POST /api/v1/verifications/text`, JSON, Bearer token, `Idempotency-Key` UUID.

```json
{
  "text": "Pesan mengaku petugas bank dan meminta kode OTP segera.",
  "question": "Apakah tindakan yang diminta aman?",
  "source_url": null,
  "sender_context": "UNKNOWN_NUMBER"
}
```

| Field | Validasi Product | Pemetaan ke AI |
|---|---|---|
| text | Wajib; trim; 10–25.000 karakter; reject blank | text |
| question | Opsional/null; max 500; empty dihilangkan | question atau omitted agar default upstream |
| source_url | Opsional/null; max 2.048, HTTP(S), public URL | source_url |
| sender_context | Default UNKNOWN; enum di bawah | sender_context |
| output_mode | Tidak diterima sebagai pilihan client | Product menetapkan BOTH |
| user_id/role/save_history | Tidak diterima dari client | Tidak dikirim |
| Authorization/Idempotency-Key | Diproses Product | Tidak dianggap didukung AI; tidak diteruskan untuk menjanjikan dedup |

Sender enum reported: NOT_APPLICABLE, UNKNOWN_NUMBER, KNOWN_CONTACT, FORWARDED, SOCIAL_MEDIA, UNKNOWN. Nilai bebas seperti “Mengaku petugas bank” tidak dipakai pada field enum; masukkan konteks relevan ke text/question jika perlu, bukan mengubah schema.

Teks yang hanya URL dapat valid bila memenuhi aturan. Tolak localhost/loopback/link-local/private/reserved/internal destination sesuai threat model. Validasi fetch lengkap termasuk DNS rebinding/redirect berada pada service yang benar-benar membuka URL. Product tidak mengunduh arbitrary URL pada saat forwarding.

## 5. Request image dan OCR boundary

Android `POST /api/v1/verifications/image` menggunakan multipart:

| Field | Status | Catatan |
|---|---|---|
| image | Wajib | Binary JPEG/JPG, PNG, WEBP |
| question | Opsional | Max 500 karakter |
| output_mode | Server-only | Product menambah BOTH saat call AI |

Reported limit: maksimal 8 MB, masing-masing sisi minimal 64 dan maksimal 6.000 piksel, total ≤30.000.000 pixel. **OPEN satuan MB:** draft Product konservatif 8.000.000 bytes sampai upstream mengonfirmasi apakah 8 MiB. Jangan menganggap content-length cukup: batasi stream bytes, MIME signature, actual decoding dan decompression bomb. Proxy body limit sedikit di atas batas image untuk multipart overhead, sementara validator image tetap menolak file di atas cap.

Filename tidak menjadi path filesystem server. Streaming/temp file dibatasi; tempfile dihapus pada semua jalur. Strip EXIF/metadata, jangan menyimpan input tanpa consent. Tidak kirim Base64 dalam JSON.

**Batas draft storage:** kontrak verification saat ini belum menerima consent penyimpanan screenshot. Karena itu `STORE_SCREENSHOTS_ENABLED` harus tetap false pada baseline runnable. Kebijakan opt-in ≤24 jam dalam PRD/database adalah rancangan yang dipertahankan; sebelum diaktifkan, tim harus menambahkan transport consent/version yang eksplisit ke kontrak atau endpoint asset terpisah, memeriksa binding consent ke image digest, dan menambah test. Flag server saja tidak pernah dianggap consent pengguna.

`ocr_text`, `source_url`, `sender_context` untuk image **tidak didokumentasikan sebagai field upstream pada lampiran yang tersedia**. Draft Product image tidak menerima field tambahan tersebut. OCR Android tetap berguna untuk review/koreksi atau jalur text terpisah. Menambahkan metadata image membutuhkan versi kontrak baru yang disepakati, bukan menjejalkannya ke request atau prompt tersembunyi.

## 6. Response dan wrapper Product

Product success envelope:

```json
{
  "request_id": "b683ece9-2d46-4df6-9dac-a9de609af723",
  "status": "COMPLETED",
  "history": {
    "saved": true,
    "case_id": "241c1028-6d74-4149-a4e0-542b39c2186b",
    "save_reason": "UNVERIFIED",
    "community_eligible": true,
    "community_state": "PRIVATE"
  },
  "result": {
    "request_id": "req_upstream_example",
    "status": "COMPLETED",
    "mode": "MOCK",
    "verdict": "UNVERIFIED",
    "risk_level": "UNKNOWN",
    "headline": "Contoh sintetis: bukti belum cukup",
    "requires_human_review": true,
    "dimensions": {
      "factual_status": "UNVERIFIED",
      "source_authenticity": "UNVERIFIED",
      "sender_identity": "UNVERIFIED",
      "channel_status": "UNVERIFIED",
      "scam_risk": "UNKNOWN",
      "content_authenticity": "NOT_APPLICABLE"
    },
    "evidence": [],
    "sources": [],
    "recommended_actions": [],
    "presentation": {
      "narrative": {"text": "SIMULASI. Ini bukan hasil pemeriksaan live."}
    },
    "disclaimer": "Dukungan keputusan, bukan jaminan."
  }
}
```

Contoh di atas sintetis. `mode=MOCK` adalah penanda fixture Product dan belum dipastikan sebagai enum upstream. Jangan mengirim contoh tersebut sebagai bukti runtime AI. Schema `AIResult` pada draft OpenAPI mengizinkan perluasan dan ditandai provisional; perlu diganti dengan schema aktual/adapter tervalidasi sebelum remote release.

`result` mempertahankan field user-facing upstream: verdict/risk, dimensions, headline, what_checked, why, sufficiency/label, evidence/sources, recommended_actions, uncertainty, requires_human_review, community_status, privacy_notice, narrative/presentation, disclaimer dan version metadata bila ada. Body debug trace/prompt/private provider payload disaring secara eksplisit; jangan memantulkan seluruh object upstream tanpa boundary review.

Evidence dengan metadata absent tetap null/absent; tidak mengarang publisher/date/score. Rule matches disimpan hanya sebagai snapshot aman jika tersedia. Narasi tidak dijadikan satu-satunya sumber DTO; evidence/actions tetap structured.

### History policy

Draft REVIEW_REQUIRED: eligible storage = verdict UNVERIFIED atau requires_human_review true. Jika keduanya false: saved false, case_id null, NOT_REQUIRED, community_eligible false, NOT_AVAILABLE. Jika storage gagal: saved false, case_id null, SAVE_FAILED dan tidak menawarkan community action. Ketika saved true: case_id harus valid dan FK result/evidence sudah commit.

Jika tim memilih ALL, konfigurasi menjadi ALL, save_reason ALL_POLICY untuk kasus yang bukan review, dan contract tests/UI diubah bersama. Tetap jangan menyatakan setiap stored case otomatis boleh dipublikasikan; eligibility memperhitungkan AI/community policy dan sanitasi.

## 7. Product API katalog lengkap

Semua path berikut TARGET/DRAFT, bukan endpoint yang telah diuji live. Default authentication JWT kecuali health. Path canonical berbeda dari PRD v2.1 lama.

| Method | Path | Payload/response utama |
|---|---|---|
| GET | /api/health | status ok, service product, version; tanpa secret |
| GET | /api/ready | Minimal ready/degraded; detail dependency khusus operator/network |
| GET | /api/v1/me | user_id, roles, capabilities; role server |
| GET | /api/v1/me/profile | display_name, locale, bio, avatar_url aman |
| PATCH | /api/v1/me/profile | display_name/bio/locale whitelist; tidak ada role/is_active |
| POST | /api/v1/verifications/text | TextRequest → VerificationEnvelope |
| POST | /api/v1/verifications/image | Multipart image/question → VerificationEnvelope |
| GET | /api/v1/history | items summary + next_cursor |
| GET | /api/v1/history/{case_id} | Envelope tersimpan; image URL nullable bila asset masih ada |
| DELETE | /api/v1/history/{case_id} | 204 jika diperbolehkan; 403 CASE_LOCKED bila verified |
| POST | /api/v1/history/{case_id}/community-preview | Preview id, expiry, sanitized text/image, redactions |
| POST | /api/v1/history/{case_id}/community | preview_id + consent true → community state |
| DELETE | /api/v1/history/{case_id}/community | Withdrawal 204; tidak diam-diam deindex tanpa tracking |
| GET | /api/v1/community | Sanitized feed, status jelas, cursor |
| GET | /api/v1/community/{case_id} | Sanitized content + AI result aman + vote aggregates |
| POST | /api/v1/community/{case_id}/vote | vote DIDUKUNG/DIBANTAH → own vote + counts |
| DELETE | /api/v1/community/{case_id}/vote | Cancel, response aggregates/own vote null |
| GET | /api/v1/learning/modules | Published modules + progress summary |
| GET | /api/v1/learning/modules/{module_id} | Module/version/lesson content |
| POST | /api/v1/learning/lessons/{lesson_id}/complete | Idempotent completion → progress |
| GET | /api/v1/learning/modules/{module_id}/quiz | Questions/options tanpa keys; module_version |
| POST | /api/v1/learning/modules/{module_id}/quiz-attempts | module_version, answers(question_id,option_id) → score/explanation |
| GET | /api/v1/learning/progress | Module progress, latest/best score |
| POST | /api/v1/contributions | title, summary, reasoning, case_id?, sources[] → draft |
| GET | /api/v1/contributions/mine | Own contributions, cursor |
| GET | /api/v1/contributions/{contribution_id} | Owner/moderator safe detail |
| PATCH | /api/v1/contributions/{contribution_id} | Editable fields + expected_revision; DRAFT/NEEDS_EVIDENCE |
| POST | /api/v1/contributions/{contribution_id}/submit | expected_revision → SUBMITTED |
| GET | /api/v1/moderation/queue | Moderator; status filter + cursor |
| POST | /api/v1/moderation/contributions/{contribution_id}/claim | expected_revision → lease holder/expiry |
| POST | /api/v1/moderation/contributions/{contribution_id}/decisions | action, reason, evidence_ids, expected_revision, publish_to_connection, allow_rag |

Vote POST dipilih mengikuti ringkasan audit terbaru dalam percakapan; lampiran lama memakai PUT. **OPEN**: konfirmasi metode sebelum freeze. Semantik tetap atomic upsert satu vote per user. Delete vote dipertahankan sebagai operasi pembatalan yang tercantum lampiran lama.

Mutasi penting memakai Idempotency-Key: verification, preview, publication, contribution create/submit, moderation decisions, quiz submission, lesson complete. Patch/revision mencegah lost update. Status auth invalid 401, valid tetapi dilarang 403, non-owner private resource 404. Path `/contributions/mine` didaftarkan sebelum dynamic route untuk menghindari interpretasi sebagai ID.

Pagination default limit 20, max 100, opaque cursor berisi order tuple timestamp/ID dan scope/filter yang ditandatangani/divalidasi. Jangan memakai offset untuk feed mutable. Cursor A tidak dapat dipakai mengakses history B. Semua timestamps RFC3339 UTC; UUID Product konsisten dengan DB.

## 8. Error envelope dan retry

```json
{
  "error": {
    "code": "AI_TIMEOUT",
    "message": "Pemeriksaan belum memberikan respons. Coba lagi setelah status dipastikan.",
    "request_id": "b683ece9-2d46-4df6-9dac-a9de609af723",
    "retryable": false,
    "retry_after_seconds": null
  }
}
```

| HTTP | Product code | Perilaku |
|---|---|---|
| 400 | INVALID_REQUEST | Perbaiki input |
| 401 | INVALID_ACCESS_TOKEN | Refresh token satu kali; ulang dengan key sama |
| 403 | FORBIDDEN / OWNER_CANNOT_VOTE / CASE_LOCKED | Tidak retry |
| 404 | CASE_NOT_FOUND / NOT_FOUND | Tidak membocorkan private resource |
| 409 | IDEMPOTENCY_CONFLICT / REQUEST_IN_PROGRESS / UNKNOWN_OUTCOME / PREVIEW_EXPIRED / REVISION_CONFLICT | Baca state, jangan inferensi baru otomatis |
| 413 | PAYLOAD_TOO_LARGE | Crop/kompres |
| 415 | UNSUPPORTED_MEDIA_TYPE | Gunakan format yang didukung |
| 422 | VALIDATION_ERROR | Safe field message tanpa raw input |
| 429 | RATE_LIMITED | Hormati Retry-After; hanya retry aman sesuai outcome |
| 502 | AI_INVALID_RESPONSE / FACT_CHECK_UPSTREAM_FAILURE | Schema/provider gagal, bukan verdict |
| 503 | SERVICE_UNAVAILABLE / AI_AUTH_FAILED / PERSISTENCE_UNAVAILABLE | Gangguan service/config; jangan meminta user memperbaiki internal key |
| 504 | AI_TIMEOUT | Jika outcome unknown, retryable false sampai rekonsiliasi |

AI 401/403 berarti service key salah/akses internal ditolak; **bukan** token Supabase user expired. Jangan mengirim 401 user yang memicu refresh loop. AI 429 sebelum work dapat diberi retry hint; read timeout/network reset sesudah request terkirim berpotensi sudah dieksekusi. Error debug body tidak diteruskan.

## 9. Idempotency dan exactly-once limitation

1. Setelah auth/input, bentuk hash dari canonical payload efektif: trim text, optional defaults, sender, output BOTH; image bytes hash + question. Jangan gunakan random filename sebagai hash.
2. Unique `(user,route,key)` claim operation dalam transaksi pendek. Key reused dengan hash berbeda →409.
3. Jika COMPLETED dan cache tersedia, replay response dengan case ID yang sama; current request dapat diberi header replay, ID original tetap traceable.
4. Jika PROCESSING →409 REQUEST_IN_PROGRESS dengan retry-after singkat; tidak memanggil AI lagi.
5. Jika connection gagal sebelum satu byte request terkirim dan client dapat memastikan itu, satu reconnect retry boleh dilakukan dalam deadline. Jangan retry read/write/reset unknown hanya karena Product mempunyai key.
6. Read timeout setelah dispatch →UNKNOWN_OUTCOME. Key lokal **tidak** membuat AI endpoint idempotent. Hindari automatic retry; pengguna/operator harus mendapat status jelas.
7. Jika AI valid tetapi persistence gagal, cache aman hasil untuk retry persistence-only dengan key yang sama. Jika DB/cache sama-sama gagal dan proses mati, hasil mungkin tidak dapat dipulihkan; jangan menjanjikan exactly-once atau recovery sempurna.
8. Cache terminal dibersihkan setelah 10 menit sebagai proposal; repeated request setelah expiry tidak dijamin dedup. UI jangan auto retry lama tanpa batas. Retention/deletion menginvalidasi cached private result agar data yang dihapus tidak muncul dari replay.

## 10. Connection strategy dan time budget

Satu `httpx.AsyncClient` pada lifespan, ditutup saat shutdown. Fixed base URL dari environment, header key server-only, TLS verification aktif; no arbitrary target dari request client.

| Parameter draft | Nilai |
|---|---:|
| Connect timeout | 5 detik |
| Pool timeout | 5 detik |
| Write timeout | 30 detik |
| Read timeout | 120 detik |
| Absolute AI call deadline | 120 detik termasuk retry/connect/write/read |
| Product request deadline | 135 detik |
| Reverse proxy timeout | 145 detik |
| Android call timeout | 150 detik |
| HTTP max connections / keepalive | 10 / 5 |
| In-flight inference per Product instance | 4 awal; sesuaikan kapasitas AI |

Timeout phase HTTPX bukan total deadline; implementasikan deadline outer, bukan menganggap read=120 membatasi seluruh request. Angka ini PROPOSED karena client 120s lama berpotensi memotong request sebelum AI 120s selesai dan database commit. Pastikan hosting/load balancer benar-benar mengizinkan budget; jika tidak, desain async job perlu perubahan kontrak, tidak disisipkan sepihak.

Rate limiter user/IP yang tidak menyimpan PII berlebihan, bounded semaphore, queue wait terbatas. Circuit breaker draft buka 30 detik setelah 5 transient failures berturut; half-open satu probe aman. Health GET bukan request inference yang ditagih. Jangan menganggap success health memverifikasi key/model/search.

## 11. Mock dan contract test

Mock mengimplementasikan interface AI client lokal, deterministic fixture; tidak menjalankan pipeline/prompt/ranking. UI wajib SIMULASI. Test mencakup text/image success, UNVERIFIED, critical indicators, 401 AI, 429, timeout before/after dispatch, malformed JSON, schema mismatch, empty narrative, unexpected enum, history save failure dan delete replay invalidation.

Schema Product draft menyediakan extension fields untuk compatibility, tetapi critical required fields divalidasi ketat. Upstream snapshot harus dibandingkan dengan fixture asli teredaksi. Test generated Android client memakai Product contract, bukan AI contract. Structural YAML pass tidak cukup: remote smoke staging diperlukan dengan consent/budget test tim.

## 12. Community indexing — belum dianggap tersedia

PRD lama menargetkan:

| Method | Target path AI | Status |
|---|---|---|
| PUT | /api/internal/v1/knowledge/community/{id} | TARGET; belum dikonfirmasi |
| DELETE | /api/internal/v1/knowledge/community/{id} | TARGET; belum dikonfirmasi |
| POST | /api/internal/v1/knowledge/rulebook/rebuild | AI operator-only target; tidak diimplementasikan Product |

Default `COMMUNITY_RAG_SYNC_ENABLED=false`. Product outbox boleh menyimpan event BLOCKED untuk pekerjaan yang disepakati, tetapi tidak memanggil path tebakan. Kontrak indexing minimum yang perlu disepakati: canonical ID, revision, event ID, content hash, sanitized content, sources/provenance, moderation decision ID, verified_at, consent version, retracted_at, expiry, response ack, duplicate/stale event handling dan deletion guarantee.

Product adalah pemilik canonical contribution; AI membuat chunk/embedding dan mengelola Qdrant/local index. Jangan menghubungkan Qdrant langsung dari Product untuk melewati dependency API yang belum siap. Withdraw segera menghilangkan post Product; indeks remote mungkin lag, harus dipantau dan tidak dipakai ketika safety filter tidak dapat menjamin revocation.

## 13. Baseline teknis AI yang dipertahankan untuk konteks

Seluruh angka berikut **REPORTED BASELINE PRD v2.1**, bukan klaim HEAD/current deployment. Detail utuh termasuk formula dan payload ada di lampiran PRD historis. Tidak ada dependency Product untuk mengimplementasikannya.

| Komponen | Rincian reported |
|---|---|
| Corpus | 255 semantic chunks, 18 source records, 17 triggers; RB-INF-001@1.0.0, RB-ATO-001@1.2.0, RB-GOV-001@1.2.0 |
| BM25 | k1=1,5; b=0,75; score dinormalisasi kandidat |
| Hashed-subword | Unigram, adjacent bigram, character trigram ^token$; BLAKE2b digest 8 bytes, bucket mod384, sign bit, L2, cosine |
| Fusion | 0,50 BM25norm +0,27 max(0,cos) +0,19 metadata +severityBoost |
| Metadata | min(1,0,16×matchedSignalCount+0,35×domainMatch); critical boost 0,04 |
| Limits retrieval | min score 0,12; 30 candidates; 12 final; phase quota 3/5/2/2; LRU128 |
| Planner | openai/gpt-oss-20b; 8 claims; reviewer gpt-oss-120b opsional default off |
| Vision/verifier | Qwen reported; nama model dari arsip perlu dikonfirmasi ulang |
| Evidence | Tavily/local official/community adapters; 3 queries×3 results; 6 evidence ke verifier |
| Budget chars | Planner case 6.000; 6 rules×240 chars; evidence650; web excerpt800 |
| Sufficiency | Threshold reported0,58, tidak dihitung ulang Product |
| OCR AI | Tesseract ind+eng opsional, bukan kebutuhan setup Product |
| Qdrant | Target optional derived index, belum runtime terverifikasi pada baseline lama |

Pipeline reported: validate/privacy→CaseContext→signals/critical triggers→Rulebook retrieval→claim planner/repair→parallel evidence→dedup/aggregation/sufficiency→six-dimension verifier→deterministic guardrail→response. Rulebook bukan bukti. Hashed-subword bukan learned semantic embedding. Community tidak boleh mengubah deterministic rules otomatis.

Qdrant target di sisi AI: cosine, dimension mengikuti embedding terpilih, deterministic point IDs + SHA-256, payload filters object_kind/status/domain/phase/version/consent/retracted_at. Collection contoh `waspadai_knowledge_v1` bukan resource yang dibuat paket ini. Mode local default; fallback local wajib diuji di AI repo. Upsert/retract revision menjamin stale event tidak mengembalikan evidence yang dicabut.

## 14. Open items sebelum freeze

Full SHA/OpenAPI byte hash; exact enums/evidence schema; history ALL vs REVIEW_REQUIRED; vote POST vs PUT; 8MB vs8MiB; apakah image menerima OCR/context; actual internal host/network; public vs internal header requirements; upstream idempotency/cancel capability; privacy-safe trace fields; community indexing/retract schema; async kebutuhan hosting; source retention policy dan user deletion locked cases.

Selama hal ini OPEN, Product dapat dikembangkan dengan draft/mock, tetapi remote compatibility belum dinyatakan lulus.


## Lampiran historis utuh — android-api-contract.md (lampiran lama)

**NON-NORMATIVE / ARSIP.** Isi berikut dipertahankan tanpa pemangkasan untuk audit detail sumber. Ini bukan spesifikasi aktif. Arsitektur monorepo AI, endpoint direct, status CURRENT, enum, nama tabel, resep SQL, metode vote, policy history/retensi dan perintah setup di dalamnya dapat bertentangan dengan keputusan terbaru; gunakan bagian aktif dokumen dan ADR. Jangan menjalankan perintah arsip atau mengklaim angka benchmark/test sebagai hasil pemeriksaan saat ini.

<details>
<summary>Buka seluruh isi sumber terdahulu</summary>

~~~~text
# Kontrak API Android WaspadAI

Status dokumen: kontrak integrasi MVP untuk aplikasi Android/Kotlin yang memakai
API AI WaspadAI pada repository ini.

Keputusan integrasi saat ini: aplikasi Android boleh memanggil endpoint publik
WaspadAI secara langsung setelah pengguna login di aplikasi. Login Supabase
dipakai oleh aplikasi Android untuk mengontrol akses fitur, tetapi token
Supabase tidak divalidasi oleh API WaspadAI pada mode demo ini.

## 1. Batas sistem

Mode MVP yang dipakai sekarang:

```text
Android Kotlin -> Public WaspadAI API -> pipeline fact-check
        |
        +-- Supabase login diperiksa di aplikasi Android
```

Pada mode ini, Android memakai endpoint publik WaspadAI:

```text
GET  https://waspadai.shafwan.digital/api/health
POST https://waspadai.shafwan.digital/api/v1/verify/text
POST https://waspadai.shafwan.digital/api/v1/verify/image
```

Android tidak boleh memanggil endpoint internal WaspadAI:

```text
POST https://waspadai.shafwan.digital/api/internal/v1/verify/text
POST https://waspadai.shafwan.digital/api/internal/v1/verify/image
```

Endpoint internal membutuhkan `X-Waspadai-API-Key`. Secret itu hanya boleh
disimpan di server, bukan di aplikasi Android, karena APK dapat dibongkar.

Mode lanjutan yang dapat dipakai nanti:

```text
Android -> FastAPI aplikasi -> FastAPI WaspadAI -> pipeline fact-check
             |                    |
             |                    +-- stateless; tanpa akun dan history
             +-- Supabase Auth, history, Storage, komunitas, dan voting
```

Mode lanjutan diperlukan jika history, Storage, komunitas, voting, ownership,
dan moderasi ingin dikelola secara server-side. Pada mode itu Android memanggil
FastAPI aplikasi, sedangkan FastAPI aplikasi memanggil endpoint internal
WaspadAI menggunakan `X-Waspadai-API-Key`.

## 2. Base URL dan autentikasi

Base URL AI WaspadAI yang dipakai Android pada mode MVP:

```text
https://waspadai.shafwan.digital
```

Endpoint verifikasi yang dipakai Android:

```text
POST /api/v1/verify/text
POST /api/v1/verify/image
```

Sebelum memanggil endpoint tersebut, aplikasi Android harus memastikan pengguna
sudah login melalui Supabase. API WaspadAI mode demo tidak menerima dan tidak
memvalidasi header `Authorization`.

Jika nanti memakai FastAPI aplikasi terpisah, barulah setiap request Android
wajib mengirim access token sesi Supabase:

```http
Authorization: Bearer <supabase_access_token>
Accept: application/json
```

Pada mode lanjutan tersebut, FastAPI aplikasi harus:

1. mengambil Bearer token dari header `Authorization`;
2. memvalidasi token melalui `supabase.auth.get_user(access_token)` pada MVP;
3. menolak token tidak valid atau kedaluwarsa dengan HTTP `401`;
4. mengambil `sub` token sebagai `user_id` terpercaya;
5. menggunakan `user_id` tersebut untuk semua operasi history, Storage, vote,
   dan kepemilikan kasus.

Android tidak mengirim refresh token ke API aplikasi. Jika menerima `401` dari
FastAPI aplikasi, Android meminta Supabase SDK memperbarui session lalu
mengulang request paling banyak satu kali.

Untuk request pemeriksaan, Android sebaiknya mengirim UUID yang tetap sama saat
mengulang aksi yang sama:

```http
Idempotency-Key: <uuid-v4>
```

Pada mode MVP direct-to-WaspadAI, header ini belum diproses oleh WaspadAI dan
bersifat opsional. Pada mode lanjutan, header ini mencegah retry jaringan
menjalankan pipeline AI dua kali; backend aplikasi dapat menyimpan pasangan
`user_id + Idempotency-Key` secara singkat, misalnya 10 menit.

## 3. Karakteristik pemeriksaan

- Pemeriksaan bersifat synchronous: satu request menghasilkan satu respons.
- Timeout client yang disarankan adalah 120 detik.
- Chat bukan percakapan multi-turn; setiap pesan adalah pemeriksaan baru.
- Naratif menjadi tampilan utama.
- Bukti, sumber, tindakan, dan detail tetap dikirim sebagai data terstruktur
  untuk bagian UI yang dapat dibuka.
- Pada mode MVP, Android sebaiknya mengirim `output_mode=BOTH` agar mendapatkan
  hasil naratif dan data terstruktur dalam satu request.
- Pada mode lanjutan, backend aplikasi dapat selalu meminta `output_mode=BOTH`
  kepada WaspadAI sehingga Android tidak perlu mengelola mode output.

## 4. Pemeriksaan teks

```http
POST /api/v1/verify/text
Content-Type: application/json
```

Request:

```json
{
  "text": "Pesan mengaku dari bank dan meminta OTP agar akun tidak diblokir.",
  "question": "Apakah pesan ini aman?",
  "source_url": null,
  "sender_context": "UNKNOWN_NUMBER",
  "output_mode": "BOTH"
}
```

Field:

| Field | Wajib | Aturan |
| --- | --- | --- |
| `text` | Ya | 10-25.000 karakter setelah trim |
| `question` | Tidak | Maksimal 500 karakter; backend memakai pertanyaan default jika kosong |
| `source_url` | Tidak | URL publik maksimal 2.048 karakter |
| `sender_context` | Tidak | Default `UNKNOWN` |
| `output_mode` | Tidak | Gunakan `BOTH` agar response berisi naratif dan data terstruktur |

Nilai `sender_context`:

```text
NOT_APPLICABLE
UNKNOWN_NUMBER
KNOWN_CONTACT
FORWARDED
SOCIAL_MEDIA
UNKNOWN
```

Teks yang hanya berisi satu URL publik tetap valid. URL localhost, loopback,
private IP, dan URL internal harus ditolak.

## 5. Pemeriksaan screenshot

```http
POST /api/v1/verify/image
Content-Type: multipart/form-data
```

Multipart fields:

| Field | Wajib | Aturan |
| --- | --- | --- |
| `image` | Ya | File biner JPG, PNG, atau WEBP |
| `question` | Tidak | Maksimal 500 karakter |
| `output_mode` | Tidak | Gunakan `BOTH` agar response berisi naratif dan data terstruktur |

Batas gambar:

| Batas | Nilai |
| --- | --- |
| Ukuran file | Maksimal 8 MB |
| Dimensi minimal | 64 x 64 piksel |
| Dimensi maksimal | 6.000 x 6.000 piksel |
| Jumlah piksel | Maksimal 30.000.000 piksel |
| Format | JPEG/JPG, PNG, WEBP |

Android harus menggunakan alur berikut:

1. mengambil screenshot melalui flow capture/overlay Android;
2. menampilkan preview dan crop;
3. mengirim hasil crop sebagai file multipart, bukan Base64 dalam JSON;
4. mempertahankan layar loading sampai respons diterima atau timeout.

WEBP atau JPEG terkompresi disarankan untuk mengurangi waktu upload. Kompresi
tidak boleh membuat teks pada screenshot sulit dibaca.

## 6. Respons pemeriksaan

Pada mode MVP direct-to-WaspadAI, response dikembalikan langsung oleh API
WaspadAI. Tidak ada wrapper `result` dan tidak ada metadata `history` dari API
ini.

Contoh bentuk response yang perlu dibaca Android:

```json
{
  "request_id": "req_01kotlinexample",
  "status": "COMPLETED",
  "mode": "LIVE",
  "verdict": "UNVERIFIED",
  "risk_level": "MEDIUM",
  "headline": "Bukti belum cukup untuk memastikan klaim",
  "evidence_sufficiency": 0.42,
  "evidence_sufficiency_label": "Bukti belum cukup untuk memastikan klaim",
  "requires_human_review": true,
  "community_status": "ELIGIBLE_WITH_CONSENT",
  "what_checked": [
    "Klaim utama pada pesan",
    "Kecocokan dengan sumber yang ditemukan"
  ],
  "why": [
    "Bukti yang ditemukan belum mencakup seluruh klaim material."
  ],
  "evidence": [],
  "sources": [],
  "recommended_actions": [
    {
      "code": "RETURN_UNVERIFIED",
      "title": "Tunggu bukti yang lebih kuat",
      "detail": "Jangan jadikan informasi ini satu-satunya dasar keputusan."
    }
  ],
  "uncertainty": "Masih diperlukan sumber primer atau sumber tepercaya lain.",
  "dimensions": {
    "factual_status": "UNVERIFIED",
    "source_authenticity": "UNVERIFIED",
    "sender_identity": "UNVERIFIED",
    "channel_status": "UNVERIFIED",
    "scam_risk": "MEDIUM",
    "content_authenticity": "NOT_APPLICABLE"
  },
  "presentation": {
    "requested_mode": "BOTH",
    "structured": true,
    "narrative": {
      "text": "Hasil pemeriksaan: bukti yang tersedia belum cukup untuk memastikan klaim. Periksa kembali sumber resmi sebelum menindaklanjutinya.",
      "summary": "Bukti belum cukup untuk memastikan klaim",
      "paragraphs": [
        "Hasil pemeriksaan: bukti yang tersedia belum cukup untuk memastikan klaim.",
        "Periksa kembali sumber resmi sebelum menindaklanjutinya."
      ]
    },
  },
  "disclaimer": "Fact-check adalah dukungan keputusan, bukan jaminan."
}
```

Android harus memakai `presentation.narrative.text` sebagai jawaban utama.
Bagian `evidence`, `sources`, `recommended_actions`, `uncertainty`, dan
`dimensions` ditampilkan secara bertingkat ketika pengguna membuka detail.

Jika nanti memakai FastAPI aplikasi terpisah, backend aplikasi boleh membungkus
hasil WaspadAI dengan metadata history:

```json
{
  "request_id": "req_01kotlinexample",
  "status": "COMPLETED",
  "history": {
    "saved": true,
    "case_id": "case_01example",
    "save_reason": "UNVERIFIED",
    "community_eligible": true,
    "community_state": "PRIVATE"
  },
  "result": {
    "verdict": "UNVERIFIED",
    "headline": "Bukti belum cukup untuk memastikan klaim"
  }
}
```

Aturan penyimpanan:

```text
saved = verdict == UNVERIFIED OR requires_human_review == true
```

Jika hasil sudah cukup tegas dan tidak memerlukan review:

```json
{
  "history": {
    "saved": false,
    "case_id": null,
    "save_reason": "NOT_REQUIRED",
    "community_eligible": false,
    "community_state": "NOT_AVAILABLE"
  }
}
```

Aturan penyimpanan ini hanya berlaku jika ada FastAPI aplikasi yang mengelola
history. API WaspadAI pada mode MVP direct tidak menyimpan history pengguna.

## 7. History pada mode lanjutan

Bagian ini belum disediakan oleh API WaspadAI mode demo. History dikelola oleh
FastAPI aplikasi jika nanti arsitektur lanjutan dipakai.

History hanya berisi kasus `UNVERIFIED` atau `requires_human_review=true`.

```http
GET /api/v1/history?limit=20&cursor=<opaque_cursor>
GET /api/v1/history/{case_id}
DELETE /api/v1/history/{case_id}
```

Daftar history mengembalikan ringkasan, bukan seluruh evidence:

```json
{
  "items": [
    {
      "case_id": "case_01example",
      "input_type": "IMAGE",
      "headline": "Bukti belum cukup untuk memastikan klaim",
      "verdict": "UNVERIFIED",
      "requires_human_review": true,
      "community_state": "PRIVATE",
      "created_at": "2026-09-14T12:00:00Z"
    }
  ],
  "next_cursor": null
}
```

`GET /history/{case_id}` mengembalikan hasil lengkap dan signed URL sementara
untuk screenshot privat. Bucket Supabase Storage tidak boleh public.

Pengguna dapat menghapus history selama kasus belum menjadi
`VERIFIED_EVIDENCE`. Jika kasus sedang terlihat di komunitas, penghapusan juga
menarik kasus dari komunitas dan menghapus file turunan yang dipublikasikan.

## 8. Preview dan publikasi komunitas pada mode lanjutan

Bagian ini belum disediakan oleh API WaspadAI mode demo. Preview, redaksi final,
publikasi komunitas, dan consent dikelola oleh FastAPI aplikasi jika nanti
arsitektur lanjutan dipakai.

Kasus baru selalu privat. Tidak ada publikasi otomatis.

### Membuat preview redaksi

```http
POST /api/v1/history/{case_id}/community-preview
```

Response:

```json
{
  "preview_id": "preview_01example",
  "expires_at": "2026-09-14T12:15:00Z",
  "redacted_text": "Hubungi [PHONE_REDACTED] untuk informasi lebih lanjut.",
  "redacted_image_url": "https://signed-storage-url.example",
  "redactions": ["PHONE"],
  "confirmation_required": true
}
```

Preview dibuat dari screenshot yang sebelumnya sudah di-preview/crop oleh
pengguna. Backend melakukan redaksi PII lagi dan Android wajib menampilkan hasil
akhir tersebut sebelum meminta konfirmasi.

### Mengonfirmasi publikasi

```http
POST /api/v1/history/{case_id}/community
Content-Type: application/json
```

```json
{
  "preview_id": "preview_01example",
  "consent": true
}
```

`consent` harus bernilai `true`. Preview yang kedaluwarsa harus dibuat ulang.
Komunitas hanya melihat image hasil redaksi, klaim, hasil awal AI, dan agregat
vote. `user_id`, email, nomor telepon, serta path screenshot asli tidak pernah
dikirim ke client komunitas.

### Menarik kasus

```http
DELETE /api/v1/history/{case_id}/community
```

Pemilik dapat menarik kasus selama belum berstatus `VERIFIED_EVIDENCE`.

## 9. Community feed dan voting pada mode lanjutan

Bagian ini belum disediakan oleh API WaspadAI mode demo. Feed komunitas dan vote
dikelola oleh FastAPI aplikasi jika nanti arsitektur lanjutan dipakai.

```http
GET    /api/v1/community?limit=20&cursor=<opaque_cursor>
GET    /api/v1/community/{case_id}
PUT    /api/v1/community/{case_id}/vote
DELETE /api/v1/community/{case_id}/vote
```

Memberikan atau mengubah vote:

```json
{
  "vote": "DIDUKUNG"
}
```

Nilai vote yang valid hanya:

```text
DIDUKUNG
DIBANTAH
```

Response agregat:

```json
{
  "case_id": "case_01example",
  "user_vote": "DIDUKUNG",
  "counts": {
    "DIDUKUNG": 18,
    "DIBANTAH": 7
  }
}
```

Aturan vote:

- satu pengguna memiliki maksimal satu vote aktif per kasus;
- `PUT` membuat vote atau mengganti vote lama;
- `DELETE` membatalkan vote pengguna;
- pemilik kasus tidak boleh memberikan vote pada kasusnya sendiri;
- identitas voter tidak ditampilkan;
- vote adalah sinyal komunitas, bukan verdict faktual;
- jumlah vote tidak boleh otomatis menjadikan kasus evidence terverifikasi.

Hanya moderator/admin yang dapat menetapkan `VERIFIED_EVIDENCE` setelah menilai
sumber dan bukti. Endpoint moderasi tidak termasuk kontrak Android.

## 10. Error envelope

Pada mode MVP direct-to-WaspadAI, error mengikuti response FastAPI WaspadAI.
Contoh error validasi:

```json
{
  "detail": [
    {
      "type": "string_too_short",
      "loc": ["body", "text"],
      "msg": "String should have at least 10 characters"
    }
  ]
}
```

Jika nanti memakai FastAPI aplikasi terpisah, backend aplikasi sebaiknya
menyeragamkan error menjadi bentuk berikut:

```json
{
  "error": {
    "code": "INVALID_ACCESS_TOKEN",
    "message": "Sesi tidak valid atau sudah berakhir.",
    "request_id": "req_01error",
    "retryable": false,
    "retry_after_seconds": null
  }
}
```

Status dan tindakan Android:

| HTTP | Contoh code | Tindakan client |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | Tampilkan kesalahan input |
| `401` | `INVALID_ACCESS_TOKEN` | Hanya untuk mode backend aplikasi; refresh session lalu retry satu kali |
| `403` | `OWNER_CANNOT_VOTE`, `CASE_LOCKED` | Tampilkan alasan; jangan retry |
| `404` | `CASE_NOT_FOUND` | Kembali ke daftar sebelumnya |
| `409` | `PREVIEW_EXPIRED`, `CASE_ALREADY_VERIFIED` | Refresh data atau buat preview baru |
| `413` | `PAYLOAD_TOO_LARGE` | Minta pengguna mengompres/crop gambar |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Gunakan JPG, PNG, atau WEBP |
| `422` | `VALIDATION_ERROR` | Tampilkan pesan validasi field |
| `429` | `RATE_LIMITED` | Tunggu `retry_after_seconds` |
| `502` | `FACT_CHECK_UPSTREAM_FAILURE` | Tawarkan coba lagi |
| `503` | `SERVICE_UNAVAILABLE` | Tawarkan coba lagi nanti |

Android tidak boleh menampilkan stack trace atau exception internal kepada
pengguna. Pada mode direct, tampilkan pesan ramah berdasarkan HTTP status dan
simpan detail teknis hanya untuk log/debug.

## 11. Kontrak internal backend aplikasi ke WaspadAI

Bagian ini untuk tim backend, bukan tim Android.

```http
POST /api/internal/v1/verify/text
POST /api/internal/v1/verify/image
X-Waspadai-API-Key: <service_secret>
```

Backend aplikasi harus selalu meminta `output_mode=BOTH`, memakai timeout 120
detik, dan meneruskan file sebagai multipart tanpa Base64. Secret disimpan pada
environment backend dan WaspadAI.

Karena kedua FastAPI berada pada VPS yang sama tetapi berbeda repository,
hubungkan container melalui private Docker network. Endpoint internal WaspadAI
tidak perlu dipublikasikan sebagai rute internet khusus.

WaspadAI tidak memvalidasi Supabase token dan tidak menyimpan history. FastAPI
aplikasi adalah pemilik autentikasi, `user_id`, Supabase Database, Storage,
community state, vote, dan moderasi.

## 12. Checklist implementasi Kotlin

- Pastikan session Supabase aktif sebelum pengguna boleh memakai fitur cek AI.
- Untuk mode MVP direct, jangan kirim Bearer token ke WaspadAI.
- Jangan pernah mengirim `user_id` atau service key WaspadAI dari Android.
- Gunakan request JSON untuk teks dan multipart untuk screenshot.
- Kirim `output_mode=BOTH` pada request teks dan screenshot.
- Tampilkan preview/crop sebelum upload screenshot.
- Gunakan timeout 120 detik dan satu loading state.
- Refresh token dan retry paling banyak sekali ketika menerima `401` dari
  backend aplikasi; pada mode direct WaspadAI, `401` tidak menjadi alur normal.
- `Idempotency-Key` opsional pada mode direct; wajib dipertimbangkan jika nanti
  memakai backend aplikasi.
- Tampilkan naratif terlebih dahulu, lalu detail bertingkat.
- Pada mode direct, baca naratif dari `presentation.narrative.text`.
- Fitur history, community, signed URL screenshot, dan vote membutuhkan backend
  aplikasi terpisah; jangan menganggap field tersebut tersedia dari API
  WaspadAI direct.
~~~~

</details>
