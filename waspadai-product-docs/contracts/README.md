# Contract Registry

Folder ini memisahkan kontrak runtime/keputusan yang berlaku dari draft dan snapshot.

| Folder | Status | Penggunaan |
| --- | --- | --- |
| [`current/`](current/) | `CURRENT` | Sumber kebenaran integrasi Android–Product–AI |
| [`examples/`](examples/) | `CURRENT` | Fixture Product API untuk contract test |
| [`future/`](future/) | `FUTURE` | Draft Product API lama dan fixture target yang belum runtime |
| [`reference/`](reference/) | `REFERENCE` | Snapshot upstream untuk audit; bukan override kontrak current |

Kontrak kanonik adalah [`current/android-api-contract.md`](current/android-api-contract.md). Perubahannya harus berasal dari source contract tim, disertai sinkronisasi checksum, fixture, test, dan review owner Android/Backend/AI.

