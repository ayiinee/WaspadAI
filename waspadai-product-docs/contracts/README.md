# Contract Registry

Folder ini memisahkan kontrak runtime yang berlaku dari rancangan dan snapshot.

| Folder | Status | Penggunaan |
| --- | --- | --- |
| [`current/`](current/) | `CURRENT` | Sumber kebenaran implementasi Android–AI |
| [`examples/`](examples/) | `CURRENT` | Fixture valid untuk test client |
| [`future/`](future/) | `FUTURE` | Rancangan Product Backend; belum menjadi runtime contract |
| [`reference/`](reference/) | `REFERENCE` | Snapshot upstream/legacy untuk audit, bukan override contract current |

Kontrak kanonik adalah [`current/android-api-contract.md`](current/android-api-contract.md). Perubahan terhadap berkas ini wajib berasal dari perubahan yang disetujui pada source contract tim, disertai contract test dan review owner Android/AI.

