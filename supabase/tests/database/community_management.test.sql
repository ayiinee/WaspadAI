begin;

select plan(4);

select ok(
    exists (
        select 1
          from pg_constraint
         where conrelid = 'public.community_likes'::regclass
           and contype = 'p'
           and pg_get_constraintdef(oid) like '%post_id, user_id%'
    ),
    'one active like per user and community post remains enforced'
);
select ok(
    position('owner_id <>' in coalesce((
        select with_check
          from pg_policies
         where schemaname = 'public'
           and tablename = 'community_likes'
           and policyname = 'community_likes_owner_write'
    ), '')) = 0,
    'like insert policy does not forbid the post owner'
);
select ok(
    to_regprocedure('private.community_display_name(uuid)') is not null,
    'restricted creator display-name function exists'
);
select ok(
    has_function_privilege('product_app', 'private.community_display_name(uuid)', 'EXECUTE')
    and not has_function_privilege('authenticated', 'private.community_display_name(uuid)', 'EXECUTE'),
    'only the product backend can resolve public creator display names'
);

select * from finish();
rollback;
