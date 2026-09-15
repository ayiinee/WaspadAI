# Scope dan Status Implementasi

Status: `CURRENT`.

Dokumen ini membedakan requirement current dari target fase berikutnya. Status fitur harus diperbarui berdasarkan kode dan test, bukan berdasarkan keberadaan desain.

## Scope berdasarkan fase

| Kapabilitas | Fase | Kontrak/owner | Catatan |
| --- | --- | --- | --- |
| Supabase login di Android | MVP | Supabase SDK + Android | Session aktif menjadi precondition fitur AI |
| Verifikasi teks | MVP | Public WaspadAI API | `POST /api/v1/verify/text` |
| Verifikasi screenshot | MVP | Public WaspadAI API | Multipart ke `POST /api/v1/verify/image` |
| Narasi dan detail hasil | MVP | Direct AI response | Tanpa wrapper Product |
| Retry/error UX | MVP | Android | Mapping berbasis HTTP status/FastAPI detail |
| History lintas perangkat | Future | Product Backend + database | Belum tersedia dari API AI publik |
| Community preview/publish | Future | Product Backend + Storage | Membutuhkan redaksi dan consent server-side |
| Community feed dan voting | Future | Product Backend | Vote bukan verdict faktual |
| Ownership dan moderation | Future | Product Backend | Otorisasi harus server-side |
| Knowledge indexing komunitas | Future/Open | AI + Product Backend | Kontrak sinkronisasi belum current |

## Status repository aplikasi saat baseline

Repository aplikasi yang diaudit berisi scaffold Android Compose, health/readiness backend, migration awal, dan tooling. Login, layar pemeriksaan, integrasi API AI end-to-end, serta fitur future tidak boleh disebut selesai hanya karena dokumennya ada.

Saat status implementasi berubah, update tabel ini dalam PR yang sama dengan perubahan kode atau sertakan link bukti build/test/release.

## Out of scope MVP

- menyimpan service key WaspadAI di Android;
- memanggil `/api/internal/v1/*` dari Android;
- mengirim token Supabase ke public WaspadAI API;
- mengasumsikan `Idempotency-Key` diproses oleh public WaspadAI API;
- membuat wrapper `result/history` di client seolah berasal dari AI;
- mengiklankan history, community, vote, atau moderation sebelum Product Backend tersedia.

