# WaspadAI Product Documentation

Dokumentasi teknis dan produk untuk pengembangan aplikasi WaspadAI. Baseline aktif ditetapkan pada 15 September 2026.

> **Kontrak kanonik saat ini:** [`contracts/current/android-api-contract.md`](contracts/current/android-api-contract.md). Berkas tersebut adalah salinan byte-identik dari `android-api-contract.md` yang diberikan tim dan menjadi sumber kebenaran untuk integrasi Android dengan AI.

## Keputusan yang berlaku

Mode MVP saat ini adalah:

```text
Android Kotlin ──HTTPS──> Public WaspadAI API ──> fact-check pipeline
      │
      └── Supabase Auth diperiksa oleh aplikasi sebelum fitur digunakan
```

- Android memanggil `POST /api/v1/verify/text` dan `POST /api/v1/verify/image` secara langsung.
- Android tidak mengirim Bearer token Supabase ke WaspadAI.
- Android tidak pernah memakai endpoint `/api/internal/*` atau menyimpan `X-Waspadai-API-Key`.
- Respons sukses dibaca langsung; tidak ada wrapper `result`, `history`, atau `execution_mode` pada mode MVP.
- History server-side, komunitas, voting, ownership, dan moderasi belum disediakan oleh API AI publik. Fitur tersebut membutuhkan Product Backend pada fase berikutnya.

## Mulai membaca

| Kebutuhan | Dokumen |
| --- | --- |
| Memahami status dan scope | [Product overview](docs/product/overview.md) |
| Mengimplementasikan Android | [Android architecture](docs/architecture/android-client.md) |
| Mengintegrasikan API AI | [API documentation](docs/api/README.md) |
| Menyiapkan development | [Development setup](docs/development/setup.md) |
| Mengetahui ownership | [Ownership](docs/governance/ownership.md) |
| Meninjau keputusan arsitektur | [ADR](docs/adr/README.md) |

Peta lengkap tersedia di [`docs/README.md`](docs/README.md).

## Struktur repository dokumentasi

```text
.
├── README.md
├── contracts/
│   ├── current/      # kontrak normatif yang berlaku
│   ├── examples/     # fixture valid untuk kontrak current
│   ├── future/       # rancangan fase berikutnya; non-runtime
│   └── reference/    # snapshot eksternal; bukan sumber kebenaran aktif
├── docs/
│   ├── product/
│   ├── architecture/
│   ├── api/
│   ├── development/
│   ├── governance/
│   ├── adr/
│   └── archive/      # materi historis; non-normatif
└── scripts/
```

## Urutan otoritas

Jika ada perbedaan informasi, gunakan urutan berikut:

1. `contracts/current/android-api-contract.md` untuk wire contract Android–AI;
2. ADR berstatus `Accepted` untuk keputusan arsitektur;
3. dokumen aktif di `docs/`;
4. kontrak `future/` dan snapshot `reference/` sebagai bahan rancangan;
5. `docs/archive/` hanya untuk audit sejarah.

Dokumen future dan archive tidak boleh dipakai sebagai bukti bahwa endpoint atau fitur sudah tersedia.

## Quality gate

Jalankan dari root repository dokumentasi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1
```

Pemeriksaan memvalidasi checksum kontrak kanonik, JSON, tautan relatif, penandaan dokumen non-current, dan pola kontradiksi kritis pada dokumen aktif.

