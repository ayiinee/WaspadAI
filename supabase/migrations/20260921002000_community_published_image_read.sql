-- Allow authenticated viewers to read metadata for the redacted image of a
-- published community post. The Product API still downloads the object with
-- the service-role key and exposes only the image proxy endpoint.
drop policy if exists stored_assets_published_community_read on private.stored_assets;
create policy stored_assets_published_community_read
on private.stored_assets
for select to product_app using (
    deleted_at is null
    and purpose in ('SCREENSHOT_OPT_IN', 'COMMUNITY_PREVIEW')
    and exists (
        select 1
          from public.community_posts p
          join private.consent_records consent
            on consent.id = p.publication_consent_id
         where p.redacted_asset_id = stored_assets.id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and consent.scope = 'COMMUNITY_PUBLICATION'
           and consent.revoked_at is null
           and (consent.expires_at is null or consent.expires_at > now())
    )
);
