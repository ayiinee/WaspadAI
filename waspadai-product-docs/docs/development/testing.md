# Testing Strategy

Status: `CURRENT`.

## Product API contract tests

- plural path `/api/v1/verifications/text`;
- Bearer auth dan UUID `Idempotency-Key` wajib;
- text 10–25.000, question 500, public `source_url`, dan `page_context` bounds;
- `output_mode`/unknown client field ditolak;
- wrapper `history`, `result`, `execution_mode` tervalidasi;
- idempotency replay dan conflict;
- history owner-only dan cross-user 404.

## Database/community target tests

- hanya post/case `VERIFIED_EVIDENCE` aktif;
- contribution `VERIFIED` dan moderation terbaru `VERIFY + allow_rag`;
- publication dan `RAG_REUSE` consent aktif, owner/target/hash cocok;
- withdrawal, revoke, expiry, delete, retract, dan revision baru mengecualikan record lama;
- `PUBLISHED_UNVERIFIED`, vote, AI result lama, raw asset/path, dan PII tidak pernah masuk payload;
- community evidence kosong tetap menghasilkan request valid;
- stale outbox UPSERT tidak menghidupkan kembali tombstone.

## Integration

Uji Supabase Auth, database role/RLS, Product API, dan internal AI pada staging dengan data sintetis. Remote integration baru dianggap aktif bila exported schema kedua service kompatibel dan smoke test membuktikan request/response aktual.

## Android context trigger

- Unit: normalisasi `AssistStructure`, prioritas screenshot, state machine tanpa network sebelum konfirmasi, `CRITICAL`, full result mapping, dan pembersihan buffer pending.
- Auth: session restore melalui encrypted store abstraction, refresh/retry, dan clear saat logout.
- Intent: share teks, satu gambar, banyak gambar, MIME tidak valid, URI tidak terbaca, file kosong, ukuran, dan batas lima attachment.
- Device: Android 10, Android 14, Android 16/targetSdk 36; minimal satu Pixel/AOSP dan satu Samsung/OEM.
- Manual: WhatsApp, browser, secure window, multi-window, rotasi, session expired, lock screen, dan mengembalikan default assistant ke Gemini/assistant sebelumnya.

Perintah lokal Android:

```powershell
cd frontend
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Verifikasi dokumentasi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1
```

