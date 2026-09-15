# Documentation Governance

Status: `CURRENT`.

## Tujuan

Dokumentasi harus membuat engineer baru dapat membedakan fakta runtime, keputusan yang berlaku, rancangan masa depan, dan materi historis tanpa penjelasan lisan tambahan.

## Aturan struktur

- `contracts/current/`: hanya kontrak yang berlaku.
- `contracts/examples/`: hanya fixture current yang parseable.
- `contracts/future/`: draft yang belum runtime; wajib memiliki warning.
- `contracts/reference/`: snapshot eksternal/legacy.
- `docs/{product,architecture,api,development,governance,adr}`: dokumen aktif.
- `docs/archive/`: history non-normatif.

Hindari duplikasi detail wire. Dokumen implementasi merujuk kontrak kanonik; jika harus mengulang field untuk usability, quality gate dan reviewer contract wajib memeriksa konsistensinya.

## Definition of Done perubahan dokumentasi

1. Status dokumen jelas.
2. Source of truth dan owner disebut.
3. Semua relative link valid.
4. JSON fixture valid dan tidak mengandung data nyata.
5. Current/future/archive tidak tercampur dalam tabel atau kalimat ambigu.
6. Path, auth, request, response, timeout, dan error konsisten dengan kontrak current.
7. `scripts/verify-docs.ps1` lulus.
8. Reviewer domain menyetujui perubahan.

## Mengubah kontrak current

1. Owner AI/Android menyetujui source contract baru.
2. Buat ADR bila topology/auth/compatibility berubah.
3. Ganti salinan `contracts/current/android-api-contract.md` secara byte-identik.
4. Perbarui checksum, fixture, implementation guide, test, dan release checklist.
5. Jalankan quality gate dan integration smoke.
6. Rilis dokumentasi dengan identifier/checksum yang dapat dipin tim.

## Archive policy

Dokumen superseded dipindahkan utuh ke folder versi di `archive/`; jangan dihapus jika masih diperlukan untuk audit. Archive tidak diperiksa sebagai source current dan tidak boleh muncul dalam onboarding utama selain sebagai tautan audit yang jelas.

