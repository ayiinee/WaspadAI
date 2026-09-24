# Community seed assets

These images are local-development fixtures for `supabase/seed.sql`.
They are served by the Product API only when a `private.stored_assets` row uses
the `seed-assets` bucket. Production uploads continue to use Supabase Storage.

- `phishing-account.png`, `fake-giveaway.png`, and `neighborhood-flood.png`
  are AI-generated fictional evidence images.
- `prabowo-video.png` and `gibran-rumor.png` are copied from the existing
  Android demo drawable set.

The people and claims in these fixtures are examples for UI testing. They do
not represent real WaspadAI users or authoritative fact-check records.
