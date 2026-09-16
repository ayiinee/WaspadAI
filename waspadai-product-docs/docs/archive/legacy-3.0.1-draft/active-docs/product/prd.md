# WaspadAI — Product Requirements Document

Versi: 3.0.1-draft. Tanggal: 15 September 2026. Status: TARGET untuk implementasi Product. Batas repo mengikuti [ADR-0001](../adr/0001-product-ai-boundary.md).

PRD aktif dipisahkan menjadi modul agar requirement dapat direview dan diperbarui tanpa membuka satu dokumen monolitik. Urutan di bawah mempertahankan urutan bagian 1–17 dari sumber sebelum modularisasi.

| Urutan | Modul | Cakupan |
|---|---|---|
| 1 | [Overview dan prinsip](overview.md) | Cara memakai dokumen, ringkasan, persona, dan prinsip wajib |
| 2 | [Scope dan taksonomi](scope-and-taxonomy.md) | Scope bertahap, fitur, enum, dan invariant output |
| 3 | [User flows](user-flows.md) | Login, verification, history, community, moderation, dan learning |
| 4 | [Kualitas dan acceptance](quality-and-acceptance.md) | State, keamanan, retensi, NFR, acceptance criteria, dan golden cases |
| 5 | [Delivery dan governance](delivery-and-governance.md) | Status implementasi, milestone, risiko, Definition of Done, dan change control |

Detail historis/non-normatif tetap tersedia secara byte-for-byte dalam arsip PRD [3.0.0](../archive/monoliths/product-prd.v3.0.0-draft.md) dan [3.0.1](../archive/monoliths/product-prd.v3.0.1-draft.md). Jika isi arsip bertentangan dengan modul aktif, gunakan modul aktif dan ADR.
