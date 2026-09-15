# Development Setup

Status: `CURRENT`.

## Prasyarat Android

- Android Studio versi stabil yang mendukung project;
- JDK dan Android SDK sesuai version catalog/Gradle project;
- perangkat atau emulator dengan akses internet;
- Supabase development URL dan publishable key melalui local config;
- public AI base URL `https://waspadai.shafwan.digital`.

Gunakan Gradle wrapper repository, bukan Gradle global:

```powershell
Set-Location frontend
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```

## Environment boundary

Konfigurasi client yang boleh ada di Android:

- Supabase URL;
- Supabase publishable/anon key sesuai kebijakan project;
- public WaspadAI base URL;
- build environment/telemetry flags non-secret.

Konfigurasi yang dilarang di Android:

- Supabase service-role key;
- `X-Waspadai-API-Key` atau internal AI key;
- database URL/password;
- signing secret atau credential server lainnya.

Jangan commit `.env`, local properties berisi credential, keystore, token, screenshot pengguna, atau request/response production.

## Verifikasi konektivitas

1. Pastikan perangkat dapat membuka `GET https://waspadai.shafwan.digital/api/health`.
2. Jalankan verifikasi teks sintetis non-sensitif melalui build development.
3. Pastikan request menuju `/api/v1/verify/text`, tanpa header Authorization.
4. Pastikan response dibaca dari top-level dan narrative dari `presentation.narrative.text`.
5. Uji image kecil sintetis melalui multipart `/api/v1/verify/image`.

Gunakan fixture lokal untuk unit/UI test. Call remote harus eksplisit, tidak dijalankan diam-diam pada unit test atau CI tanpa environment yang disetujui.

## Product Backend repository

Scaffold backend yang mungkin ada di repository aplikasi tidak menjadi dependency verifikasi MVP. Jalankan dan kembangkan backend hanya untuk fitur future yang disetujui; jangan mengalihkan Android dari public AI ke Product Backend sebelum [exit criteria](../architecture/future-product-backend.md) terpenuhi.

