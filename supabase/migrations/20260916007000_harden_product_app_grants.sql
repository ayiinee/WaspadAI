-- Reassert the least-privilege runtime contract after hosted provisioning.
-- Do not amend an applied migration: this forward migration removes grants that
-- may have been introduced by an operator or default privilege configuration.

grant usage on schema auth to product_app;
grant execute on function auth.uid() to product_app;

revoke all privileges on table public.profiles from product_app;
revoke update (id, display_name, avatar_asset_id, bio, locale, is_active, created_at, updated_at)
    on public.profiles from product_app;
grant select on table public.profiles to product_app;
grant update (display_name, bio, locale) on table public.profiles to product_app;

revoke all privileges on table public.user_roles from product_app;
grant select on table public.user_roles to product_app;
