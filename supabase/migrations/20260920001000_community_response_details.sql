-- Persist the full "Beri Penilaian" response alongside the classification vote.
-- One user can keep one current response per published community post.

alter table public.community_votes
    add column if not exists reasoning text not null default ''
        check (char_length(reasoning) <= 5000),
    add column if not exists evidence_asset_id uuid null
        references private.stored_assets (id) on delete set null;

create index if not exists community_votes_post_created_idx
    on public.community_votes (post_id, created_at desc);

drop policy if exists stored_assets_community_response_read on private.stored_assets;
create policy stored_assets_community_response_read on private.stored_assets
for select to product_app using (
    purpose = 'CONTRIBUTION_EVIDENCE'
    and deleted_at is null
    and exists (
        select 1
          from public.community_votes v
          join public.community_posts p on p.id = v.post_id
         where v.evidence_asset_id = stored_assets.id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);
