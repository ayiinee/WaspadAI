"""PostgreSQL transaction boundary and statements for Community."""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from uuid import UUID

from psycopg_pool import AsyncConnectionPool

from app.database import user_transaction as database_user_transaction


@asynccontextmanager
async def community_transaction(
    pool: AsyncConnectionPool,
    user_id: UUID,
    statement_timeout_seconds: int,
) -> AsyncIterator[object]:
    async with database_user_transaction(
        pool,
        user_id,
        statement_timeout_seconds,
    ) as connection:
        yield connection


INSERT_REFRESH_AUDIT = """

            insert into private.audit_logs
                (actor_id, action, resource_type, resource_id, request_id, safe_metadata)
            values (%s, 'COMMUNITY_FEED_REFRESHED', 'community_feed', 'connection-feed', %s, %s)
            
"""

SELECT_COMMUNITY_FEED = """

            select p.id as community_id, p.case_id, p.title, p.redacted_text,
                   p.status, p.published_at,
                   (p.owner_id = %s) as is_owner,
                   private.community_display_name(p.owner_id) as creator_display_name,
                   (
                       p.redacted_asset_id is not null
                       or coalesce(jsonb_array_length(media.items), 0) > 0
                   ) as has_image,
                   coalesce(media.items, '[]'::jsonb) as media,
                   coalesce(counts.hoaks, 0)::int as hoaks,
                   coalesce(counts.waspada, 0)::int as waspada,
                   coalesce(counts.valid, 0)::int as valid,
                   own.vote as user_vote,
                   coalesce(social.like_count, 0)::int as like_count,
                   coalesce(social.view_count, 0)::int as view_count,
                   coalesce(social.comment_count, 0)::int as comment_count,
                   coalesce(social.share_count, 0)::int as share_count,
                   coalesce(social.user_liked, false) as user_liked
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              left join public.community_votes own
                on own.post_id = p.id and own.user_id = %s
              left join lateral (
                  select jsonb_agg(
                      jsonb_build_object(
                          'id', m.id, 'media_type', m.media_type,
                          'position', m.sort_order, 'width', m.width, 'height', m.height
                      ) order by m.sort_order
                  ) as items
                    from public.community_media m
                   where m.community_id = p.id
              ) media on true
              left join lateral (
                  select
                      count(*) filter (where v.vote = 'HOAKS') as hoaks,
                      count(*) filter (where v.vote = 'WASPADA') as waspada,
                      count(*) filter (where v.vote = 'VALID') as valid
                    from public.community_votes v
                   where v.post_id = p.id
             ) counts on true
             left join lateral (
                 select (select count(*) from public.community_likes l
                          where l.post_id = p.id) as like_count,
                        (select count(*) from public.community_views v
                          where v.post_id = p.id) as view_count,
                        (select count(*) from public.community_votes comment
                          where comment.post_id = p.id) as comment_count,
                        (select count(*) from public.community_shares share
                          where share.post_id = p.id) as share_count,
                        exists (select 1 from public.community_likes mine
                                where mine.post_id = p.id and mine.user_id = %s) as user_liked
             ) social on true
             where p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and p.publication_consent_id is not null
               and exists (
                   select 1
                     from private.consent_records c
                    where c.id = p.publication_consent_id
                      and c.scope = 'COMMUNITY_PUBLICATION'
                      and c.revoked_at is null
                      and (c.expires_at is null or c.expires_at > now())
               )
               and (%s::timestamptz is null or (p.published_at, p.case_id) <
                   (%s::timestamptz, %s::uuid))
             order by p.published_at desc, p.case_id desc
             limit %s
            
"""

SELECT_USER_SUMMARY = """

            select
                coalesce((
                    select count(*)
                      from public.community_votes v
                     where v.user_id = %s
                ), 0)::int as assessments_count,
                coalesce((
                    select count(*)
                      from public.contributions c
                     where c.user_id = %s
                       and c.status <> 'RETRACTED'
                       and c.retracted_at is null
                ), 0)::int as evidence_added_count,
                coalesce((
                    select count(*)
                      from public.contributions c
                     where c.user_id = %s
                       and c.status = 'VERIFIED'
                       and c.retracted_at is null
                ), 0)::int as resolved_cases_count
            
"""

SELECT_RESPONSE_EVIDENCE_ASSET = """
select evidence_asset_id
                 from public.community_votes
                where post_id = %s and user_id = %s
"""

INSERT_RESPONSE_ASSET = """

                insert into private.stored_assets
                    (user_id, case_id, bucket, object_path, purpose, mime_type,
                     size_bytes, sha256, expires_at)
                values (%s, %s, 'verification-inputs', %s, 'CONTRIBUTION_EVIDENCE',
                        %s, %s, %s, now() + make_interval(hours => %s))
                returning id
                
"""

UPSERT_COMMUNITY_RESPONSE = """

            insert into public.community_votes
                (post_id, user_id, vote, reasoning, evidence_asset_id)
            values (%s, %s, %s, %s, %s)
            on conflict (post_id, user_id) do update
                set vote = excluded.vote,
                    reasoning = excluded.reasoning,
                    evidence_asset_id = coalesce(excluded.evidence_asset_id,
                                                 public.community_votes.evidence_asset_id),
                    updated_at = now()
            
"""

SOFT_DELETE_ASSET = """
update private.stored_assets set deleted_at = now() where id = %s
"""

SELECT_LEGACY_COMMUNITY_IMAGE = """

            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              join private.stored_assets asset on asset.id = p.redacted_asset_id
             where (p.id = %s or p.case_id = %s)
               and p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and p.publication_consent_id is not null
               and asset.deleted_at is null
               and exists (
                   select 1 from private.consent_records consent
                    where consent.id = p.publication_consent_id
                      and consent.scope = 'COMMUNITY_PUBLICATION'
                      and consent.revoked_at is null
                      and (consent.expires_at is null or consent.expires_at > now())
               )
            
"""

SELECT_PREVIEW_MEDIA_ASSET = """

            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_previews preview
              join public.community_preview_media media on media.preview_id = preview.id
              join private.stored_assets asset on asset.id = media.asset_id
             where preview.id = %s and preview.case_id = %s and preview.user_id = %s
               and media.id = %s and preview.state = 'READY'
               and preview.expires_at > now() and asset.deleted_at is null
            
"""

SELECT_COMMUNITY_MEDIA_ASSET = """

            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_posts post
              join public.community_media media on media.community_id = post.id
              join private.stored_assets asset on asset.id = media.asset_id
             where (post.id = %s or post.case_id = %s) and media.id = %s
               and post.withdrawn_at is null
               and post.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and post.publication_consent_id is not null
               and asset.deleted_at is null
            
"""

SELECT_RESPONSE_IMAGE_ASSET = """

            select asset.bucket, asset.object_path, asset.mime_type
              from public.community_votes v
              join public.community_posts p on p.id = v.post_id
              join private.stored_assets asset on asset.id = v.evidence_asset_id
             where (p.id = %s or p.case_id = %s) and v.user_id = %s
               and p.withdrawn_at is null
               and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
               and asset.deleted_at is null
            
"""

UPSERT_COMMUNITY_VOTE = """

            insert into public.community_votes (post_id, user_id, vote)
            values (%s, %s, %s)
            on conflict (post_id, user_id) do update
                set vote = excluded.vote, updated_at = now()
            
"""

DELETE_COMMUNITY_VOTE = """
delete from public.community_votes where post_id = %s and user_id = %s
"""

SELECT_CASE_FOR_PREVIEW = """

            select c.id, c.revision, c.headline, c.sanitized_text, c.risk_level
              from public.verification_cases c
             where c.id = %s and c.user_id = %s and c.deleted_at is null
               and c.community_state = 'PRIVATE'
            
"""

SELECT_CASE_IMAGE_ASSET = """

            select asset.id
              from private.stored_assets asset
             where asset.case_id = %s
               and asset.purpose = 'SCREENSHOT_OPT_IN'
               and asset.deleted_at is null
             order by asset.created_at, asset.id
             limit 4
            
"""

INSERT_COMMUNITY_PREVIEW = """

            insert into public.community_previews
                (id, case_id, user_id, case_revision, redacted_text,
                    redacted_asset_id, content_hash, redaction_version,
                    redactions, state, expires_at)
            values (%s, %s, %s, %s, %s, %s, %s, 'server-v1', '[]'::jsonb, 'READY',
                    %s + make_interval(secs => %s))
            returning expires_at
            
"""

INSERT_COMMUNITY_PREVIEW_MEDIA = """

                insert into public.community_preview_media(preview_id, asset_id, position)
                values (%s, %s, %s)
                returning id
                
"""

SELECT_PREVIEW_FOR_PUBLICATION = """

                select p.id as preview_id, p.redacted_text, p.redacted_asset_id, p.content_hash,
                       p.case_revision, p.expires_at, p.state,
                       c.headline, c.revision
                  from public.community_previews p
                  join public.verification_cases c on c.id = p.case_id
                 where p.id = %s and p.case_id = %s and p.user_id = %s
                   and c.user_id = %s and c.deleted_at is null
                   and c.community_state = 'PRIVATE'
                for update of p, c
                
"""

INSERT_PUBLICATION_CONSENT = """

                insert into private.consent_records
                    (user_id, scope, case_id, preview_id, content_hash, policy_version)
                values (%s, 'COMMUNITY_PUBLICATION', %s, %s, %s, 'community-v1')
                returning id
                
"""

INSERT_RAG_CONSENT = """

                    insert into private.consent_records
                        (user_id, scope, case_id, preview_id, content_hash, policy_version)
                    values (%s, 'RAG_REUSE', %s, %s, %s, 'community-v1')
                    returning id
                    
"""

INSERT_COMMUNITY_POST = """

                insert into public.community_posts
                    (case_id, owner_id, preview_id, title, redacted_text, redacted_asset_id, status,
                     publication_consent_id, rag_consent_id, content_hash, revision)
                values (%s, %s, %s, %s, %s, %s, 'PUBLISHED_UNVERIFIED', %s, %s, %s, %s)
                returning id
                
"""

INSERT_COMMUNITY_MEDIA_FROM_PREVIEW = """

                insert into public.community_media
                    (community_id, asset_id, media_type, sort_order, width, height)
                select %s, preview_media.asset_id, preview_media.media_type,
                       preview_media.position, preview_media.width, preview_media.height
                  from public.community_preview_media preview_media
                 where preview_media.preview_id = %s
                 order by preview_media.position
                 limit 4
                on conflict do nothing
                
"""

INSERT_LEGACY_COMMUNITY_MEDIA = """

                    insert into public.community_media(community_id, asset_id, sort_order)
                    values (%s, %s, 0)
                    on conflict do nothing
                    
"""

UPDATE_CASE_PUBLISHED = """

                update public.verification_cases
                   set community_state = 'PUBLISHED_UNVERIFIED', revision = revision + 1
                 where id = %s
                 returning revision
                
"""

UPDATE_PREVIEW_PUBLISHED = """

                update public.community_previews
                   set state = 'CONSUMED', consumed_at = now()
                 where id = %s
                
"""

SELECT_POST_FOR_UPDATE = """

            select p.id, p.status, p.publication_consent_id, p.rag_consent_id
              from public.community_posts p
             where p.id = %s and p.owner_id = %s and p.withdrawn_at is null
             for update
            
"""

UPDATE_COMMUNITY_POST = """

            update public.community_posts
               set redacted_text = %s, content_hash = %s, revision = revision + 1
             where id = %s
            
"""

UPDATE_CONSENT_HASH = """

            update private.consent_records
               set content_hash = %s
             where user_id = %s and id in (%s, %s) and revoked_at is null
            
"""

SELECT_POST_FOR_WITHDRAWAL = """

            select p.id as post_id, p.status as post_status, p.publication_consent_id,
                   p.rag_consent_id, c.community_state, c.revision
              from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              join public.verification_cases c on c.id = p.case_id
             where p.case_id = %s and p.owner_id = %s and c.user_id = %s
               and c.deleted_at is null
             for update of p, c
            
"""

WITHDRAW_COMMUNITY_POST = """

            update public.community_posts
               set status = 'WITHDRAWN', withdrawn_at = now(), revision = revision + 1
             where id = %s
            
"""

UPDATE_CASE_WITHDRAWN = """

            update public.verification_cases
               set community_state = 'WITHDRAWN', revision = revision + 1
             where id = %s
             returning revision
            
"""

REVOKE_COMMUNITY_CONSENTS = """

            update private.consent_records
               set revoked_at = now()
             where user_id = %s
               and id in (%s, %s)
               and scope in ('COMMUNITY_PUBLICATION', 'RAG_REUSE')
               and revoked_at is null
            
"""

SELECT_COMMUNITY_DETAIL = """

        select p.id as community_id, p.case_id, p.title, p.redacted_text, p.status, p.published_at,
               (p.owner_id = %s) as is_owner,
               private.community_display_name(p.owner_id) as creator_display_name,
               (
                   p.redacted_asset_id is not null
                   or coalesce(jsonb_array_length(media.items), 0) > 0
               ) as has_image,
               coalesce(media.items, '[]'::jsonb) as media,
               r.result_json, r.execution_mode,
               coalesce(counts.hoaks, 0)::int as hoaks,
               coalesce(counts.waspada, 0)::int as waspada,
               coalesce(counts.valid, 0)::int as valid,
               own.vote as user_vote,
               coalesce(social.like_count, 0)::int as like_count,
               coalesce(social.view_count, 0)::int as view_count,
               coalesce(social.comment_count, 0)::int as comment_count,
               coalesce(social.share_count, 0)::int as share_count,
               coalesce(social.user_liked, false) as user_liked
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
          join public.verification_cases c on c.id = p.case_id and c.deleted_at is null
          join public.verification_results r on r.case_id = p.case_id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
          left join lateral (
              select jsonb_agg(
                  jsonb_build_object(
                      'id', m.id, 'media_type', m.media_type,
                      'position', m.sort_order, 'width', m.width, 'height', m.height
                  ) order by m.sort_order
              ) as items
                from public.community_media m
               where m.community_id = p.id
          ) media on true
          left join lateral (
              select
                  count(*) filter (where v.vote = 'HOAKS') as hoaks,
                  count(*) filter (where v.vote = 'WASPADA') as waspada,
                  count(*) filter (where v.vote = 'VALID') as valid
                from public.community_votes v
               where v.post_id = p.id
         ) counts on true
         left join lateral (
             select (select count(*) from public.community_likes l
                      where l.post_id = p.id) as like_count,
                    (select count(*) from public.community_views v
                      where v.post_id = p.id) as view_count,
                    (select count(*) from public.community_votes comment
                      where comment.post_id = p.id) as comment_count,
                    (select count(*) from public.community_shares share
                      where share.post_id = p.id) as share_count,
                    exists (select 1 from public.community_likes mine
                            where mine.post_id = p.id and mine.user_id = %s) as user_liked
         ) social on true
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
           and exists (
               select 1 from private.consent_records consent
                where consent.id = p.publication_consent_id
                  and consent.scope = 'COMMUNITY_PUBLICATION'
                  and consent.revoked_at is null
                  and (consent.expires_at is null or consent.expires_at > now())
           )
        
"""

SELECT_COMMUNITY_RESPONSES = """

        select v.user_id as response_id,
               coalesce(nullif(btrim(profile.display_name), ''), 'Pengguna WaspadAI') as author,
               v.created_at, v.vote, v.reasoning,
               (v.evidence_asset_id is not null) as has_image
          from public.community_votes v
          join public.community_posts p on p.id = v.post_id
          left join public.profiles profile on profile.id = v.user_id
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
         order by v.created_at desc
        
"""

SELECT_COMMUNITY_RESPONSE = """

        select v.user_id as response_id,
               coalesce(nullif(btrim(profile.display_name), ''), 'Pengguna WaspadAI') as author,
               v.created_at, v.vote, v.reasoning,
               (v.evidence_asset_id is not null) as has_image
          from public.community_votes v
          left join public.profiles profile on profile.id = v.user_id
         where v.post_id = %s and v.user_id = %s
        
"""

SELECT_VOTE_TARGET = """

        select p.id as post_id, p.case_id, p.owner_id, p.status
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
           and exists (
               select 1 from private.consent_records consent
                where consent.id = p.publication_consent_id
                  and consent.scope = 'COMMUNITY_PUBLICATION'
                  and consent.revoked_at is null
                  and (consent.expires_at is null or consent.expires_at > now())
           )
        
"""

SELECT_VOTE_RESULT = """

        select p.id as community_id, p.case_id, own.vote as user_vote,
               count(*) filter (where v.vote = 'HOAKS')::int as hoaks,
               count(*) filter (where v.vote = 'WASPADA')::int as waspada,
               count(*) filter (where v.vote = 'VALID')::int as valid
          from public.community_posts p
          join public.verification_results result on result.case_id = p.case_id
          left join public.community_votes v on v.post_id = p.id
          left join public.community_votes own
            on own.post_id = p.id and own.user_id = %s
         where (p.id = %s or p.case_id = %s)
           and p.withdrawn_at is null
           and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
           and p.publication_consent_id is not null
         group by p.id, p.case_id, own.vote
        
"""

INSERT_COMMUNITY_LIKE = """
insert into public.community_likes(post_id, user_id)
               values (%s, %s) on conflict do nothing
"""

DELETE_COMMUNITY_LIKE = """
delete from public.community_likes where post_id = %s and user_id = %s
"""

INSERT_COMMUNITY_VIEW = """
insert into public.community_views(post_id, user_id) values (%s, %s)
               on conflict (post_id, user_id) do update set last_seen_at = now()
"""

INSERT_COMMUNITY_SHARE = """
insert into public.community_shares(post_id, user_id) values (%s, %s)
"""

SELECT_SOCIAL_TARGET = """
select p.id as post_id, p.case_id
             from public.community_posts p
             join public.verification_results result on result.case_id = p.case_id
            where (p.id = %s or p.case_id = %s)
              and p.withdrawn_at is null
              and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
              and p.publication_consent_id is not null
              and exists (
                  select 1 from private.consent_records c
                   where c.id = p.publication_consent_id
                     and c.scope = 'COMMUNITY_PUBLICATION'
                     and c.revoked_at is null
                     and (c.expires_at is null or c.expires_at > now())
              )
"""

SELECT_SOCIAL_RESULT = """
select p.id as community_id, p.case_id,
                    exists (select 1 from public.community_likes l
                            where l.post_id = p.id and l.user_id = %s) as liked,
                    (select count(*) from public.community_likes l
                      where l.post_id = p.id)::int as like_count,
                    (select count(*) from public.community_views v
                      where v.post_id = p.id)::int as view_count,
                    (select count(*) from public.community_votes v
                      where v.post_id = p.id)::int as comment_count,
                    (select count(*) from public.community_shares s
                      where s.post_id = p.id)::int as share_count
               from public.community_posts p
              join public.verification_results result on result.case_id = p.case_id
              where (p.id = %s or p.case_id = %s)
                and p.withdrawn_at is null
                and p.status in ('PUBLISHED_UNVERIFIED', 'VERIFIED_EVIDENCE')
                and p.publication_consent_id is not null
"""
