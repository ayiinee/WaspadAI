# Peta Dokumentasi WaspadAI

Dokumen di bawah ini adalah dokumentasi aktif. Materi lama berada di [`archive/`](archive/README.md) dan tidak bersifat normatif.

## Jalur baca utama

1. [Product overview](product/overview.md)
2. [Scope dan status implementasi](product/scope-and-status.md)
3. [System overview](architecture/system-overview.md)
4. [Kontrak API saat ini](api/README.md)
5. [Development setup](development/setup.md)
6. [Documentation governance](governance/documentation-governance.md)

## Berdasarkan peran

| Peran | Mulai dari | Lanjutan |
| --- | --- | --- |
| Product/PM | [Product overview](product/overview.md) | [User flows](product/user-flows.md) |
| Android engineer | [Android client](architecture/android-client.md) | [Request dan response](api/request-response.md) |
| Backend engineer | [Future Product Backend](architecture/future-product-backend.md) | [Future API](api/future-product-api.md) |
| QA engineer | [Testing strategy](development/testing.md) | [Release checklist](development/release-checklist.md) |
| Security/reviewer | [Data dan privacy](architecture/data-and-privacy.md) | [Errors dan resilience](api/errors-and-resilience.md) |
| Tech lead | [ADR index](adr/README.md) | [Ownership](governance/ownership.md) |

## Status label

| Label | Arti |
| --- | --- |
| `CURRENT` | Berlaku untuk implementasi MVP sekarang |
| `FUTURE` | Rancangan fase berikutnya; belum boleh diasumsikan tersedia |
| `REFERENCE` | Snapshot atau informasi pendukung eksternal |
| `ARCHIVED` | Riwayat; tidak boleh menjadi dasar implementasi baru |

Setiap dokumen arsitektur/API harus menyatakan labelnya di bagian awal.

