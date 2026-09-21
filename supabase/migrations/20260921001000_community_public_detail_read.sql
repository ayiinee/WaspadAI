-- Allow the Product API to resolve public community detail across users.
-- Feed rows are already public, but detail previously joined owner-scoped
-- verification tables, making GET /community/{case_id} return a false 404
-- for every viewer except the case owner.

drop policy if exists verification_cases_published_community_read on public.verification_cases;
create policy verification_cases_published_community_read
on public.verification_cases
for select to product_app using (
    community_state in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
    and deleted_at is null
    and retention_expires_at > now()
    and exists (
        select 1
          from public.community_posts p
         where p.case_id = verification_cases.id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);

drop policy if exists verification_results_published_community_read on public.verification_results;
create policy verification_results_published_community_read
on public.verification_results
for select to product_app using (
    exists (
        select 1
          from public.verification_cases c
          join public.community_posts p on p.case_id = c.id
         where c.id = verification_results.case_id
           and c.deleted_at is null
           and c.community_state in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);

drop policy if exists consent_records_published_community_read on private.consent_records;
create policy consent_records_published_community_read
on private.consent_records
for select to product_app using (
    scope = 'COMMUNITY_PUBLICATION'
    and revoked_at is null
    and (expires_at is null or expires_at > now())
    and exists (
        select 1
          from public.community_posts p
         where p.publication_consent_id = consent_records.id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
    )
);
