-- Community classification votes are intentionally separate from verification
-- verdicts. A user may cast one classification per published community post.

alter table public.community_votes
    drop constraint if exists community_votes_vote_check;

alter table public.community_votes
    add constraint community_votes_vote_check
    check (vote in ('HOAKS', 'WASPADA', 'VALID'));

create index if not exists community_votes_post_updated_idx
    on public.community_votes (post_id, updated_at desc);

create policy community_votes_public_aggregate_read on public.community_votes
for select to product_app using (
    exists (
        select 1
          from public.community_posts p
         where p.id = post_id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);

-- The API applies the same publication and ownership gates before writing. The
-- policy keeps those gates enforced if a query path is accidentally widened.
drop policy if exists community_votes_non_owner_insert on public.community_votes;
create policy community_votes_non_owner_insert on public.community_votes
for insert to product_app with check (
    user_id = (select auth.uid())
    and exists (
        select 1
          from public.community_posts p
         where p.id = post_id
           and p.owner_id <> (select auth.uid())
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);

drop policy if exists community_votes_own_update on public.community_votes;
create policy community_votes_own_update on public.community_votes
for update to product_app using (
    user_id = (select auth.uid())
    and exists (
        select 1
          from public.community_posts p
         where p.id = post_id
           and p.owner_id <> (select auth.uid())
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
) with check (
    user_id = (select auth.uid())
    and vote in ('HOAKS', 'WASPADA', 'VALID')
);

-- Published posts must carry a live publication consent record. This policy
-- permits the Product API to validate public consent while keeping other
-- consent records owner-scoped.
create policy consent_records_publication_feed_read on private.consent_records
for select to product_app using (
    scope = 'COMMUNITY_PUBLICATION'
    and revoked_at is null
    and (expires_at is null or expires_at > now())
    and exists (
        select 1
          from public.community_posts p
         where p.publication_consent_id = id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
    )
);
