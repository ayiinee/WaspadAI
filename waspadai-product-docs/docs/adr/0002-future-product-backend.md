# ADR-0002: Product Backend sebagai Gateway Android–AI

- Status: Accepted
- Effective date: 2026-09-16
- Owners: Product owner, Backend lead, Android lead, AI contract owner
- Supersedes: ADR-0001

## Context

History, database, private Storage, ownership, community, consent, voting, dan moderation memerlukan server yang memvalidasi identity. Community evidence juga harus dipilih berdasarkan state database yang tidak boleh dipercaya atau dirakit oleh Android.

## Decision

- Android mengirim Bearer access token Supabase ke Product API.
- Product API mengambil `sub` sebagai `user_id`, mengelola state Product, dan memanggil internal WaspadAI dengan `X-Waspadai-API-Key`.
- Android tidak memanggil public/internal WaspadAI secara langsung.
- WaspadAI tidak menerima credential Supabase dan tidak mengakses database Product.
- Product API memilih community evidence relevan yang memenuhi seluruh gate Bagian 11 kontrak kanonik, lalu mengirim proyeksi sanitized.
- Vote, post unverified, hasil AI lama, dan record tanpa consent `RAG_REUSE` bukan factual evidence.

## Status implementasi

Keputusan topology berlaku, tetapi capability dirilis bertahap. Saat ADR diterima, text verification masih `MOCK`; remote AI, image, community API, moderation service, dan community evidence transport masih target. Status runtime harus mengikuti kode/test, bukan status ADR.

