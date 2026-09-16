-- Controlled moderator access, asynchronous index outbox, and append-only audit.
-- product_worker is an operational role; an operator may provision LOGIN and its
-- credential outside this repository when a worker is deployed.

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'product_worker') then
        create role product_worker nologin noinherit nobypassrls;
    end if;
end;
$$;

grant usage on schema private to product_worker;

create function private.has_role(required_role text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_roles r
        where r.user_id = (select auth.uid())
          and r.role = required_role
    );
$$;
revoke all on function private.has_role(text) from public, anon, authenticated;
grant execute on function private.has_role(text) to product_app;

create table public.moderation_decisions (
    id uuid primary key default gen_random_uuid(),
    contribution_id uuid not null references public.contributions (id) on delete restrict,
    moderator_id uuid null references public.profiles (id) on delete set null,
    expected_revision bigint not null check (expected_revision > 0),
    previous_status text not null check (previous_status in (
        'DRAFT', 'SUBMITTED', 'NEEDS_EVIDENCE', 'VERIFIED', 'REJECTED', 'RETRACTED'
    )),
    action text not null check (action in ('VERIFY', 'REJECT', 'NEEDS_EVIDENCE', 'RETRACT')),
    new_status text not null check (new_status in (
        'DRAFT', 'SUBMITTED', 'NEEDS_EVIDENCE', 'VERIFIED', 'REJECTED', 'RETRACTED'
    )),
    reason text not null check (char_length(btrim(reason)) between 1 and 5000),
    evidence_ids jsonb not null default '[]'::jsonb check (jsonb_typeof(evidence_ids) = 'array'),
    sanitized_snapshot jsonb not null check (jsonb_typeof(sanitized_snapshot) = 'object'),
    publish_to_connection boolean not null default false,
    allow_rag boolean not null default false,
    created_at timestamptz not null default now()
);
create index moderation_decisions_contribution_created_idx
    on public.moderation_decisions (contribution_id, created_at desc);

create table private.outbox_events (
    id uuid primary key default gen_random_uuid(),
    aggregate_type text not null check (char_length(btrim(aggregate_type)) between 1 and 100),
    aggregate_id uuid not null,
    aggregate_revision bigint not null check (aggregate_revision > 0),
    event_type text not null check (event_type in ('COMMUNITY_UPSERT', 'COMMUNITY_DELETE')),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
    state text not null default 'PENDING'
        check (state in ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'BLOCKED')),
    attempts integer not null default 0 check (attempts >= 0),
    available_at timestamptz not null default now(),
    locked_by text null check (char_length(locked_by) <= 255),
    lease_until timestamptz null,
    processed_at timestamptz null,
    last_error_code text null check (char_length(last_error_code) <= 100),
    created_at timestamptz not null default now(),
    unique (aggregate_type, aggregate_id, aggregate_revision, event_type)
);
create index outbox_events_available_idx on private.outbox_events (state, available_at, id);
create index outbox_events_aggregate_idx on private.outbox_events (aggregate_id, aggregate_revision);

create table private.audit_logs (
    id bigint generated always as identity primary key,
    actor_id uuid null references public.profiles (id) on delete set null,
    action text not null check (char_length(btrim(action)) between 1 and 100),
    resource_type text not null check (char_length(btrim(resource_type)) between 1 and 100),
    resource_id text null check (char_length(resource_id) <= 255),
    request_id uuid null,
    trace_id text null check (char_length(trace_id) <= 255),
    safe_metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(safe_metadata) = 'object'),
    created_at timestamptz not null default now()
);
create index audit_logs_resource_idx on private.audit_logs (resource_type, resource_id, created_at);
create index audit_logs_created_idx on private.audit_logs (created_at);

alter table public.moderation_decisions enable row level security;
alter table public.moderation_decisions force row level security;
alter table private.outbox_events enable row level security;
alter table private.outbox_events force row level security;
alter table private.audit_logs enable row level security;
alter table private.audit_logs force row level security;

revoke all on public.moderation_decisions from public, anon, authenticated;
revoke all on private.outbox_events, private.audit_logs from public, anon, authenticated;
grant select, insert on public.moderation_decisions to product_app;
grant select, insert on private.outbox_events to product_app;
grant insert on private.audit_logs to product_app;
grant select, update on private.outbox_events to product_worker;
grant insert on private.audit_logs to product_worker;

create policy contributions_moderator_read on public.contributions
for select to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy contributions_moderator_update on public.contributions
for update to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy contribution_sources_moderator_read on public.contribution_sources
for select to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);

create policy moderation_decisions_moderator_read on public.moderation_decisions
for select to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy moderation_decisions_moderator_insert on public.moderation_decisions
for insert to product_app with check (
    moderator_id = (select auth.uid())
    and ((select private.has_role('MODERATOR')) or (select private.has_role('ADMIN')))
);

create policy outbox_events_moderator_insert on private.outbox_events
for insert to product_app with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy outbox_events_worker_access on private.outbox_events
for all to product_worker using (true) with check (true);

create policy audit_logs_product_app_insert on private.audit_logs
for insert to product_app with check (actor_id is null or actor_id = (select auth.uid()));
create policy audit_logs_worker_insert on private.audit_logs
for insert to product_worker with check (true);
