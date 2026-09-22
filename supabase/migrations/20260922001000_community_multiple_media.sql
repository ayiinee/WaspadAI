-- Canonical ordered media for community previews and published posts.
-- Legacy redacted_asset_id columns remain as the first-image compatibility field.

create table public.community_preview_media (
    id uuid primary key default gen_random_uuid(),
    preview_id uuid not null references public.community_previews(id) on delete cascade,
    asset_id uuid not null references private.stored_assets(id) on delete restrict,
    media_type text not null default 'IMAGE' check (media_type = 'IMAGE'),
    position smallint not null check (position between 0 and 3),
    width integer null check (width is null or width > 0),
    height integer null check (height is null or height > 0),
    created_at timestamptz not null default now(),
    unique (preview_id, position),
    unique (preview_id, asset_id)
);

create table public.community_media (
    id uuid primary key default gen_random_uuid(),
    community_id uuid not null references public.community_posts(id) on delete cascade,
    asset_id uuid not null references private.stored_assets(id) on delete restrict,
    media_type text not null default 'IMAGE' check (media_type = 'IMAGE'),
    thumbnail_asset_id uuid null references private.stored_assets(id) on delete set null,
    sort_order smallint not null check (sort_order between 0 and 3),
    width integer null check (width is null or width > 0),
    height integer null check (height is null or height > 0),
    created_at timestamptz not null default now(),
    unique (community_id, sort_order),
    unique (community_id, asset_id)
);

create index community_preview_media_preview_idx
    on public.community_preview_media(preview_id, position);
create index community_media_community_idx
    on public.community_media(community_id, sort_order);

-- Existing single-image posts become one-item media collections.
insert into public.community_media (community_id, asset_id, sort_order)
select p.id, p.redacted_asset_id, 0
  from public.community_posts p
 where p.redacted_asset_id is not null
on conflict do nothing;

-- Existing unconsumed previews retain their image when published later.
insert into public.community_preview_media (preview_id, asset_id, position)
select preview.id, preview.redacted_asset_id, 0
  from public.community_previews preview
 where preview.redacted_asset_id is not null
on conflict do nothing;

alter table public.community_preview_media enable row level security;
alter table public.community_preview_media force row level security;
alter table public.community_media enable row level security;
alter table public.community_media force row level security;

revoke all on public.community_preview_media, public.community_media from public, anon, authenticated;
grant select, insert on public.community_preview_media, public.community_media to product_app;

create policy community_preview_media_owner_access on public.community_preview_media
for all to product_app using (
    exists (
        select 1 from public.community_previews preview
         where preview.id = preview_id
           and preview.user_id = (select auth.uid())
    )
) with check (
    exists (
        select 1 from public.community_previews preview
         where preview.id = preview_id
           and preview.user_id = (select auth.uid())
    )
);

create policy community_media_published_read on public.community_media
for select to product_app using (
    exists (
        select 1 from public.community_posts post
         where post.id = community_id
           and post.withdrawn_at is null
           and post.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and post.publication_consent_id is not null
    )
);

create policy community_media_owner_insert on public.community_media
for insert to product_app with check (
    exists (
        select 1 from public.community_posts post
         where post.id = community_id
           and post.owner_id = (select auth.uid())
    )
);
