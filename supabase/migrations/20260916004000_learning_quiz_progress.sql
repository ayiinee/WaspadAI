-- Curated learning content and server-scored quiz persistence. Raw option rows
-- are never granted to direct clients because they contain the answer key.

create table public.learning_modules (
    id uuid primary key default gen_random_uuid(),
    slug text not null check (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    title text not null check (char_length(btrim(title)) between 1 and 200),
    summary text not null check (char_length(btrim(summary)) between 1 and 2000),
    cover_asset_id uuid null,
    difficulty smallint not null check (difficulty between 1 and 5),
    display_order smallint not null check (display_order >= 0),
    version integer not null default 1 check (version > 0),
    status text not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (slug, version)
);
create index learning_modules_status_order_idx on public.learning_modules (status, display_order, id);

create table public.learning_lessons (
    id uuid primary key default gen_random_uuid(),
    module_id uuid not null references public.learning_modules (id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 200),
    body_md text not null check (char_length(btrim(body_md)) between 1 and 50000),
    duration_minutes smallint not null check (duration_minutes > 0),
    display_order smallint not null check (display_order >= 0),
    is_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (module_id, display_order)
);

create table public.quiz_questions (
    id uuid primary key default gen_random_uuid(),
    module_id uuid not null references public.learning_modules (id) on delete cascade,
    lesson_id uuid null references public.learning_lessons (id) on delete set null,
    question_text text not null check (char_length(btrim(question_text)) between 1 and 5000),
    explanation text not null check (char_length(btrim(explanation)) between 1 and 10000),
    version integer not null default 1 check (version > 0),
    display_order smallint not null check (display_order >= 0),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (module_id, display_order, version)
);

create table public.quiz_options (
    id uuid primary key default gen_random_uuid(),
    question_id uuid not null references public.quiz_questions (id) on delete cascade,
    option_text text not null check (char_length(btrim(option_text)) between 1 and 2000),
    display_order smallint not null check (display_order >= 0),
    is_correct boolean not null,
    created_at timestamptz not null default now(),
    unique (question_id, display_order)
);

create table public.lesson_progress (
    user_id uuid not null references public.profiles (id) on delete cascade,
    lesson_id uuid not null references public.learning_lessons (id) on delete cascade,
    completed_at timestamptz not null default now(),
    primary key (user_id, lesson_id)
);

create table public.quiz_attempts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    module_id uuid not null references public.learning_modules (id) on delete restrict,
    module_version integer not null check (module_version > 0),
    submission_key uuid not null,
    total_questions integer not null check (total_questions > 0),
    correct_answers integer not null check (correct_answers between 0 and total_questions),
    score numeric(5,2) not null check (score between 0 and 100),
    question_snapshot jsonb not null check (jsonb_typeof(question_snapshot) = 'array'),
    completed_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (user_id, module_id, submission_key)
);
create index quiz_attempts_owner_module_completed_idx
    on public.quiz_attempts (user_id, module_id, completed_at desc, id desc);

create table public.quiz_answers (
    attempt_id uuid not null references public.quiz_attempts (id) on delete cascade,
    question_id uuid not null references public.quiz_questions (id) on delete restrict,
    selected_option_id uuid not null references public.quiz_options (id) on delete restrict,
    is_correct boolean not null,
    answered_at timestamptz not null default now(),
    primary key (attempt_id, question_id)
);

create function private.assert_published_module_complete()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.status = 'PUBLISHED' and (tg_op = 'INSERT' or old.status is distinct from 'PUBLISHED') then
        if not exists (select 1 from public.learning_lessons l where l.module_id = new.id) then
            raise exception 'published module requires at least one lesson';
        end if;
        if exists (
            select 1
            from public.quiz_questions q
            left join public.quiz_options o on o.question_id = q.id
            where q.module_id = new.id and q.is_active
            group by q.id
            having count(o.id) < 2 or count(*) filter (where o.is_correct) <> 1
        ) then
            raise exception 'each active question in a published module requires two options and exactly one correct answer';
        end if;
    end if;
    return new;
end;
$$;

create function private.assert_quiz_answer_matches_attempt()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    attempt_module_id uuid;
    attempt_module_version integer;
    question_module_id uuid;
    question_version integer;
    option_question_id uuid;
begin
    select module_id, module_version into attempt_module_id, attempt_module_version
    from public.quiz_attempts where id = new.attempt_id;
    select module_id, version into question_module_id, question_version
    from public.quiz_questions where id = new.question_id;
    select question_id into option_question_id from public.quiz_options where id = new.selected_option_id;

    if attempt_module_id is null or question_module_id is null or option_question_id is null
       or attempt_module_id <> question_module_id
       or attempt_module_version <> question_version
       or option_question_id <> new.question_id then
        raise exception 'quiz answer does not belong to its attempt module and question';
    end if;
    return new;
end;
$$;

create trigger learning_modules_assert_publish before insert or update of status on public.learning_modules
for each row execute function private.assert_published_module_complete();
create trigger quiz_answers_assert_membership before insert or update on public.quiz_answers
for each row execute function private.assert_quiz_answer_matches_attempt();
create trigger learning_modules_set_updated_at before update on public.learning_modules
for each row execute function public.set_updated_at();
create trigger learning_lessons_set_updated_at before update on public.learning_lessons
for each row execute function public.set_updated_at();
create trigger quiz_questions_set_updated_at before update on public.quiz_questions
for each row execute function public.set_updated_at();

alter table public.learning_modules enable row level security;
alter table public.learning_modules force row level security;
alter table public.learning_lessons enable row level security;
alter table public.learning_lessons force row level security;
alter table public.quiz_questions enable row level security;
alter table public.quiz_questions force row level security;
alter table public.quiz_options enable row level security;
alter table public.quiz_options force row level security;
alter table public.lesson_progress enable row level security;
alter table public.lesson_progress force row level security;
alter table public.quiz_attempts enable row level security;
alter table public.quiz_attempts force row level security;
alter table public.quiz_answers enable row level security;
alter table public.quiz_answers force row level security;

revoke all on public.learning_modules, public.learning_lessons, public.quiz_questions,
    public.quiz_options, public.lesson_progress, public.quiz_attempts, public.quiz_answers
    from public, anon, authenticated;
grant select, insert, update, delete on public.learning_modules, public.learning_lessons,
    public.quiz_questions, public.quiz_options to product_app;
grant select, insert on public.lesson_progress, public.quiz_attempts, public.quiz_answers to product_app;

create policy learning_modules_read on public.learning_modules
for select to product_app using (
    status = 'PUBLISHED' or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_modules_moderator_write on public.learning_modules
for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_lessons_read on public.learning_lessons
for select to product_app using (
    is_published and exists (select 1 from public.learning_modules m where m.id = module_id and m.status = 'PUBLISHED')
    or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy learning_lessons_moderator_write on public.learning_lessons
for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy quiz_questions_read on public.quiz_questions
for select to product_app using (
    is_active and exists (select 1 from public.learning_modules m where m.id = module_id and m.status = 'PUBLISHED')
    or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy quiz_questions_moderator_write on public.quiz_questions
for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy quiz_options_read on public.quiz_options
for select to product_app using (
    exists (select 1 from public.quiz_questions q
            join public.learning_modules m on m.id = q.module_id
            where q.id = question_id and q.is_active and m.status = 'PUBLISHED')
    or (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy quiz_options_moderator_write on public.quiz_options
for all to product_app using (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
) with check (
    (select private.has_role('MODERATOR')) or (select private.has_role('ADMIN'))
);
create policy lesson_progress_owner_read on public.lesson_progress
for select to product_app using (user_id = (select auth.uid()));
create policy lesson_progress_owner_insert on public.lesson_progress
for insert to product_app with check (user_id = (select auth.uid()));
create policy quiz_attempts_owner_read on public.quiz_attempts
for select to product_app using (user_id = (select auth.uid()));
create policy quiz_attempts_owner_insert on public.quiz_attempts
for insert to product_app with check (user_id = (select auth.uid()));
create policy quiz_answers_owner_read on public.quiz_answers
for select to product_app using (
    exists (select 1 from public.quiz_attempts a where a.id = attempt_id and a.user_id = (select auth.uid()))
);
create policy quiz_answers_owner_insert on public.quiz_answers
for insert to product_app with check (
    exists (select 1 from public.quiz_attempts a where a.id = attempt_id and a.user_id = (select auth.uid()))
);

create view public.published_learning_modules
with (security_invoker = true) as
select id, slug, title, summary, cover_asset_id, difficulty, display_order, version
from public.learning_modules where status = 'PUBLISHED';
create view public.published_learning_lessons
with (security_invoker = true) as
select l.id, l.module_id, l.title, l.body_md, l.duration_minutes, l.display_order
from public.learning_lessons l
join public.learning_modules m on m.id = l.module_id
where l.is_published and m.status = 'PUBLISHED';
create view public.published_quiz_questions
with (security_invoker = true) as
select q.id, q.module_id, q.lesson_id, q.question_text, q.explanation, q.version, q.display_order
from public.quiz_questions q
join public.learning_modules m on m.id = q.module_id
where q.is_active and m.status = 'PUBLISHED';
create view public.published_quiz_options
with (security_invoker = true) as
select o.id, o.question_id, o.option_text, o.display_order
from public.quiz_options o
join public.quiz_questions q on q.id = o.question_id
join public.learning_modules m on m.id = q.module_id
where q.is_active and m.status = 'PUBLISHED';
revoke all on public.published_learning_modules, public.published_learning_lessons,
    public.published_quiz_questions, public.published_quiz_options from public, anon, authenticated;
grant select on public.published_learning_modules, public.published_learning_lessons,
    public.published_quiz_questions, public.published_quiz_options to product_app;
