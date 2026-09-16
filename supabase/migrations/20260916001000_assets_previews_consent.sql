-- Asset metadata and consent records. Storage objects themselves are configured
-- in the final storage migration, after all Product references exist.

create table private.stored_assets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    case_id uuid null references public.verification_cases (id) on delete cascade,
    bucket text not null check (char_length(btrim(bucket)) between 1 and 100),
    object_path text not null check (char_length(btrim(object_path)) between 1 and 1024),
    purpose text not null check (purpose in (
        'SCREENSHOT_OPT_IN', 'REDACTED_PREVIEW', 'CONTRIBUTION_EVIDENCE', 'AVATAR', 'LEARNING'
    )),
    mime_type text not null check (char_length(btrim(mime_type)) between 1 and 255),
    size_bytes bigint not null check (size_bytes >= 0),
    sha256 text not null check (sha256 ~ '^[a-f0-9]{64}$'),
    consent_id uuid null,
    expires_at timestamptz null,
    deleted_at timestamptz null,
    created_at timestamptz not null default now(),
    unique (bucket, object_path)
);
create index stored_assets_expiry_idx on private.stored_assets (expires_at)
    where deleted_at is null;
create index stored_assets_case_idx on private.stored_assets (case_id);

create table public.community_previews (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.verification_cases (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    case_revision bigint not null check (case_revision > 0),
    redacted_text text not null check (char_length(btrim(redacted_text)) between 1 and 25000),
    redacted_asset_id uuid null references private.stored_assets (id) on delete set null,
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    redaction_version text not null check (char_length(btrim(redaction_version)) between 1 and 100),
    redactions jsonb not null default '[]'::jsonb check (jsonb_typeof(redactions) = 'array'),
    state text not null check (state in ('READY', 'CONSUMED', 'EXPIRED', 'INVALIDATED')),
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    consumed_at timestamptz null
);
create index community_previews_case_created_idx on public.community_previews (case_id, created_at desc);
create index community_previews_expiry_idx on public.community_previews (expires_at);

create table private.consent_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    scope text not null check (scope in ('TEMP_SCREENSHOT_STORAGE', 'COMMUNITY_PUBLICATION', 'RAG_REUSE')),
    case_id uuid null references public.verification_cases (id) on delete set null,
    contribution_id uuid null,
    preview_id uuid null,
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    policy_version text not null check (char_length(btrim(policy_version)) between 1 and 100),
    granted_at timestamptz not null default now(),
    revoked_at timestamptz null,
    expires_at timestamptz null,
    check (num_nonnulls(case_id, contribution_id, preview_id) >= 1)
);
create index consent_records_user_scope_idx on private.consent_records (user_id, scope, granted_at desc);

alter table private.stored_assets enable row level security;
alter table private.stored_assets force row level security;
alter table public.community_previews enable row level security;
alter table public.community_previews force row level security;
alter table private.consent_records enable row level security;
alter table private.consent_records force row level security;

revoke all on private.stored_assets, private.consent_records from public, anon, authenticated;
revoke all on public.community_previews from public, anon, authenticated;
grant select, insert, update on private.stored_assets, private.consent_records to product_app;
grant select, insert, update on public.community_previews to product_app;

create policy stored_assets_owner_access on private.stored_assets
for all to product_app using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy community_previews_owner_access on public.community_previews
for all to product_app using (user_id = (select auth.uid()))
with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.verification_cases c
                where c.id = case_id and c.user_id = (select auth.uid()))
);

create policy consent_records_owner_access on private.consent_records
for all to product_app using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
