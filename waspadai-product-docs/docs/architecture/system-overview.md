# System Overview

Status: `CURRENT`, kecuali diagram future yang ditandai.

## Context current

```text
┌──────────────┐      Supabase SDK       ┌──────────────┐
│ Android app  │ ───────────────────────> │ Supabase Auth│
│              │                          └──────────────┘
│              │      public HTTPS
│              │ ───────────────────────> ┌────────────────────┐
└──────────────┘                          │ WaspadAI API       │
                                         │ fact-check pipeline │
                                         └────────────────────┘
```

Trust boundary current:

- Android menyimpan session pengguna melalui mekanisme aman Supabase SDK.
- Android hanya memakai public WaspadAI API untuk pemeriksaan.
- WaspadAI API tidak menerima atau memvalidasi credential pengguna Product pada mode ini.
- Tidak ada service secret atau endpoint internal pada boundary Android.

## Context future

```text
Android ──Bearer Supabase──> Product Backend ──internal API key──> WaspadAI API
                                │
                                ├── Product database
                                └── private Storage/community/moderation
```

Diagram future adalah target untuk fitur server-side. Diagram tersebut tidak menggantikan current flow sampai ADR baru diterima, Product API menjadi current, dan deployment lulus integration test.

## Dependency rule

- Android UI bergantung pada use case/domain, bukan langsung pada transport object.
- Adapter public AI berada di data/network layer dan memetakan wire DTO secara eksplisit.
- Domain tidak menghitung ulang verdict AI.
- Product Backend future tidak berada pada jalur verifikasi current.
- Referensi ke upstream snapshot atau archive tidak boleh mengubah runtime topology.

Keputusan arsitektur dicatat di [ADR-0001](../adr/0001-current-mvp-direct-ai.md) dan [ADR-0002](../adr/0002-future-product-backend.md).

