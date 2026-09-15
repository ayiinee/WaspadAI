# Auth, AI, Android, dan Web Setup

[Kembali ke indeks setup](setup.md)

## 8. Auth validation implementation

MVP get_user: Product memakai Supabase Auth untuk memverifikasi access token dan memperoleh user trusted. Token invalid→401; Auth service unreachable→503, bukan menganggap token valid. Gunakan metadata server untuk role; jangan mengambil role dari raw_user_meta_data.

JWKS target: pin issuer/audience/algorithm allowlist, validate signature/exp/nbf/sub UUID, cache JWKS, refresh terbatas saat kid unknown, fail closed. Jangan membiarkan header alg menentukan algoritma tanpa allowlist. Status disabled/deleted user memerlukan policy pemeriksaan server, bukan sekadar JWT signature. HS256 legacy jika memang dipakai memerlukan keputusan/config terpisah; `SUPABASE_JWT_SECRET` tidak diasumsikan selalu dibutuhkan.

Android refresh single-flight satu kali pada401 Product. Refresh token tidak dikirim ke Product atau AI. Test expired/wrong issuer/audience/signature, revoked/disabled behavior yang dipilih, dan pemisahan 401 user vs503 internal AI key.

## 9. Remote/mock AI setup

Mock: mode mock, fixtures sintetis, UI SIMULASI, tidak butuh AI URL/key. Production startup harus menolak mock.

Remote: isi confirmed URL/key, salin OpenAPI asli ke contracts/upstream, isi full SHA/hash/version/source reference, validasi mappings dan pin. Snapshot unggahan sudah disertakan dan hash telah dicek; full commit dan hasil smoke remote masih belum terverifikasi.

Health GET tanpa key bila kontrak mengizinkan dapat diperiksa operator. Internal verification smoke harus menggunakan key server dari secret manager; hindari key literal dalam command history. Script test membaca env dan tidak mencetak header. Smoke dilakukan dengan input sintetis, batas biaya dan izin tim; paket ini tidak mengirim request inferensi live.

Tidak mengirim Supabase token kepada AI. Jangan menguji internal key dengan memasangnya di URL/browser/Android. AI401 berarti perbaiki konfigurasi server, bukan meminta user login ulang.

## 10. Android setup dan physical device

Setelah Android project tersedia, buka `frontend` di Android Studio, sync Gradle, pilih SDK/JDK sesuai pin. Baseline memakai compileSdk/targetSdk 36 tanpa minor SDK 36.1. Buat local.properties tanpa commit credential/private paths:

```properties
PRODUCT_API_BASE_URL=http://127.0.0.1:8001
SUPABASE_URL=https://REPLACE_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=REPLACE_PUBLIC_KEY
```

Implementasi Gradle harus memuat properti ke BuildConfig/config sesuai variant; menulis properties sendiri tidak membuatnya otomatis tersedia. Jangan overwrite sdk.dir yang dibuat Android Studio.

USB debugging:

```bash
adb devices
adb reverse tcp:8001 tcp:8001
```

Device fisik melalui reverse dapat mengakses backend laptop127.0.0.1:8001. Emulator umumnya memakai10.0.2.2 untuk host; verifikasi environment emulator tim. Jangan memakai localhost device tanpa reverse dan mengira itu laptop.

Baseline tanpa local stack memakai URL Supabase hosted development yang sama untuk HP dan backend laptop. `adb reverse` hanya diperlukan untuk Product API di laptop; jangan expose port database ke jaringan publik atau memasukkan DB credential ke Android.

Build/test dari folder Android:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Windows menggunakan gradlew.bat. Task names mengikuti plugin/variant nyata. Signing keystore/password tetap di secret manager, release upload artifact dikontrol tim. Periksa izin overlay, foreground capture, sistem consent, revoke, rotation/background, secure screen, stop dan cleanup pada perangkat demo.

## 11. Web opsional

Setelah web scaffold dan package-lock tersedia:

```bash
npm ci --prefix frontend/web
npm run dev --prefix frontend/web
```

Public config: NEXT_PUBLIC_PRODUCT_API_BASE_URL, NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY. Server secrets tidak memakai prefix NEXT_PUBLIC. Production CORS origin allowlist, tidak wildcard dengan credentials. Auth redirect harus sama dengan environment. Web AI existing tidak perlu dipindah untuk menjalankan Android Product.
