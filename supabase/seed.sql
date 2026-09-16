-- Local development fixture only. This seed is deliberately inert when the
-- local Auth project has no user. Never run it against production data.

do $$
declare
    seed_user uuid;
    unverified_case uuid := '00000000-0000-0000-0000-000000000101';
    verified_case uuid := '00000000-0000-0000-0000-000000000102';
    private_case uuid := '00000000-0000-0000-0000-000000000103';
    unverified_consent uuid := '00000000-0000-0000-0000-000000000201';
    verified_consent uuid := '00000000-0000-0000-0000-000000000202';
    result_json jsonb := jsonb_build_object(
        'request_id', 'seed-request',
        'trace_id', 'seed-trace',
        'status', 'COMPLETED',
        'mode', 'LIVE',
        'mode_notice', 'Fixture development.',
        'input_summary', jsonb_build_object('input_type', 'TEXT'),
        'verdict', 'UNVERIFIED',
        'risk_level', 'UNKNOWN',
        'dimensions', jsonb_build_object(
            'factual_status', 'UNVERIFIED',
            'source_authenticity', 'UNVERIFIED',
            'sender_identity', 'UNVERIFIED',
            'channel_status', 'UNVERIFIED',
            'scam_risk', 'UNKNOWN',
            'content_authenticity', 'NOT_APPLICABLE'
        ),
        'headline', 'Fixture komunitas untuk pengujian.',
        'evidence_sufficiency', 0,
        'evidence_sufficiency_label', 'Fixture development.',
        'what_checked', jsonb_build_array(),
        'why', jsonb_build_array('Fixture development.'),
        'evidence', jsonb_build_array(),
        'recommended_actions', jsonb_build_array(),
        'sources', jsonb_build_array(),
        'uncertainty', 'Fixture development.',
        'requires_human_review', true,
        'community_status', 'ELIGIBLE_WITH_CONSENT',
        'privacy_notice', 'Fixture development.',
        'rulebook', jsonb_build_object(),
        'pipeline', jsonb_build_array(),
        'presentation', jsonb_build_object(
            'narrative', jsonb_build_object('text', 'Fixture komunitas development.')
        ),
        'disclaimer', 'Fixture development.'
    );
begin
    select id into seed_user from auth.users order by created_at limit 1;
    if seed_user is null then
        raise notice 'No local Auth user found; community seed skipped.';
        return;
    end if;

    insert into public.profiles (id, display_name)
    values (seed_user, 'Pengguna Fixture')
    on conflict (id) do nothing;

    insert into public.verification_cases
        (id, user_id, product_request_id, input_type, input_source, input_hash,
         headline, verdict, risk_level, requires_human_review, save_reason,
         community_state, retention_expires_at)
    values
        (unverified_case, seed_user, unverified_case, 'TEXT', 'MANUAL', repeat('1', 64),
         'Kasus komunitas belum terverifikasi', 'UNVERIFIED', 'UNKNOWN', true,
         'UNVERIFIED', 'PUBLISHED_UNVERIFIED', now() + interval '90 days'),
        (verified_case, seed_user, verified_case, 'TEXT', 'MANUAL', repeat('2', 64),
         'Kasus komunitas dengan bukti', 'UNVERIFIED', 'UNKNOWN', true,
         'UNVERIFIED', 'VERIFIED_EVIDENCE', now() + interval '90 days'),
        (private_case, seed_user, private_case, 'TEXT', 'MANUAL', repeat('3', 64),
         'Kasus privat fixture', 'UNVERIFIED', 'UNKNOWN', true,
         'UNVERIFIED', 'PRIVATE', now() + interval '90 days')
    on conflict (id) do nothing;

    insert into public.verification_results
        (case_id, factual_status, source_authenticity, sender_identity,
         channel_status, scam_risk, content_authenticity, evidence_sufficiency,
         result_json, execution_mode)
    values
        (unverified_case, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
         'UNKNOWN', 'NOT_APPLICABLE', 0, result_json, 'MOCK'),
        (verified_case, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
         'UNKNOWN', 'NOT_APPLICABLE', 0, result_json, 'MOCK'),
        (private_case, 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED', 'UNVERIFIED',
         'UNKNOWN', 'NOT_APPLICABLE', 0, result_json, 'MOCK')
    on conflict (case_id) do nothing;

    insert into private.consent_records
        (id, user_id, scope, case_id, content_hash, policy_version)
    values
        (unverified_consent, seed_user, 'COMMUNITY_PUBLICATION', unverified_case,
         repeat('a', 64), 'community-v1'),
        (verified_consent, seed_user, 'COMMUNITY_PUBLICATION', verified_case,
         repeat('b', 64), 'community-v1')
    on conflict (id) do nothing;

    insert into public.community_posts
        (case_id, owner_id, title, redacted_text, status, publication_consent_id,
         content_hash, revision)
    values
        (unverified_case, seed_user, 'Kasus komunitas belum terverifikasi',
         'Fixture publik untuk menguji feed dan vote.', 'PUBLISHED_UNVERIFIED',
         unverified_consent, repeat('a', 64), 1),
        (verified_case, seed_user, 'Kasus komunitas dengan bukti',
         'Fixture terverifikasi untuk menguji feed dan detail.', 'VERIFIED_EVIDENCE',
         verified_consent, repeat('b', 64), 1)
    on conflict (case_id) do nothing;
end $$;
