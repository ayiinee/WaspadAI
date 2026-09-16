# Release Checklist

Status: `CURRENT`.

## Contract

- [ ] Kontrak root dan salinan `contracts/current/` byte-identik serta checksum benar.
- [ ] Android memakai `/api/v1/verifications/text`, Bearer token, dan UUID `Idempotency-Key`.
- [ ] Android tidak mengirim `output_mode` atau community evidence.
- [ ] Response dibaca dari wrapper `result/history/execution_mode`.
- [ ] Endpoint `TARGET` tidak dipresentasikan sebagai runtime.

## Community evidence

- [ ] Seluruh gate Bagian 11 kontrak kanonik diuji pada database aktual.
- [ ] Revocation/withdrawal/retraction/expiry menghilangkan record lama.
- [ ] Vote, post unverified, prior AI result, PII, consent ID, dan private Storage tidak terkirim.
- [ ] Batas array/payload telah dibekukan pada schema internal AI.
- [ ] Contract test lintas repository dan staging smoke lulus.

## Security dan operasi

- [ ] APK tidak memuat internal AI key, service-role key, atau database credential.
- [ ] Product runtime memakai role `product_app` dan transaction-local user claim.
- [ ] Log tidak memuat token, body, screenshot, atau community content.
- [ ] Timeout Android 150 detik dan upstream AI 120 detik.
- [ ] Rollback dan deindex/tombstone behavior diuji.

