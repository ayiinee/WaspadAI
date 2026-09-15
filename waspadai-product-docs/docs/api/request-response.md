# Request dan Response Android–AI

Status: `CURRENT`.

Dokumen ini adalah implementation guide. Untuk definisi lengkap, gunakan [kontrak kanonik](../../contracts/current/android-api-contract.md).

## Text verification

```http
POST /api/v1/verify/text
Content-Type: application/json
Accept: application/json
```

```json
{
  "text": "Pesan mengaku dari bank dan meminta OTP agar akun tidak diblokir.",
  "question": "Apakah pesan ini aman?",
  "source_url": null,
  "sender_context": "UNKNOWN_NUMBER",
  "output_mode": "BOTH"
}
```

| Field | Wajib | Rule current |
| --- | --- | --- |
| `text` | Ya | 10–25.000 karakter setelah trim |
| `question` | Tidak | Maksimal 500 karakter |
| `source_url` | Tidak | Public URL, maksimal 2.048 karakter |
| `sender_context` | Tidak | Default `UNKNOWN`; enum di bawah |
| `output_mode` | Tidak | Kirim `BOTH` untuk UI Android |

`sender_context`: `NOT_APPLICABLE`, `UNKNOWN_NUMBER`, `KNOWN_CONTACT`, `FORWARDED`, `SOCIAL_MEDIA`, atau `UNKNOWN`.

Teks yang hanya berisi public URL tetap valid. Localhost, loopback, private IP, dan internal URL harus ditolak.

## Image verification

```http
POST /api/v1/verify/image
Content-Type: multipart/form-data
Accept: application/json
```

| Field | Wajib | Rule current |
| --- | --- | --- |
| `image` | Ya | Binary JPEG/JPG, PNG, atau WEBP |
| `question` | Tidak | Maksimal 500 karakter |
| `output_mode` | Tidak | Kirim `BOTH` |

Limit image: maksimal 8 MB; sisi 64–6.000 piksel; total maksimal 30.000.000 piksel. Kirim file hasil preview/crop sebagai multipart, bukan Base64 JSON.

## Success response

Response dikembalikan langsung oleh WaspadAI. Tidak ada wrapper `result` atau metadata `history`.

```json
{
  "request_id": "req_example",
  "status": "COMPLETED",
  "mode": "LIVE",
  "verdict": "UNVERIFIED",
  "risk_level": "MEDIUM",
  "headline": "Bukti belum cukup untuk memastikan klaim",
  "evidence_sufficiency": 0.42,
  "evidence_sufficiency_label": "Bukti belum cukup untuk memastikan klaim",
  "requires_human_review": true,
  "community_status": "ELIGIBLE_WITH_CONSENT",
  "what_checked": [],
  "why": [],
  "evidence": [],
  "sources": [],
  "recommended_actions": [],
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
      "text": "Hasil pemeriksaan: bukti yang tersedia belum cukup untuk memastikan klaim.",
      "summary": "Bukti belum cukup untuk memastikan klaim",
      "paragraphs": [
        "Hasil pemeriksaan: bukti yang tersedia belum cukup untuk memastikan klaim."
      ]
    }
  },
  "disclaimer": "Fact-check adalah dukungan keputusan, bukan jaminan."
}
```

Fixture parseable tersedia di [`contracts/examples/verification-response.json`](../../contracts/examples/verification-response.json).

Android menampilkan `presentation.narrative.text` sebagai jawaban utama. `evidence`, `sources`, `recommended_actions`, `uncertainty`, dan `dimensions` ditampilkan sebagai detail. Client tidak boleh menghitung ulang atau “memperbaiki” verdict.

