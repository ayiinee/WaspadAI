-- Persistent social interactions for published community posts.
create table public.community_likes (
    post_id uuid not null references public.community_posts(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
create index community_likes_post_idx on public.community_likes(post_id);

create table public.community_views (
    post_id uuid not null references public.community_posts(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
create index community_views_post_idx on public.community_views(post_id);

create table public.community_shares (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.community_posts(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now()
);
create index community_shares_post_idx on public.community_shares(post_id, created_at desc);

alter table public.community_likes enable row level security;
alter table public.community_likes force row level security;
alter table public.community_views enable row level security;
alter table public.community_views force row level security;
alter table public.community_shares enable row level security;
alter table public.community_shares force row level security;

revoke all on public.community_likes, public.community_views, public.community_shares from public, anon, authenticated;
grant select, insert, delete on public.community_likes to product_app;
grant select, insert, update on public.community_views to product_app;
grant select, insert on public.community_shares to product_app;

create policy community_likes_public_read on public.community_likes for select to product_app using (
    exists (select 1 from public.community_posts p where p.id = post_id and p.withdrawn_at is null
      and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE') and p.publication_consent_id is not null)
);
create policy community_likes_owner_write on public.community_likes for insert to product_app with check (
    user_id = (select auth.uid()) and exists (select 1 from public.community_posts p where p.id = post_id
      and p.owner_id <> (select auth.uid()) and p.withdrawn_at is null
      and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE') and p.publication_consent_id is not null)
);
create policy community_likes_owner_delete on public.community_likes for delete to product_app using (user_id = (select auth.uid()));

create policy community_views_public_read on public.community_views for select to product_app using (
    exists (select 1 from public.community_posts p where p.id = post_id and p.withdrawn_at is null
      and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE') and p.publication_consent_id is not null)
);
create policy community_views_owner_write on public.community_views for insert to product_app with check (
    user_id = (select auth.uid()) and exists (select 1 from public.community_posts p where p.id = post_id
      and p.withdrawn_at is null and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
      and p.publication_consent_id is not null)
);
create policy community_views_owner_update on public.community_views for update to product_app using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create policy community_shares_public_read on public.community_shares for select to product_app using (
    exists (select 1 from public.community_posts p where p.id = post_id and p.withdrawn_at is null
      and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE') and p.publication_consent_id is not null)
);
create policy community_shares_owner_insert on public.community_shares for insert to product_app with check (
    user_id = (select auth.uid()) and exists (select 1 from public.community_posts p where p.id = post_id
      and p.withdrawn_at is null and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
      and p.publication_consent_id is not null)
);
