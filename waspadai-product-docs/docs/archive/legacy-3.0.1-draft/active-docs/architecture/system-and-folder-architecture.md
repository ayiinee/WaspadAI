# WaspadAI — System and Folder Architecture

Versi 3.0.1-draft. Status TARGET. Semua struktur merupakan rancangan implementasi Product. [ADR Product–AI](../adr/0001-product-ai-boundary.md) menentukan batas layanan dan [indeks database](database-architecture.md) menentukan desain data.

[ADR baseline initial setup](../adr/0002-initial-product-baseline.md) membekukan penyesuaian layout Product aktual, package Android, toolchain, dan artifact dokumentasi CI. Modul aktif di bawahnya berlaku saat detail arsip monolitik berbeda.

| Urutan | Modul | Cakupan |
|---|---|---|
| 1 | [System overview](system-overview.md) | Topologi, trust boundary, dan struktur repository Product |
| 2 | [Android architecture](android-architecture.md) | Struktur aplikasi, feature boundary, dan perilaku perangkat |
| 3 | [Backend architecture](backend-architecture.md) | Struktur Product FastAPI, dependency, tests, dan scripts |
| 4 | [Web architecture](web-architecture.md) | Web Product opsional dan batas keamanannya |
| 5 | [Runtime, security, configuration](runtime-security-configuration.md) | Workflow runtime, failure boundary, security, dan naming |
| 6 | [Feature mapping dan migration](feature-mapping-and-migration.md) | Lokasi fitur, migrasi rancangan lama, dan Definition of Done |

Versi lengkap sebelum dan sesudah pembaruan kontrak dipertahankan dalam arsip arsitektur sistem [3.0.0](../archive/monoliths/system-and-folder-architecture.v3.0.0-draft.md) dan [3.0.1](../archive/monoliths/system-and-folder-architecture.v3.0.1-draft.md).
