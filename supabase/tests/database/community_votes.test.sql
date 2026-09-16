begin;

select plan(9);

select ok(
    exists (
        select 1
          from pg_constraint
         where conrelid = 'public.community_votes'::regclass
           and conname = 'community_votes_vote_check'
           and pg_get_constraintdef(oid) like '%HOAKS%WASPADA%VALID%'
    ),
    'community votes use the classification enum'
);
select ok(
    exists (
        select 1 from pg_indexes
         where schemaname = 'public'
           and tablename = 'community_votes'
           and indexname = 'community_votes_post_updated_idx'
    ),
    'community votes have a post update index'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.community_votes'::regclass),
    'community votes use RLS'
);
select ok(
    (select relforcerowsecurity from pg_class where oid = 'public.community_votes'::regclass),
    'community votes force RLS'
);
select ok(
    exists (
        select 1 from pg_policies
         where schemaname = 'public'
           and tablename = 'community_votes'
           and policyname = 'community_votes_non_owner_insert'
    ),
    'community vote insert policy exists'
);
select ok(
    exists (
        select 1 from pg_policies
         where schemaname = 'public'
           and tablename = 'community_votes'
           and policyname = 'community_votes_public_aggregate_read'
    ),
    'backend can aggregate votes for public posts'
);
select ok(
    exists (
        select 1 from pg_policies
         where schemaname = 'private'
           and tablename = 'consent_records'
           and policyname = 'consent_records_publication_feed_read'
    ),
    'active publication consent feed policy exists'
);
select ok(
    not has_table_privilege('authenticated', 'public.community_votes', 'INSERT'),
    'clients cannot write votes directly'
);
select ok(
    has_table_privilege('product_app', 'public.community_votes', 'UPDATE'),
    'product api can update votes through RLS'
);

select * from finish();
rollback;
