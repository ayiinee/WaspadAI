-- Published community images remain in private Storage, but Product API must
-- be able to read their metadata while serving an authenticated image proxy.

create policy stored_assets_published_community_read on private.stored_assets
for select to product_app using (
    exists (
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
