-- Published posts can contain multiple media rows. Allow the Product API to
-- read asset metadata for both the legacy cover image and every published
-- community_media attachment.

drop policy if exists stored_assets_published_community_read on private.stored_assets;
create policy stored_assets_published_community_read
on private.stored_assets
for select to product_app using (
    deleted_at is null
    and purpose in ('SCREENSHOT_OPT_IN', 'COMMUNITY_PREVIEW')
    and exists (
        select 1
          from public.community_posts post
          join private.consent_records consent
            on consent.id = post.publication_consent_id
         where (
                   post.redacted_asset_id = stored_assets.id
                   or exists (
                       select 1
                         from public.community_media media
                        where media.community_id = post.id
                          and media.asset_id = stored_assets.id
                   )
               )
           and post.withdrawn_at is null
           and post.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and consent.scope = 'COMMUNITY_PUBLICATION'
           and consent.revoked_at is null
           and (consent.expires_at is null or consent.expires_at > now())
    )
);

create index if not exists community_media_asset_idx
    on public.community_media(asset_id);
