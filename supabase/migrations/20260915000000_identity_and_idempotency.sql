-- Product-owned identity and idempotency foundation. No application credential is stored here.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- The operator enables LOGIN and sets a password outside this migration.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'product_app') then
        create role product_app nologin noinherit nobypassrls;
    end if;
end;
$$;

grant usage on schema public, private to product_app;
grant usage on schema auth to product_app;
grant execute on function auth.uid() to product_app;

create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text not null check (char_length(btrim(display_name)) between 1 and 80),
    avatar_asset_id uuid null,
    bio text null check (char_length(bio) <= 500),
    locale text not null default 'id-ID' check (locale in ('id-ID', 'en-US')),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.user_roles (
    user_id uuid not null references public.profiles (id) on delete cascade,
    role text not null check (role in ('USER', 'MODERATOR', 'ADMIN')),
    granted_by uuid null references public.profiles (id) on delete set null,
    created_at timestamptz not null default now(),
    primary key (user_id, role)
);

create table private.request_operations (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    route_key text not null check (char_length(route_key) between 1 and 255),
    idempotency_key uuid not null,
    payload_hash text not null check (payload_hash ~ '^[a-f0-9]{64}$'),
    state text not null check (state in ('PROCESSING', 'COMPLETED', 'FAILED', 'UNKNOWN_OUTCOME')),
    upstream_started_at timestamptz null,
    lease_until timestamptz null,
    response_json jsonb null,
    http_status smallint null check (http_status between 100 and 599),
    ai_result_cache jsonb null,
    persistence_state text not null check (persistence_state in ('NOT_REQUIRED', 'PENDING', 'SAVED', 'FAILED')),
    error_code text null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, route_key, idempotency_key)
);

create index request_operations_state_lease_idx
on private.request_operations (state, lease_until);
create index request_operations_expiry_idx
on private.request_operations (expires_at);

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();
create trigger request_operations_set_updated_at before update on private.request_operations
for each row execute function public.set_updated_at();

create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    candidate_name text;
begin
    candidate_name := left(btrim(coalesce(new.raw_user_meta_data ->> 'full_name', '')), 80);
    insert into public.profiles (id, display_name)
    values (new.id, coalesce(nullif(candidate_name, ''), 'Pengguna WaspadAI'));
    insert into public.user_roles (user_id, role) values (new.id, 'USER');
    return new;
end;
$$;

create trigger auth_user_created_profile after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table private.request_operations enable row level security;
alter table private.request_operations force row level security;

-- Signed-in clients can inspect their identity, but cannot create roles or operations.
create policy profiles_select_self_client on public.profiles
for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_self_client on public.profiles
for update to authenticated using (id = (select auth.uid()))
with check (id = (select auth.uid()));
create policy roles_select_self_client on public.user_roles
for select to authenticated using (user_id = (select auth.uid()));

-- Backend role reads through transaction-local claims set from a validated token.
create policy profiles_select_self_backend on public.profiles
for select to product_app using (id = (select auth.uid()));
create policy profiles_update_self_backend on public.profiles
for update to product_app using (id = (select auth.uid()))
with check (id = (select auth.uid()));
create policy roles_owner_backend on public.user_roles
for select to product_app using (user_id = (select auth.uid()));
create policy operations_owner_backend on private.request_operations
for all to product_app using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- Supabase projects may have broad default privileges on exposed public tables.
revoke all on public.profiles, public.user_roles from public, anon, authenticated;
revoke all on private.request_operations from public, anon, authenticated;
grant select on public.profiles, public.user_roles to authenticated;
grant update (display_name, bio, locale) on public.profiles to authenticated;
grant select on public.profiles to product_app;
grant update (display_name, bio, locale) on public.profiles to product_app;
grant select on public.user_roles to product_app;
grant select, insert, update, delete on private.request_operations to product_app;
