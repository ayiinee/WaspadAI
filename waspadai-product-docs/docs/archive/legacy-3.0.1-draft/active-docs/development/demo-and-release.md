# Demo dan Release

[Kembali ke indeks setup](setup.md)

## 18. Demo runbook

Sebelum demo: HP/charger/kabel/USB, Wi-Fi dan hotspot cadangan, akun uji dua user/moderator tanpa data pribadi, confirm AI health/key dengan input sintetis, pastikan branch/tag/schema/config yang sama, clear private inputs, review empat golden cases, timeout labels, history policy dan mock badge.

Urutan: login→overlay→consent→crop→verifikasi OTP sintetis→narasi/dimensi/safe action→kasus insufficient evidence→history→lesson/quiz/progress→preview sanitized/consent→vote→moderator decision→index evidence **hanya jika nyata teruji**. Jangan memasukkan kredensial/OTP nyata dalam screenshot.

Fallback: video lokal dari rehearsal, screenshot hasil yang disanitasi, mock berlabel jelas jika dipilih. Tunjukkan bahwa simulasi bukan output live. Jika remote gagal, jelaskan kegagalan dan gunakan cadangan, tidak mengubah response mode diam-diam.

Rehearsal dua kali berturut pada HP final; rekam durasi end-to-end, version metadata, known limitations dan action owner. Setelah demo: revoke akun/key sementara bila diperlukan, bersihkan screenshot sesuai consent/retensi, pastikan asset/index demo tidak tertinggal di feed production.

## 19. Checklist release final

- [ ] Full upstream SHA/OpenAPI/hash telah diverifikasi; schema sesuai unggahan dan remote compatibility diuji.
- [ ] History policy, vote method, image field limit dan community visibility disepakati.
- [ ] Product source/scaffold/locks/migrations/tests benar-benar tersedia, bukan hanya dokumen.
- [ ] Runtime bukan postgres admin; RLS dan claims isolation lulus.
- [ ] Production remote, debug off, TLS verify on; APK/bundle/log bebas secret.
- [ ] Idempotency unknown/persistence-only/delete-replay tests lulus.
- [ ] Permission/capture/OCR/image/result/history berjalan di HP final.
- [ ] Learning/quiz/progress dan community consent/moderation sesuai scope.
- [ ] Storage cleanup teruji; RAG sync hanya aktif dengan kontrak/ack/retract yang nyata.
- [ ] Deployment rollback dan backup disiapkan.
- [ ] Dua rehearsal, video cadangan, signed build dan dokumen versi rilis tersedia.

## 20. Referensi verifikasi tim

Tautan berikut diwarisi dari sumber terdahulu dan belum dibuka ulang pada penyusunan paket: [Supabase local development](https://supabase.com/docs/guides/local-development), [migration](https://supabase.com/docs/guides/deployment/database-migrations), [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [database connections](https://supabase.com/docs/guides/database/connecting-to-postgres), [JWT](https://supabase.com/docs/guides/auth/jwts), [Storage access control](https://supabase.com/docs/guides/storage/security/access-control). Gunakan docs resmi yang cocok dengan versi pinned saat implementasi; jangan menjalankan perintah arsip sebagai pengganti review versi.

## Validasi snapshot unggahan pada pembaruan 3.0.1

File upstream tidak diubah: termasuk header internal required:false dan tidak adanya securitySchemes. Product tetap mengirim key sesuai keputusan boundary; uji auth runtime terpisah. Product OpenAPI dinaikkan ke3.1.0 untuk mempertahankan const/anyOf null dari schema AI tanpa konversi lossy. Code generator Android harus mendukung3.1 atau menggunakan adapter DTO tervalidasi. Fixture mengandung result.mode=LIVE agar valid terhadap schema, tetapi execution_mode=MOCK pada envelope dan narasi SIMULASI menandai sumber sintetis. Dilarang menjadikan mode LIVE pada fixture sebagai bukti call remote. Uji negatif wajib: missing required field, extra field AI/evidence, question=null, wrong mode, malformed page_context. Integration smoke, deadline, ukuran image runtime dan key enforcement tetap belum diuji.
