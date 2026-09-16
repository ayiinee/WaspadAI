# Android Architecture

[Kembali ke indeks arsitektur sistem](system-and-folder-architecture.md)

## 3. Android: struktur rinci

Satu Gradle application module untuk MVP; fitur dipisah package, bukan puluhan Gradle module sejak awal. Android Gradle root DECIDED berada langsung di `frontend/`; root package dan applicationId DECIDED `id.waspadai.app`.

```text
frontend/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/id/waspadai/app/
│   │   │   ├── WaspadAIApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── core/
│   │   │   │   ├── common/{AppResult,CoroutineDispatchers,ClockProvider}.kt
│   │   │   │   ├── model/{VerificationResult,HistoryItem,CommunityState}.kt
│   │   │   │   ├── network/
│   │   │   │   │   ├── ProductApi.kt
│   │   │   │   │   ├── ApiClient.kt
│   │   │   │   │   ├── AuthInterceptor.kt
│   │   │   │   │   ├── TokenRefreshCoordinator.kt
│   │   │   │   │   ├── IdempotencyKeyStore.kt
│   │   │   │   │   ├── NetworkMonitor.kt
│   │   │   │   │   ├── ApiErrorMapper.kt
│   │   │   │   │   └── dto/
│   │   │   │   ├── auth/{SessionManager,SupabaseAuthClient,TokenProvider}.kt
│   │   │   │   ├── database/{WaspadAIDatabase,CachePolicy}.kt
│   │   │   │   ├── database/dao/
│   │   │   │   ├── database/entity/
│   │   │   │   ├── datastore/{AppPreferences,PermissionPreferences}.kt
│   │   │   │   ├── permissions/
│   │   │   │   │   ├── PermissionCoordinator.kt
│   │   │   │   │   ├── OverlayPermissionManager.kt
│   │   │   │   │   └── MediaProjectionPermissionManager.kt
│   │   │   │   ├── overlay/{FloatingVerifyService,OverlayController,OverlayState}.kt
│   │   │   │   ├── capture/
│   │   │   │   │   ├── ScreenCaptureService.kt
│   │   │   │   │   ├── MediaProjectionController.kt
│   │   │   │   │   ├── ScreenshotProcessor.kt
│   │   │   │   │   └── CaptureResult.kt
│   │   │   │   ├── ocr/{OcrEngine,OcrResult,TextBlockMapper}.kt
│   │   │   │   ├── privacy/{SensitiveTextRedactor,ConsentController}.kt
│   │   │   │   ├── image/{ImageCropper,ImageCompressor,ImageSanitizer}.kt
│   │   │   │   ├── navigation/{AppDestination,AppNavHost}.kt
│   │   │   │   ├── designsystem/{component,icon,theme,token}/
│   │   │   │   └── telemetry/{AnalyticsTracker,SafeCrashReporter}.kt
│   │   │   ├── feature/
│   │   │   │   ├── auth/
│   │   │   │   ├── onboarding/
│   │   │   │   ├── home/
│   │   │   │   ├── verification/
│   │   │   │   ├── result/
│   │   │   │   ├── history/
│   │   │   │   ├── learning/
│   │   │   │   ├── quiz/
│   │   │   │   ├── progress/
│   │   │   │   ├── community/
│   │   │   │   ├── contribution/
│   │   │   │   └── profile/
│   │   │   ├── worker/{ProgressSyncWorker,PrivateCacheCleanupWorker}.kt
│   │   │   └── di/{AppModule,NetworkModule,DatabaseModule,RepositoryModule}.kt
│   │   └── res/{drawable,font,mipmap,values,xml}/
│   ├── src/test/
│   ├── src/androidTest/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/wrapper/
├── gradle/libs.versions.toml
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties.example
```

Kurung kurawal pada tree hanya notasi pemadatan nama file sejenis, bukan nama path literal. Cache Room opsional untuk data sanitized/learning; bukan penyimpanan screenshot atau token plaintext. Jangan menambah PendingVerificationWorker yang menyimpan gambar diam-diam. Durable verification queue membutuhkan perubahan consent/retention dan bukan default.

### Struktur setiap feature

```text
feature/verification/
├── data/
│   ├── VerificationRepositoryImpl.kt
│   ├── VerificationRemoteDataSource.kt
│   └── mapper/VerificationMapper.kt
├── domain/
│   ├── VerificationRepository.kt
│   ├── SubmitTextVerificationUseCase.kt
│   └── SubmitImageVerificationUseCase.kt
└── presentation/
    ├── VerificationScreen.kt
    ├── VerificationViewModel.kt
    ├── VerificationUiState.kt
    ├── VerificationAction.kt
    └── component/
```

Dependency source: presentation bergantung pada domain; data mengimplementasikan interface domain; composition root/DI menghubungkan implementasi. Domain tidak mengimpor Retrofit/Supabase/Compose. Urutan panggilan runtime UI→ViewModel→use case→repository bukan berarti domain harus mengimpor implementation data.

### Kontrak perilaku Android

- Composable tidak memanggil HTTP; ViewModel memiliki state/action dan cancellation yang jelas.
- Session refresh single-flight; concurrent 401 tidak membuat refresh storm.
- Access token ditempel ke Product saja; interceptor tidak menempel token ke sembarang URL evidence.
- Key idempotency tetap untuk satu aksi dan payload yang sama; input berubah berarti aksi/key baru.
- Capture service menangani lifecycle/foreground notification/permission menurut Android target yang dipin. Tidak memakai kembali token/projection yang tidak lagi valid.
- ImageReader/VirtualDisplay/MediaProjection release pada semua jalur. Jangan mengakali protected screen.
- Strip metadata, crop dan kompres tanpa merusak keterbacaan; ukuran file/pixel diuji sebelum upload.
- OCR untuk review; image dikirim binary multipart. Tidak ada Base64 JSON.
- Loading UI tidak menjanjikan stage internal AI yang tidak dilaporkan. Untuk synchronous API gunakan “Sedang memeriksa”, elapsed time dan aksi aman.
- Content image/text tidak dipersist sebelum consent. Logout membersihkan cache yang terikat user.
- Network security cleartext hanya build debug dan host lokal yang diperlukan. Release wajib TLS verification aktif.
- `backup_rules.xml`/`data_extraction_rules.xml` mengecualikan session/private cache; service secrets tidak pernah ada di BuildConfig.
