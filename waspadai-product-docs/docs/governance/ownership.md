# Ownership dan Review

Status: `CURRENT`.

| Area | Accountable owner | Required reviewer |
| --- | --- | --- |
| Android UX, session gate, dan public AI adapter | Android lead | Product + AI contract owner |
| Public/internal AI API dan pipeline | AI lead | Android lead untuk public contract |
| Product scope dan acceptance | Product owner | Engineering + QA |
| Product Backend future | Backend lead | Security, Android, AI lead |
| Supabase data/RLS/Storage future | Backend/data owner | Security reviewer |
| Documentation structure dan release | Tech lead | Owner domain yang berubah |
| Contract/regression testing | QA lead | Android + AI lead |

## Handoff rule

Perubahan contract tidak cukup disampaikan lewat chat. PR/release note harus memuat:

- alasan dan owner;
- status `CURRENT` atau `FUTURE`;
- endpoint, auth, request, response, error, timeout, dan compatibility impact;
- fixture/test yang berubah;
- migration dan rollback Android/backend;
- tanggal berlaku dan target environment.

Secret diserahkan melalui secret manager, bukan dokumentasi, issue, chat, screenshot, atau fixture.

