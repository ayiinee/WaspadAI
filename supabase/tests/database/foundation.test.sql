begin;

select plan(13);

select ok(to_regnamespace('private') is not null, 'private schema exists');
select ok(to_regclass('public.profiles') is not null, 'profiles table exists');
select ok(to_regclass('public.user_roles') is not null, 'user roles table exists');
select ok(to_regclass('private.request_operations') is not null, 'private operations table exists');
select ok(
    (select relrowsecurity from pg_class where oid = 'private.request_operations'::regclass),
    'private operations enforce RLS'
);
select ok(
    (select relforcerowsecurity from pg_class where oid = 'private.request_operations'::regclass),
    'private operations force RLS for their owner'
);
select ok(
    (select not rolbypassrls and not rolsuper and not rolcreaterole
     from pg_roles where rolname = 'product_app'),
    'product_app cannot bypass RLS or administer roles after provisioning'
);
select ok(
    not has_schema_privilege('authenticated', 'private', 'USAGE'),
    'signed-in clients cannot access private schema'
);
select ok(
    not has_table_privilege('authenticated', 'private.request_operations', 'SELECT'),
    'signed-in clients cannot read operation cache'
);
select ok(
    not has_table_privilege('authenticated', 'public.user_roles', 'INSERT'),
    'signed-in clients cannot assign roles'
);
select ok(
    not has_column_privilege('authenticated', 'public.profiles', 'is_active', 'UPDATE'),
    'signed-in clients cannot change active status'
);
select ok(
    has_column_privilege('authenticated', 'public.profiles', 'display_name', 'UPDATE'),
    'signed-in clients can edit display name'
);
select ok(
    exists (
        select 1 from pg_trigger
        where tgrelid = 'auth.users'::regclass
          and tgname = 'auth_user_created_profile'
          and not tgisinternal
    ),
    'signup creates a profile through a trigger'
);

select * from finish();
rollback;
