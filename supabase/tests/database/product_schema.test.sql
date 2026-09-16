begin;

select plan(31);

select ok(to_regclass('public.verification_cases') is not null, 'verification cases table exists');
select ok(to_regclass('public.verification_results') is not null, 'verification results table exists');
select ok(to_regclass('public.verification_evidence') is not null, 'verification evidence table exists');
select ok(to_regclass('public.verification_rulebook_matches') is not null, 'rulebook matches table exists');
select ok(to_regclass('private.verification_events') is not null, 'verification events table exists');
select ok(to_regclass('private.stored_assets') is not null, 'stored assets table exists');
select ok(to_regclass('public.community_previews') is not null, 'community previews table exists');
select ok(to_regclass('private.consent_records') is not null, 'consent records table exists');
select ok(to_regclass('public.contributions') is not null, 'contributions table exists');
select ok(to_regclass('public.community_posts') is not null, 'community posts table exists');
select ok(to_regclass('public.community_votes') is not null, 'community votes table exists');
select ok(to_regclass('public.contribution_sources') is not null, 'contribution sources table exists');
select ok(to_regclass('public.moderation_decisions') is not null, 'moderation decisions table exists');
select ok(to_regclass('private.outbox_events') is not null, 'outbox table exists');
select ok(to_regclass('private.audit_logs') is not null, 'audit log table exists');
select ok(to_regclass('public.learning_modules') is not null, 'learning modules table exists');
select ok(to_regclass('public.learning_lessons') is not null, 'learning lessons table exists');
select ok(to_regclass('public.quiz_questions') is not null, 'quiz questions table exists');
select ok(to_regclass('public.quiz_options') is not null, 'quiz options table exists');
select ok(to_regclass('public.lesson_progress') is not null, 'lesson progress table exists');
select ok(to_regclass('public.quiz_attempts') is not null, 'quiz attempts table exists');
select ok(to_regclass('public.quiz_answers') is not null, 'quiz answers table exists');
select ok(to_regclass('public.published_quiz_options') is not null, 'safe quiz options view exists');
select ok(
    not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'published_quiz_options' and column_name = 'is_correct'
    ),
    'safe quiz options view does not expose answer keys'
);
select ok((select relrowsecurity from pg_class where oid = 'public.verification_cases'::regclass), 'cases use RLS');
select ok((select relrowsecurity from pg_class where oid = 'private.stored_assets'::regclass), 'assets use RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.community_posts'::regclass), 'posts use RLS');
select ok((select relrowsecurity from pg_class where oid = 'private.outbox_events'::regclass), 'outbox uses RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.quiz_attempts'::regclass), 'quiz attempts use RLS');
select ok(not has_table_privilege('authenticated', 'public.verification_cases', 'SELECT'), 'clients cannot read raw history');
select ok(not has_table_privilege('authenticated', 'public.quiz_options', 'SELECT'), 'clients cannot read answer keys');

select * from finish();
rollback;
