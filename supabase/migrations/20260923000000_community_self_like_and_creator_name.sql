-- Allow self-like while preserving one active like per (post_id, user_id).
drop policy if exists community_likes_owner_write on public.community_likes;
create policy community_likes_owner_write on public.community_likes
for insert to product_app with check (
    user_id = (select auth.uid())
    and exists (
        select 1
          from public.community_posts p
         where p.id = post_id
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
    )
);

-- Expose only the display name needed by the public feed. This avoids widening
-- the profiles RLS policy, which would also expose private profile columns.
create or replace function private.community_display_name(profile_id uuid)
returns text
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select coalesce(nullif(btrim(p.display_name), ''), 'Pengguna WaspadAI')
      from public.profiles p
     where p.id = profile_id
$$;

revoke all on function private.community_display_name(uuid) from public, anon, authenticated;
grant execute on function private.community_display_name(uuid) to product_app;
