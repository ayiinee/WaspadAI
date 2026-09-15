# AI Testing, Indexing, Baseline, dan Open Items

[Kembali ke indeks integrasi AI](ai-service.md)

## 11. Mock dan contract test

Mock mengimplementasikan interface AI client lokal, deterministic fixture; tidak menjalankan pipeline/prompt/ranking. UI wajib SIMULASI. Test mencakup text/image success, UNVERIFIED, critical indicators, 401 AI, 429, timeout before/after dispatch, malformed JSON, schema mismatch, empty narrative, unexpected enum, history save failure dan delete replay invalidation.

Schema object AI copied persis dengan additionalProperties=false; semua required fields divalidasi ketat. Metadata Product hanya berada di luar result. Upstream snapshot harus dibandingkan dengan fixture asli teredaksi. Test generated Android client memakai Product contract, bukan AI contract. Structural YAML pass tidak cukup: remote smoke staging diperlukan dengan consent/budget test tim.

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

Full SHA GitHub dan versi deployment; history ALL vs REVIEW_REQUIRED; vote POST vs PUT; 8MB vs8MiB dan batas pixel runtime; actual internal host/network; public vs internal header requirements; upstream idempotency/cancel capability; privacy-safe trace fields; community indexing/retract schema; async kebutuhan hosting; source retention policy dan user deletion locked cases.

Selama hal ini OPEN, Product dapat dikembangkan dengan draft/mock, tetapi remote compatibility belum dinyatakan lulus.
