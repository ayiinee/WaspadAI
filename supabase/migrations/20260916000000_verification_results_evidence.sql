-- Product verification history.  This is forward-only: the identity and
-- idempotency foundation has already been applied in the preceding migration.

create table public.verification_cases (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    operation_id uuid null references private.request_operations (id) on delete set null,
    product_request_id uuid not null unique,
    ai_request_id text null,
    ai_trace_id text null,
    input_type text not null check (input_type in ('TEXT', 'IMAGE')),
    input_source text not null check (input_source in ('MANUAL', 'OVERLAY', 'SHARE_INTENT', 'WEB')),
    sanitized_text text null check (char_length(sanitized_text) <= 25000),
    input_hash text not null check (input_hash ~ '^[a-f0-9]{64}$'),
    headline text not null check (char_length(btrim(headline)) between 1 and 1000),
    verdict text not null check (char_length(btrim(verdict)) between 1 and 100),
    risk_level text not null check (char_length(btrim(risk_level)) between 1 and 100),
    requires_human_review boolean not null,
    save_reason text not null check (save_reason in ('UNVERIFIED', 'HUMAN_REVIEW', 'ALL_POLICY')),
    community_state text not null default 'PRIVATE'
        check (community_state in ('PRIVATE', 'PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE', 'WITHDRAWN')),
    revision bigint not null default 1 check (revision > 0),
    retention_expires_at timestamptz not null,
    deleted_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index verification_cases_owner_created_idx
    on public.verification_cases (user_id, created_at desc, id desc)
    where deleted_at is null;
create index verification_cases_retention_idx on public.verification_cases (retention_expires_at);
create index verification_cases_community_idx on public.verification_cases (community_state, id);

create table public.verification_results (
    case_id uuid primary key references public.verification_cases (id) on delete cascade,
    factual_status text not null,
    source_authenticity text not null,
    sender_identity text not null,
    channel_status text not null,
    scam_risk text not null,
    content_authenticity text not null,
    evidence_sufficiency numeric(5,4) null check (evidence_sufficiency between 0 and 1),
    uncertainty text null check (char_length(uncertainty) <= 5000),
    result_json jsonb not null check (jsonb_typeof(result_json) = 'object'),
    upstream_contract_version text null,
    execution_mode text not null check (execution_mode in ('REMOTE', 'MOCK')),
    model_version text null,
    pipeline_version text null,
    rulebook_version text null,
    prompt_version text null,
    created_at timestamptz not null default now()
);

create table public.verification_evidence (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.verification_cases (id) on delete cascade,
    upstream_evidence_id text not null check (char_length(btrim(upstream_evidence_id)) between 1 and 255),
    display_order smallint not null check (display_order >= 0),
    source_type text null,
    publisher text null check (char_length(publisher) <= 300),
    title text null check (char_length(title) <= 1000),
    source_url text null check (char_length(source_url) <= 2048 and source_url ~* '^https?://'),
    source_domain text null check (char_length(source_domain) <= 255),
    excerpt text null check (char_length(excerpt) <= 10000),
    stance text null,
    verification_status text null,
    published_at timestamptz null,
    retrieved_at timestamptz null,
    relevance numeric(5,4) null check (relevance between 0 and 1),
    authority numeric(5,4) null check (authority between 0 and 1),
    recency numeric(5,4) null check (recency between 0 and 1),
    provenance jsonb not null default '{}'::jsonb check (jsonb_typeof(provenance) = 'object'),
    evidence_json jsonb not null check (jsonb_typeof(evidence_json) = 'object'),
    created_at timestamptz not null default now(),
    unique (case_id, upstream_evidence_id)
);
create index verification_evidence_case_idx on public.verification_evidence (case_id);

create table public.verification_rulebook_matches (
    id uuid primary key default gen_random_uuid(),
    case_id uuid not null references public.verification_cases (id) on delete cascade,
    rule_id text not null check (char_length(btrim(rule_id)) between 1 and 255),
    rule_version text null,
    rank smallint not null check (rank >= 0),
    match_type text null,
    phase text null,
    score numeric(5,4) null check (score between 0 and 1),
    score_components jsonb null check (score_components is null or jsonb_typeof(score_components) = 'object'),
    match_snapshot jsonb not null check (jsonb_typeof(match_snapshot) = 'object'),
    created_at timestamptz not null default now(),
    unique (case_id, rank)
);
create index verification_rulebook_matches_case_idx on public.verification_rulebook_matches (case_id);

create table private.verification_events (
    id bigint generated always as identity primary key,
    operation_id uuid null references private.request_operations (id) on delete set null,
    event_type text not null check (char_length(btrim(event_type)) between 1 and 100),
    stage text null check (char_length(stage) <= 100),
    duration_ms integer null check (duration_ms >= 0),
    safe_metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(safe_metadata) = 'object'),
    created_at timestamptz not null default now()
);

create trigger verification_cases_set_updated_at before update on public.verification_cases
for each row execute function public.set_updated_at();

alter table public.verification_cases enable row level security;
alter table public.verification_cases force row level security;
alter table public.verification_results enable row level security;
alter table public.verification_results force row level security;
alter table public.verification_evidence enable row level security;
alter table public.verification_evidence force row level security;
alter table public.verification_rulebook_matches enable row level security;
alter table public.verification_rulebook_matches force row level security;
alter table private.verification_events enable row level security;
alter table private.verification_events force row level security;

revoke all on public.verification_cases, public.verification_results,
    public.verification_evidence, public.verification_rulebook_matches from public, anon, authenticated;
revoke all on private.verification_events from public, anon, authenticated;
grant select, insert, update on public.verification_cases to product_app;
grant select, insert on public.verification_results, public.verification_evidence,
    public.verification_rulebook_matches to product_app;
grant select, insert on private.verification_events to product_app;

create policy verification_cases_owner_read on public.verification_cases
for select to product_app using (user_id = (select auth.uid()) and deleted_at is null);
create policy verification_cases_owner_insert on public.verification_cases
for insert to product_app with check (user_id = (select auth.uid()));
create policy verification_cases_owner_update on public.verification_cases
for update to product_app using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy verification_results_owner_read on public.verification_results
for select to product_app using (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()) and c.deleted_at is null)
);
create policy verification_results_owner_insert on public.verification_results
for insert to product_app with check (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()))
);

create policy verification_evidence_owner_read on public.verification_evidence
for select to product_app using (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()) and c.deleted_at is null)
);
create policy verification_evidence_owner_insert on public.verification_evidence
for insert to product_app with check (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()))
);

create policy verification_rulebook_matches_owner_read on public.verification_rulebook_matches
for select to product_app using (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()) and c.deleted_at is null)
);
create policy verification_rulebook_matches_owner_insert on public.verification_rulebook_matches
for insert to product_app with check (
    exists (select 1 from public.verification_cases c
            where c.id = case_id and c.user_id = (select auth.uid()))
);

create policy verification_events_operation_owner on private.verification_events
for select to product_app using (
    operation_id is not null and exists (
        select 1 from private.request_operations o
        where o.id = operation_id and o.user_id = (select auth.uid())
    )
);
create policy verification_events_operation_owner_insert on private.verification_events
for insert to product_app with check (
    operation_id is not null and exists (
        select 1 from private.request_operations o
        where o.id = operation_id and o.user_id = (select auth.uid())
    )
);
