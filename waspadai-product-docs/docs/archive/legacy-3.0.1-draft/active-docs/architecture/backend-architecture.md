# Product Backend Architecture

[Kembali ke indeks arsitektur sistem](system-and-folder-architecture.md)

## 4. Product backend: struktur rinci

```text
backend/app/
├── main.py                              # App factory, lifespan, router registration
├── api/
│   ├── router.py
│   ├── dependencies/
│   │   ├── auth.py                       # Validate token → trusted principal
│   │   ├── roles.py                      # Moderator/admin from trusted DB
│   │   ├── database.py                   # Short scoped transaction
│   │   ├── idempotency.py
│   │   └── request_context.py
│   └── v1/routes/
│       ├── health.py
│       ├── me.py
│       ├── verifications.py
│       ├── history.py
│       ├── community.py
│       ├── contributions.py
│       ├── moderation.py
│       └── learning.py
├── core/
│   ├── config.py                        # Validate env and enforce remote in production
│   ├── security.py
│   ├── exceptions.py
│   ├── rate_limit.py
│   └── logging.py
├── domain/
│   ├── verification/{models,policies}.py
│   ├── community/{models,state_machine}.py
│   ├── learning/{models,scoring}.py
│   └── common/{identifiers,clock}.py
├── schemas/
│   ├── common.py
│   ├── verification.py
│   ├── history.py
│   ├── community.py
│   ├── contribution.py
│   ├── moderation.py
│   ├── learning.py
│   └── profile.py
├── services/
│   ├── verification_service.py
│   ├── history_service.py
│   ├── community_service.py
│   ├── preview_service.py
│   ├── voting_service.py
│   ├── contribution_service.py
│   ├── moderation_service.py
│   ├── learning_service.py
│   └── retention_service.py
├── integrations/waspadai_ai/
│   ├── client.py                        # Pooled async HTTP, internal endpoints only
│   ├── schemas.py                       # Exact models dari snapshot unggahan API 0.7.0
│   ├── mapper.py                        # Product ↔ AI; no verdict calculation
│   ├── exceptions.py
│   ├── mock.py                          # Explicit labelled fixture provider
│   └── capabilities.py                  # Verified integration capability configuration
├── repositories/
│   ├── interfaces/
│   └── postgres/
│       ├── verification_repository.py
│       ├── idempotency_repository.py
│       ├── community_repository.py
│       ├── contribution_repository.py
│       ├── moderation_repository.py
│       ├── learning_repository.py
│       ├── outbox_repository.py
│       └── audit_repository.py
├── clients/
│   ├── postgres.py                      # Restricted runtime role, pool and SSL
│   ├── supabase_auth.py
│   └── supabase_storage.py               # Server-controlled paths and signed URLs
├── privacy/
│   ├── text_redaction.py
│   ├── image_validation.py
│   ├── response_projection.py
│   └── consent.py
├── middleware/{request_id,error_handler,access_log}.py
├── workers/
│   ├── community_sync_worker.py
│   ├── retention_worker.py
│   └── idempotency_cleanup_worker.py
└── observability/{metrics,tracing,audit}.py
```

Routes hanya parsing/dependencies/delegation/status response. Service memiliki workflow, domain memiliki invariant/state transition, repository memiliki SQL/transactions. `integrations/waspadai_ai/mapper.py` tidak menyusun prompt, ranking, mengubah risk ataupun membuat evidence. Privacy Product tidak menggantikan privacy enforcement AI.

Tidak ada `backend/app/ai/`, Groq/Tavily/Qdrant client, corpus Rulebook, model weights, embedding generator atau AI evaluation runner. `verification_rulebook_matches` adalah snapshot output, bukan remote retrieval engine.

### Tests dan scripts

```text
backend/tests/
├── unit/{auth,domain,services,integrations,privacy}/
├── integration/{api,postgres,storage}/
├── contract/{test_product_schema,test_ai_fixture,test_upstream_lock}.py
├── security/{test_cross_user,test_role_escalation,test_secret_leak}.py
├── fixtures/{ai_results,requests,errors}/
└── conftest.py
backend/scripts/
├── export_openapi.py
├── seed_learning.py
└── smoke_test.py
```

Tests AI internal tetap di AI repo. Smoke remote membutuhkan persetujuan operasi/test budget tim; unit test tidak bergantung internet atau credential production.
