# WaspadAI Product Documentation

Dokumentasi teknis dan produk untuk aplikasi WaspadAI.

> **Kontrak kanonik:** [`contracts/current/android-api-contract.md`](contracts/current/android-api-contract.md). File tersebut harus menjadi salinan byte-identik dari [`../android-api-contract.md`](../android-api-contract.md).

## Keputusan yang berlaku

```text
Android Kotlin ──Bearer Supabase──> Product API ──internal API key──> WaspadAI API
                                         │
                                         └── Supabase Database/Storage
```

- Android hanya memanggil Product API dan tidak menyimpan `X-Waspadai-API-Key`.
- Product API memvalidasi identity, mengelola state Product, dan membungkus hasil AI dengan `history`, `result`, serta `execution_mode`.
- Product API mengambil community evidence eligible dari database lalu mengirim proyeksi sanitized ke endpoint internal WaspadAI.
- Android dan WaspadAI tidak mengakses Supabase Database secara langsung.
- Implementasi saat ini baru menyediakan text verification `MOCK` dan history. Remote AI, image, community, moderation, dan pengiriman community evidence masih target.

## Mulai membaca

| Kebutuhan | Dokumen |
| --- | --- |
| Memahami status dan scope | [Product overview](docs/product/overview.md) |
| Mengimplementasikan Android | [Android architecture](docs/architecture/android-client.md) |
| Mengintegrasikan Product API | [API documentation](docs/api/README.md) |
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
│   ├── future/       # draft non-runtime yang belum diimplementasikan
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

1. `contracts/current/android-api-contract.md` untuk wire contract Android–Product–AI;
2. ADR berstatus `Accepted` untuk keputusan arsitektur;
3. dokumen aktif di `docs/`;
4. migration SQL untuk bentuk dan constraint database aktual;
5. kontrak `future/`, snapshot `reference/`, dan `docs/archive/` hanya sebagai bahan historis/rancangan.

## Quality gate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1
```

Pemeriksaan memvalidasi checksum kontrak kanonik, fixture JSON, tautan relatif, topology Product API, dan pemisahan status implemented/target.

