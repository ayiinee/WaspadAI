# Setup dan Handoff Tim WaspadAI

Source of truth integrasi adalah [`android-api-contract.md`](android-api-contract.md). Keputusan topology saat ini adalah Android→Product API→internal WaspadAI. Status implementasi tidak boleh disimpulkan hanya dari contract atau migration.

## Urutan membaca

1. [`README.md`](README.md)
2. [`waspadai-product-docs/README.md`](waspadai-product-docs/README.md)
3. [`android-api-contract.md`](android-api-contract.md)
4. [`waspadai-product-docs/docs/product/scope-and-status.md`](waspadai-product-docs/docs/product/scope-and-status.md)
5. [`waspadai-product-docs/docs/development/testing.md`](waspadai-product-docs/docs/development/testing.md)

## Tool dan quality gate

Gunakan Git, Node/npm, JDK 21, Android Studio/SDK, Python 3.11, dan `uv`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\waspadai-product-docs\scripts\verify-docs.ps1
Set-Location backend
uv sync --locked
uv run ruff check .
uv run pytest
```

## Android contract

- Product API base URL berasal dari build config.
- Text path: `/api/v1/verifications/text`.
- Kirim Bearer access token Supabase dan UUID `Idempotency-Key`.
- Jangan kirim `output_mode`, community evidence, internal AI key, atau database credential.
- Baca hasil dari `result.presentation.narrative.text`.
- Timeout Android 150 detik; refresh session dan retry `401` paling banyak sekali dengan key yang sama.
- Image route masih `TARGET`.

## Backend saat ini

Backend menyediakan auth, health/readiness, text verification `MOCK`, idempotency, dan history owner-only. Isi `.env` lokal dengan secret development yang diperlukan; `.env` sudah di-ignore dan tidak boleh di-commit.

```powershell
Set-Location backend
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

Remote AI, image, community/moderation API, dan community evidence transport belum runtime. Saat menambahkannya, ikuti Bagian 11 kontrak kanonik dan uji revocation, expiry, withdrawal, retraction, revision, consent, serta data leakage.

## Supabase

Ikuti [`supabase/README.md`](supabase/README.md). Migration memakai credential operator terpisah; runtime memakai role `product_app`. Gunakan project development dan dua user sementara untuk test isolasi RLS, lalu hapus user test setelah selesai.

## Handoff

- Kontrak root dan salinan current harus byte-identik.
- Docs gate, backend test, Android build, dan staging smoke relevan harus lulus.
- Owner Android/Backend/AI menyetujui endpoint, auth, payload, response, timeout, dan batas community evidence.
- Capability target tetap feature-gated sampai implementasi dan rollback terbukti.
