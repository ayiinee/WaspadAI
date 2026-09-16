-- Product-owned storage buckets remain private by default. The Product API is
-- responsible for validating metadata and constructing object paths.

insert into storage.buckets (id, name, public, file_size_limit)
values
    ('verification-inputs', 'verification-inputs', false, 8388608),
    ('community-previews', 'community-previews', false, 8388608),
    ('contribution-evidence', 'contribution-evidence', false, 8388608),
    ('profile-assets', 'profile-assets', false, 8388608),
    ('learning-assets', 'learning-assets', false, 8388608)
on conflict (id) do nothing;

grant usage on schema storage to product_app, product_worker;
grant select, insert, update, delete on storage.objects to product_app;
grant select, delete on storage.objects to product_worker;

create policy waspadai_product_app_storage_objects on storage.objects
for all to product_app
using (bucket_id in (
    'verification-inputs', 'community-previews', 'contribution-evidence', 'profile-assets', 'learning-assets'
))
with check (bucket_id in (
    'verification-inputs', 'community-previews', 'contribution-evidence', 'profile-assets', 'learning-assets'
));

create policy waspadai_product_worker_storage_cleanup on storage.objects
for select to product_worker
using (bucket_id in (
    'verification-inputs', 'community-previews', 'contribution-evidence', 'profile-assets', 'learning-assets'
));
create policy waspadai_product_worker_storage_delete on storage.objects
for delete to product_worker
using (bucket_id in (
    'verification-inputs', 'community-previews', 'contribution-evidence', 'profile-assets', 'learning-assets'
));
