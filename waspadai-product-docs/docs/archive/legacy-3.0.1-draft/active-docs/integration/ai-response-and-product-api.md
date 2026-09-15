# AI Response dan Product API

[Kembali ke indeks integrasi AI](ai-service.md)

## 6. Response dan wrapper Product

Product success envelope sintetis yang lengkap dan sesuai schema:

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
    "request_id": "req_synthetic_fixture",
    "status": "COMPLETED",
    "mode": "LIVE",
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
      "requested_mode": "BOTH",
      "structured": true,
      "narrative": {
        "text": "SIMULASI. Bukan hasil pemeriksaan live.",
        "summary": "Fixture kontrak",
        "paragraphs": [
          "SIMULASI. Bukan hasil pemeriksaan live."
        ]
      }
    },
    "disclaimer": "Dukungan keputusan, bukan jaminan.",
    "trace_id": "trace_synthetic_fixture",
    "mode_notice": "Fixture sintetis untuk validasi schema; bukan hasil AI live.",
    "input_summary": {
      "input_type": "TEXT",
      "content_type": "MESSAGE",
      "label": "Fixture sintetis",
      "media_type": null,
      "dimensions": null,
      "extraction_status": "NOT_APPLICABLE",
      "excerpt": "Contoh data sintetis untuk validasi kontrak.",
      "source_url": null,
      "sender_context": "UNKNOWN",
      "character_count": 44,
      "urls_detected": 0,
      "pii_types_redacted": []
    },
    "evidence_sufficiency": 0.0,
    "evidence_sufficiency_label": "Nilai fixture, bukan pengukuran live",
    "what_checked": [],
    "why": [],
    "uncertainty": "Contoh sintetis; tidak membuktikan hasil inferensi.",
    "community_status": "ELIGIBLE_WITH_CONSENT",
    "privacy_notice": "Tidak berisi data pengguna nyata.",
    "rulebook": {
      "corpus_versions": [],
      "retrieval_mode": "SYNTHETIC_FIXTURE",
      "candidate_count": 0,
      "selected_count": 0,
      "forced_rule_ids": [],
      "cache_hit": false,
      "duration_ms": 0
    },
    "pipeline": []
  },
  "execution_mode": "MOCK"
}
```

execution_mode adalah metadata Product: REMOTE untuk respons dari call AI, MOCK untuk fixture. result.mode tetap LIVE karena upstream mensyaratkan const LIVE. Contoh ini bukan hasil inferensi live. AIResult merujuk langsung VerificationResponse yang disalin persis dari unggahan; tidak boleh menghilangkan required fields atau menambah properti di dalam object AI. Product dapat membatasi akses data sensitif sebelum publikasi melalui DTO terpisah; jangan menyebut projection yang field wajibnya dihapus sebagai VerificationResponse lengkap.

Semua 25 top-level fields VerificationResponse wajib, termasuk trace_id, mode_notice, input_summary, rulebook, pipeline, presentation dan privacy_notice. Narrative dapat null sesuai ResponsePresentation; bila ada, text/summary/paragraphs semuanya wajib. Evidence published_at wajib hadir tetapi nullable; Evidence tidak menerima provenance tambahan dan verification_status menerima REVIEWED. Question teks tidak menerima null; image question tidak mempunyai maxLength dalam snapshot, sehingga batas500 di dokumen adalah policy Product yang harus diberlakukan terpisah. Ukuran file/piksel dan aturan SSRF tidak dibuktikan oleh schema ini. evidence_sufficiency bertipe number tanpa range pada snapshot; rentang0–1 tetap invariant domain yang perlu diuji, jangan mengaku batas itu tertulis di OpenAPI AI.

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
