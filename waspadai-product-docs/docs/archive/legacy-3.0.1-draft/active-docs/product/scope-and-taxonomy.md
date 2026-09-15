# Scope dan Taksonomi Output

[Kembali ke indeks PRD](prd.md)

## 5. Scope bertahap, bukan penghapusan fitur

P0/P1 adalah urutan pengerjaan. Seluruh domain dari PRD lama tetap dicatat. Fitur yang belum siap tidak boleh disamarkan sebagai implementasi lengkap.

| ID | Kapabilitas | Tahap | Detail minimum |
|---|---|---|---|
| F01 | Auth dan profile | P0 | Register/login/logout, refresh terkoordinasi, profile dan role server |
| F02 | Text verification | P0 | Input 10–25.000 karakter, source URL, sender enum, hasil narrative + structured |
| F03 | Image verification | P0 | JPG/PNG/WEBP multipart, preview/crop, batas ukuran/pixel, cleanup |
| F04 | Overlay/capture | P0 | Bubble, move/stop, izin overlay, one-shot MediaProjection dan resource release |
| F05 | OCR lokal | P0 | Preview teks yang dapat dikoreksi; bukan bukti identitas atau field AI otomatis |
| F06 | Enam dimensi/result | P0 | Narrative dahulu, detail dimensi/evidence/safe actions/uncertainty |
| F07 | History | P0 | Penyimpanan bersyarat dalam draft, list/detail/delete owner-only |
| F08 | Pelajari | P1 | 3–5 modul kurasi, tujuan, contoh, checklist dan lesson |
| F09 | Quiz | P1 | 3–5 pertanyaan per modul, jawaban kanonik dan scoring server |
| F10 | Progress | P1 | Lesson selesai, skor terakhir/terbaik, versi modul dan timestamp |
| F11 | Koneksi/community | P1 | Feed/detail sanitized, status jelas, tanpa DM/friend graph |
| F12 | Preview dan consent | P1 | Preview final, kedaluwarsa, consent eksplisit, tidak auto-publish |
| F13 | Voting | P1 | Satu vote/user/kasus, upsert, cancel, owner dilarang vote sendiri |
| F14 | Contribution | P1 | URL bukti, alasan, ringkasan, status dan ownership |
| F15 | Moderation | P1 | Queue, claim/lease, reason, evidence, append-only decisions, retract |
| F16 | Verified Community RAG | P1 dependency-gated | Outbox Product→AI; feature off sampai API indexing diverifikasi |
| F17 | Web pendukung | P1 opsional | Moderation/demo; tidak memindahkan web AI otomatis ke Product |
| F18 | Qdrant dan tuning indeks | P2/AI-owned | Opsional di AI; local BM25+hashed tetap reported baseline |

Non-goals: melatih foundation model, browsing/monitoring layar otomatis, direct messaging, social graph, auto-update canonical Rulebook dari komunitas, mengganti pemeriksa fakta/profesional, atau memindahkan pipeline AI ke Product.

## 6. Taksonomi output dan invariant

Tabel enum di bawah telah dicocokkan dengan AssessmentDimensions pada OpenAPI unggahan API 0.7.0 dan sesuai. Ini verifikasi terhadap file unggahan, bukan terhadap HEAD GitHub/deployment.

| Dimensi | Nilai kategori baseline | Makna |
|---|---|---|
| `factual_status` | SUPPORTED, REFUTED, MISLEADING, PARTLY_TRUE, OUTDATED, UNVERIFIED, SATIRE, OPINION, NOT_APPLICABLE | Dukungan bukti terhadap klaim faktual |
| `source_authenticity` | VERIFIED, UNVERIFIED, SUSPICIOUS, NOT_APPLICABLE | Keaslian sumber yang dirujuk |
| `sender_identity` | VERIFIED, UNVERIFIED, IMPERSONATION_LIKELY, IMPERSONATION_CONFIRMED, NOT_APPLICABLE | Identitas pengirim, tidak sama dengan sumber |
| `channel_status` | VERIFIED, UNVERIFIED, SUSPICIOUS, MALICIOUS, NOT_APPLICABLE | Keamanan/keaslian kanal atau tautan |
| `scam_risk` | CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN | Risiko penipuan/social engineering |
| `content_authenticity` | ORIGINAL, ALTERED, SYNTHETIC, UNVERIFIED, NOT_APPLICABLE | Keaslian artefak; teks polos bukan bukti manipulasi gambar |

Ketentuan:

- `verdict` adalah field upstream yang terpisah dari `dimensions.factual_status`; jangan mengasumsikan enum keduanya identik tanpa kontrak.
- `risk_level` dan `dimensions.scam_risk` ditampilkan sesuai field upstream. Ketidaksesuaian material dicatat untuk validasi, bukan dihitung ulang diam-diam.
- Label UI boleh diterjemahkan, tetapi nilai wire disimpan apa adanya. Nilai baru yang tidak dikenal tidak dipetakan menjadi aman; tampilkan “Status belum dikenali” dan tandai compatibility error.
- Enam dimensi merupakan kategori, **bukan kewajiban enam skor 0–1**. Tidak perlu tabel definisi angka buatan.
- `evidence_sufficiency` 0–1 adalah metrik kecukupan bukti, bukan probabilitas kebenaran. Threshold 0,58 pada PRD lama adalah konfigurasi AI reported; Product tidak menetapkan ulang verdict berdasarkan threshold itu.
- Teks tanpa sumber tidak membuktikan source authenticity. Screenshot bank tidak membuktikan sender identity. Konten benar dapat tetap mengandung tindakan penipuan.
- `requires_human_review` dan `community_status` adalah sinyal upstream; consent/ownership/visibility tetap keputusan Product.
- `recommended_actions` memuat code/title/detail yang aman. Jangan mengaktifkan tautan pembayaran/OTP atau menampilkan arahan penyerahan rahasia.

UI utama memakai `presentation.narrative.text` bila tersedia. Headline menjadi fallback aman jika narasi tidak ada. Detail bertingkat menampilkan `what_checked`, `why`, evidence, sources, dimensi, uncertainty, disclaimer dan safe actions. Raw trace/prompt internal tidak dikirim ke layar pengguna.
