# System Overview

Status: `CURRENT` untuk topology; capability target ditandai.

```text
Android
  └── Bearer Supabase + Idempotency-Key
       └── Product API
            ├── Supabase Auth
            ├── PostgreSQL/Storage
            └── X-Waspadai-API-Key
                 └── internal WaspadAI fact-check API
```

Trust boundary:

- Android tidak dipercaya untuk `user_id`, role, community state, consent, atau moderation.
- Product API adalah pemilik authorization dan state Product.
- WaspadAI menerima input fact-check dan proyeksi evidence sanitized, bukan token atau row database mentah.
- Public WaspadAI API tetap untuk demo/test terpisah dan bukan endpoint Android production.

Implementasi saat ini berhenti pada text verification `MOCK` dan history. Remote AI serta community evidence adalah target sehingga startup/health backend tidak boleh dianggap bukti integrasi end-to-end.

Keputusan dicatat pada [ADR index](../adr/README.md).

