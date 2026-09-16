-- Sanitized community content and user contributions. Direct client grants are
-- deliberately absent; Product API returns safe projections instead.

create table public.contributions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    case_id uuid null references public.verification_cases (id) on delete set null,
    title text not null check (char_length(btrim(title)) between 1 and 200),
    summary text not null check (char_length(btrim(summary)) between 1 and 5000),
    reasoning text not null check (char_length(btrim(reasoning)) between 1 and 5000),
    sanitized_content text null check (char_length(sanitized_content) <= 10000),
    status text not null default 'DRAFT'
        check (status in ('DRAFT', 'SUBMITTED', 'NEEDS_EVIDENCE', 'VERIFIED', 'REJECTED', 'RETRACTED')),
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    revision bigint not null default 1 check (revision > 0),
    publication_consent_id uuid null,
    rag_consent_id uuid null,
    claimed_by uuid null references public.profiles (id) on delete set null,
    claim_expires_at timestamptz null,
    submitted_at timestamptz null,
    verified_at timestamptz null,
    retracted_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index contributions_owner_created_idx on public.contributions (user_id, created_at desc);
create index contributions_status_submitted_idx on public.contributions (status, submitted_at, id);

create table public.community_posts (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null unique references public.verification_cases (id) on delete restrict,
    owner_id uuid not null references public.profiles (id) on delete restrict,
    preview_id uuid null references public.community_previews (id) on delete set null,
    title text not null check (char_length(btrim(title)) between 1 and 200),
    redacted_text text not null check (char_length(btrim(redacted_text)) between 1 and 25000),
    redacted_asset_id uuid null references private.stored_assets (id) on delete set null,
    status text not null check (status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE', 'WITHDRAWN')),
    verification_contribution_id uuid null,
    publication_consent_id uuid not null,
    rag_consent_id uuid null,
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    revision bigint not null default 1 check (revision > 0),
    published_at timestamptz not null default now(),
    withdrawn_at timestamptz null,
    verified_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index community_posts_feed_idx on public.community_posts (status, published_at desc, case_id desc)
    where withdrawn_at is null;

create table public.community_votes (
    post_id uuid not null references public.community_posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    vote text not null check (vote in ('DIDUKUNG', 'DIBANTAH')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
create index community_votes_post_vote_idx on public.community_votes (post_id, vote);

create table public.contribution_sources (
    id uuid primary key default gen_random_uuid(),
    contribution_id uuid not null references public.contributions (id) on delete cascade,
    source_url text not null check (char_length(source_url) <= 2048 and source_url ~* '^https?://'),
    title text null check (char_length(title) <= 300),
    publisher text null check (char_length(publisher) <= 200),
    note text null check (char_length(note) <= 2000),
    asset_id uuid null references private.stored_assets (id) on delete set null,
    source_hash text not null check (source_hash ~ '^[a-f0-9]{64}$'),
    created_at timestamptz not null default now(),
    unique (contribution_id, source_hash)
);
create index contribution_sources_parent_idx on public.contribution_sources (contribution_id);

create trigger contributions_set_updated_at before update on public.contributions
for each row execute function public.set_updated_at();
create trigger community_posts_set_updated_at before update on public.community_posts
for each row execute function public.set_updated_at();
create trigger community_votes_set_updated_at before update on public.community_votes
for each row execute function public.set_updated_at();

alter table public.contributions enable row level security;
alter table public.contributions force row level security;
alter table public.community_posts enable row level security;
alter table public.community_posts force row level security;
alter table public.community_votes enable row level security;
alter table public.community_votes force row level security;
alter table public.contribution_sources enable row level security;
alter table public.contribution_sources force row level security;

revoke all on public.contributions, public.community_posts, public.community_votes,
    public.contribution_sources from public, anon, authenticated;
grant select, insert, update on public.contributions, public.community_posts,
    public.community_votes, public.contribution_sources to product_app;
grant delete on public.community_votes to product_app;

create policy contributions_owner_read on public.contributions
for select to product_app using (user_id = (select auth.uid()));
create policy contributions_owner_insert on public.contributions
for insert to product_app with check (user_id = (select auth.uid()));
create policy contributions_owner_editable_update on public.contributions
for update to product_app using (
    user_id = (select auth.uid()) and status in ('DRAFT', 'NEEDS_EVIDENCE')
) with check (
    user_id = (select auth.uid()) and status in ('DRAFT', 'NEEDS_EVIDENCE', 'SUBMITTED')
);

create policy community_posts_visible_read on public.community_posts
for select to product_app using (
    owner_id = (select auth.uid())
    or (withdrawn_at is null and status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE'))
);
create policy community_posts_owner_write on public.community_posts
for insert to product_app with check (owner_id = (select auth.uid()));
create policy community_posts_owner_update on public.community_posts
for update to product_app using (owner_id = (select auth.uid()))
with check (owner_id = (select auth.uid()));

create policy community_votes_own_read on public.community_votes
for select to product_app using (user_id = (select auth.uid()));
create policy community_votes_non_owner_insert on public.community_votes
for insert to product_app with check (
    user_id = (select auth.uid())
    and exists (select 1 from public.community_posts p
                where p.id = post_id and p.owner_id <> (select auth.uid())
                  and p.withdrawn_at is null)
);
create policy community_votes_own_update on public.community_votes
for update to product_app using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
create policy community_votes_own_delete on public.community_votes
for delete to product_app using (user_id = (select auth.uid()));

create policy contribution_sources_owner_read on public.contribution_sources
for select to product_app using (
    exists (select 1 from public.contributions c
            where c.id = contribution_id and c.user_id = (select auth.uid()))
);
create policy contribution_sources_owner_write on public.contribution_sources
for insert to product_app with check (
    exists (select 1 from public.contributions c
            where c.id = contribution_id and c.user_id = (select auth.uid())
)
);
create policy contribution_sources_owner_update on public.contribution_sources
for update to product_app using (
    exists (select 1 from public.contributions c
            where c.id = contribution_id and c.user_id = (select auth.uid()))
) with check (
    exists (select 1 from public.contributions c
            where c.id = contribution_id and c.user_id = (select auth.uid()))
);
