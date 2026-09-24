-- Scalable learning content: topics, case examples and media references.
create table public.learning_topics (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique check (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    title text not null check (char_length(btrim(title)) between 1 and 160),
    description text not null check (char_length(btrim(description)) between 1 and 5000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.learning_modules
    add column if not exists topic_id uuid references public.learning_topics(id) on delete set null;

create table public.learning_cases (
    id uuid primary key default gen_random_uuid(),
    module_id uuid not null references public.learning_modules(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 200),
    description text not null check (char_length(btrim(description)) between 1 and 10000),
    reference_url text,
    display_order smallint not null default 0 check (display_order >= 0),
    created_at timestamptz not null default now(),
    unique (module_id, display_order)
);

create table public.learning_media (
    id uuid primary key default gen_random_uuid(),
    module_id uuid not null references public.learning_modules(id) on delete cascade,
    lesson_id uuid null references public.learning_lessons(id) on delete cascade,
    media_type text not null check (media_type in ('IMAGE', 'YOUTUBE')),
    url text not null check (char_length(btrim(url)) between 1 and 2000),
    title text not null check (char_length(btrim(title)) between 1 and 200),
    alt_text text not null default '',
    display_order smallint not null default 0 check (display_order >= 0),
    created_at timestamptz not null default now()
);
create index learning_media_module_order_idx on public.learning_media(module_id, display_order, id);
create index learning_cases_module_order_idx on public.learning_cases(module_id, display_order, id);

create table public.learning_module_progress (
    user_id uuid not null references public.profiles(id) on delete cascade,
    module_id uuid not null references public.learning_modules(id) on delete cascade,
    first_opened_at timestamptz not null default now(),
    last_opened_at timestamptz not null default now(),
    primary key (user_id, module_id)
);

alter table public.learning_topics enable row level security;
alter table public.learning_topics force row level security;
alter table public.learning_cases enable row level security;
alter table public.learning_cases force row level security;
alter table public.learning_media enable row level security;
alter table public.learning_media force row level security;

revoke all on public.learning_topics, public.learning_cases, public.learning_media, public.learning_module_progress from public, anon, authenticated;
grant select, insert, update, delete on public.learning_topics, public.learning_cases, public.learning_media to product_app;
grant select, insert, update on public.learning_module_progress to product_app;

create policy learning_topics_read on public.learning_topics for select to product_app using (true);
create policy learning_topics_moderator_write on public.learning_topics for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_cases_read on public.learning_cases for select to product_app using (
    exists (select 1 from public.learning_modules m where m.id = module_id and m.status = 'PUBLISHED')
    or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_cases_moderator_write on public.learning_cases for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_media_read on public.learning_media for select to product_app using (
    exists (select 1 from public.learning_modules m where m.id = module_id and m.status = 'PUBLISHED')
    or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_media_moderator_write on public.learning_media for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_module_progress_owner_read on public.learning_module_progress for select to product_app using (user_id = (select auth.uid()));
create policy learning_module_progress_owner_write on public.learning_module_progress for all to product_app using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop view public.published_learning_modules;
create view public.published_learning_modules
with (security_invoker = true) as
select id, slug, title, summary, cover_asset_id, difficulty, display_order, version, topic_id
from public.learning_modules where status = 'PUBLISHED';
grant select on public.published_learning_modules to product_app;

create or replace function public.set_learning_topic_updated_at()
returns trigger language plpgsql security definer set search_path = public
as $$ begin new.updated_at = now(); return new; end; $$;
create trigger learning_topics_set_updated_at before update on public.learning_topics
for each row execute function public.set_learning_topic_updated_at();
