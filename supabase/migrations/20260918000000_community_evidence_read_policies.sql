-- Read-only projection gates for Product API community evidence retrieval.
-- These policies expose only rows needed to validate VERIFIED_EVIDENCE records
-- that already passed publication and RAG consent gates. The API still sends
-- only sanitized DTOs to WaspadAI.

drop policy if exists consent_records_publication_feed_read on private.consent_records;

create policy verification_cases_verified_community_evidence_read
on public.verification_cases
for select to product_app using (
    community_state = 'VERIFIED_EVIDENCE'
    and deleted_at is null
    and retention_expires_at > now()
    and exists (
        select 1
          from public.community_posts p
         where p.case_id = verification_cases.id
           and p.status = 'VERIFIED_EVIDENCE'
           and p.withdrawn_at is null
           and p.verified_at is not null
    )
);

create policy contributions_verified_community_evidence_read
on public.contributions
for select to product_app using (
    status = 'VERIFIED'
    and verified_at is not null
    and retracted_at is null
    and exists (
        select 1
          from public.community_posts p
         where p.verification_contribution_id = contributions.id
           and p.status = 'VERIFIED_EVIDENCE'
           and p.withdrawn_at is null
           and p.verified_at is not null
    )
);

create policy contribution_sources_verified_community_evidence_read
on public.contribution_sources
for select to product_app using (
    exists (
        select 1
          from public.contributions c
          join public.community_posts p on p.verification_contribution_id = c.id
         where c.id = contribution_sources.contribution_id
           and c.status = 'VERIFIED'
           and c.verified_at is not null
           and c.retracted_at is null
           and p.status = 'VERIFIED_EVIDENCE'
           and p.withdrawn_at is null
           and p.verified_at is not null
    )
);

create policy moderation_decisions_verified_community_evidence_read
on public.moderation_decisions
for select to product_app using (
    action = 'VERIFY'
    and new_status = 'VERIFIED'
    and allow_rag = true
    and exists (
        select 1
          from public.contributions c
          join public.community_posts p on p.verification_contribution_id = c.id
         where c.id = moderation_decisions.contribution_id
           and c.status = 'VERIFIED'
           and c.verified_at is not null
           and c.retracted_at is null
           and p.status = 'VERIFIED_EVIDENCE'
           and p.withdrawn_at is null
           and p.verified_at is not null
    )
);

create policy consent_records_verified_community_evidence_read
on private.consent_records
for select to product_app using (
    scope in ('COMMUNITY_PUBLICATION', 'RAG_REUSE')
    and revoked_at is null
    and (expires_at is null or expires_at > now())
    and exists (
        select 1
          from public.community_posts p
         where (
                p.publication_consent_id = consent_records.id
                or p.rag_consent_id = consent_records.id
               )
           and p.status = 'VERIFIED_EVIDENCE'
           and p.withdrawn_at is null
           and p.verified_at is not null
           and p.owner_id = consent_records.user_id
           and p.case_id = consent_records.case_id
           and p.preview_id = consent_records.preview_id
           and p.content_hash = consent_records.content_hash
    )
);
