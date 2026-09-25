-- The Product API owns avatar metadata. Keep profile updates column-scoped while
-- allowing it to attach or clear a validated avatar asset for the current user.
grant update (avatar_asset_id) on table public.profiles to product_app;
