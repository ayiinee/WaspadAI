# Release Checklist

Status: `CURRENT`.

## Contract

- [ ] Checksum `contracts/current/android-api-contract.md` cocok dengan source yang disetujui.
- [ ] Android memakai public singular paths `/api/v1/verify/text|image`.
- [ ] Tidak ada Bearer token atau internal API key pada call AI.
- [ ] Text JSON dan image multipart sesuai contract; `output_mode=BOTH`.
- [ ] Direct response dibaca tanpa wrapper `result/history`.

## Security dan privacy

- [ ] APK/repository scan tidak menemukan service key, service-role key, atau password.
- [ ] Network log tidak memuat token, message body, screenshot, atau sensitive response.
- [ ] Cleartext traffic dinonaktifkan untuk production.
- [ ] Preview/crop dan temporary file cleanup diuji.
- [ ] Privacy copy tidak menjanjikan persistence/community yang belum tersedia.

## Quality

- [ ] Unit, contract, UI, dan build lulus.
- [ ] Remote smoke test memakai data sintetis dan endpoint environment yang benar.
- [ ] Timeout 120 detik serta error `413`, `415`, `422`, `429`, dan `5xx` memiliki UX.
- [ ] Accessibility minimum: screen reader label, focus order, contrast, dan scalable text.
- [ ] Monitoring membedakan client/network/API error tanpa merekam PII.

## Documentation dan handoff

- [ ] `scripts/verify-docs.ps1` lulus.
- [ ] Scope/status sesuai kode yang dirilis.
- [ ] Perubahan contract memiliki ADR/changelog dan owner review.
- [ ] Future/archive tidak ditautkan sebagai petunjuk current.
- [ ] Rollback build/config telah diuji.

