-- Resolve nullable references that could not be created until both domains
-- existed. Applied separately to keep the dependency order explicit.

alter table public.profiles
    add constraint profiles_avatar_asset_id_fkey
    foreign key (avatar_asset_id) references private.stored_assets (id) on delete set null;

alter table private.stored_assets
    add constraint stored_assets_consent_id_fkey
    foreign key (consent_id) references private.consent_records (id) on delete set null;

alter table private.consent_records
    drop constraint consent_records_check,
    add constraint consent_records_has_target_or_revocation
        check (num_nonnulls(case_id, contribution_id, preview_id) >= 1 or revoked_at is not null),
    add constraint consent_records_contribution_id_fkey
        foreign key (contribution_id) references public.contributions (id) on delete set null,
    add constraint consent_records_preview_id_fkey
        foreign key (preview_id) references public.community_previews (id) on delete set null;

alter table public.contributions
    add constraint contributions_publication_consent_id_fkey
        foreign key (publication_consent_id) references private.consent_records (id) on delete set null,
    add constraint contributions_rag_consent_id_fkey
        foreign key (rag_consent_id) references private.consent_records (id) on delete set null;

alter table public.community_posts
    add constraint community_posts_verification_contribution_id_fkey
        foreign key (verification_contribution_id) references public.contributions (id) on delete set null,
    add constraint community_posts_publication_consent_id_fkey
        foreign key (publication_consent_id) references private.consent_records (id) on delete restrict,
    add constraint community_posts_rag_consent_id_fkey
        foreign key (rag_consent_id) references private.consent_records (id) on delete set null;

alter table public.learning_modules
    add constraint learning_modules_cover_asset_id_fkey
    foreign key (cover_asset_id) references private.stored_assets (id) on delete set null;
