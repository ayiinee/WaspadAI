-- Persistent multi-turn verification rooms. Existing cases are backfilled as
-- one room per case so no verification history disappears after this change.

create table public.verification_conversations (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 1000),
    latest_message_preview text not null
        check (char_length(btrim(latest_message_preview)) between 1 and 2000),
    latest_message_role text not null default 'ASSISTANT'
        check (latest_message_role in ('USER', 'ASSISTANT')),
    last_verdict text not null check (char_length(btrim(last_verdict)) between 1 and 100),
    next_turn_index integer not null default 1 check (next_turn_index > 0),
    retention_expires_at timestamptz not null,
    deleted_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index verification_conversations_owner_updated_idx
    on public.verification_conversations (user_id, updated_at desc, id desc)
    where deleted_at is null;
create index verification_conversations_retention_idx
    on public.verification_conversations (retention_expires_at);

alter table public.verification_cases
    add column conversation_id uuid null,
    add column turn_index integer null;

insert into public.verification_conversations
    (id, user_id, title, latest_message_preview, latest_message_role,
     last_verdict, next_turn_index, retention_expires_at, deleted_at,
     created_at, updated_at)
select c.id,
       c.user_id,
       c.headline,
       coalesce(
           nullif(btrim(r.result_json #>> '{presentation,narrative,text}'), ''),
           c.headline
       ),
       'ASSISTANT',
       c.verdict,
       2,
       c.retention_expires_at,
       c.deleted_at,
       c.created_at,
       c.updated_at
  from public.verification_cases c
  join public.verification_results r on r.case_id = c.id
on conflict (id) do nothing;

update public.verification_cases
   set conversation_id = id,
       turn_index = 1
 where conversation_id is null;

alter table public.verification_cases
    alter column conversation_id set not null,
    alter column turn_index set not null,
    add constraint verification_cases_conversation_fk
        foreign key (conversation_id)
        references public.verification_conversations (id)
        on delete cascade,
    add constraint verification_cases_turn_index_positive check (turn_index > 0),
    add constraint verification_cases_conversation_turn_unique
        unique (conversation_id, turn_index);

create index verification_cases_conversation_idx
    on public.verification_cases (conversation_id, turn_index);

create trigger verification_conversations_set_updated_at
before update on public.verification_conversations
for each row execute function public.set_updated_at();

alter table public.verification_conversations enable row level security;
alter table public.verification_conversations force row level security;

revoke all on public.verification_conversations from public, anon, authenticated;
grant select, insert, update on public.verification_conversations to product_app;

create policy verification_conversations_owner_read
on public.verification_conversations
for select to product_app
using (user_id = (select auth.uid()) and deleted_at is null);

create policy verification_conversations_owner_insert
on public.verification_conversations
for insert to product_app
with check (user_id = (select auth.uid()));

create policy verification_conversations_owner_update
on public.verification_conversations
for update to product_app
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
