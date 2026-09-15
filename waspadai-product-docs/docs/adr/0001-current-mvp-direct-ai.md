# ADR-0001: Direct Public AI untuk MVP Android

- Status: Accepted
- Date: 2026-09-15
- Owners: Android lead, AI contract owner, Product owner
- Supersedes: ADR Product–AI baseline 3.0.1

## Context

Dokumentasi 3.0.1 menetapkan Android→Product Backend→internal AI sebagai keputusan current. Kontrak `android-api-contract.md` yang ditetapkan tim sebagai source of truth menyatakan mode MVP berbeda: Android memanggil public WaspadAI API langsung setelah login Supabase diperiksa di aplikasi.

Mencampur kedua mode menyebabkan path, auth, request field, response wrapper, error behavior, dan scope fitur yang berbeda.

## Decision

Mode current adalah Android→public WaspadAI API:

- `/api/v1/verify/text` dan `/api/v1/verify/image`;
- tanpa Bearer Supabase ke AI;
- tanpa internal API key di Android;
- `output_mode=BOTH`;
- direct AI response, tanpa wrapper Product;
- timeout client 120 detik;
- history/community/vote bukan bagian public AI current.

Supabase login tetap menjadi gate fitur di Android, tetapi bukan auth yang divalidasi public AI.

## Consequences

Positif: MVP dapat terintegrasi tanpa menunggu Product Backend, wire contract sederhana, dan secret internal tidak masuk APK.

Trade-off: gating bukan authorization server-side pada API AI; tidak ada ownership/history/community server-side; public endpoint memerlukan kontrol kapasitas dan abuse protection di sisi AI; idempotency belum dijamin.

## Compliance

Kontrak normatif berada di [`../../contracts/current/android-api-contract.md`](../../contracts/current/android-api-contract.md). Contract test harus menolak plural Product paths, Bearer header ke public AI, wrapper-only parser, dan internal key di client.

