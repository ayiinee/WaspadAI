# Ownership dan Review

Status: `CURRENT`.

| Area | Accountable owner | Required reviewer |
| --- | --- | --- |
| Android UX, Supabase session, Product API adapter | Android lead | Backend + Product |
| Product API, auth, database query, idempotency | Backend lead | Security + Data |
| Community eligibility, consent, moderation | Backend/Data owner | Product + Security + AI |
| Internal AI API dan fact-check pipeline | AI lead | Backend contract owner |
| Database migration/RLS/Storage | Data owner | Backend + Security |
| Contract/regression testing | QA lead | Android + Backend + AI |
| Documentation release | Tech lead | Owner domain yang berubah |

Perubahan community payload harus memperbarui kontrak kanonik, exported schema kedua service, fixtures, test, dan rollback/deindex behavior dalam release yang sama. Secret diserahkan melalui secret manager, bukan dokumentasi atau chat.

