-- Local development fixture only. This seed is deliberately inert when the
-- local Auth project has no user. Never run it against production data.

do $$
declare
    seed_user uuid;
    alya_user uuid := '10000000-0000-0000-0000-000000000001';
    dimas_user uuid := '10000000-0000-0000-0000-000000000002';
    rifqi_user uuid := '10000000-0000-0000-0000-000000000003';
    unverified_case uuid := '00000000-0000-0000-0000-000000000101';
    verified_case uuid := '00000000-0000-0000-0000-000000000102';
    private_case uuid := '00000000-0000-0000-0000-000000000103';
    verified_preview uuid := '00000000-0000-0000-0000-000000000121';
    verified_post uuid := '00000000-0000-0000-0000-000000000122';
    verified_contribution uuid := '00000000-0000-0000-0000-000000000123';
    verified_source uuid := '00000000-0000-0000-0000-000000000124';
    unverified_consent uuid := '00000000-0000-0000-0000-000000000201';
    verified_publication_consent uuid := '00000000-0000-0000-0000-000000000202';
    verified_rag_consent uuid := '00000000-0000-0000-0000-000000000203';
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
    select id into seed_user
      from auth.users
     where id not in (alya_user, dimas_user, rifqi_user)
     order by created_at
     limit 1;
    if seed_user is null then
        raise notice 'No local Auth user found; community seed skipped.';
    else

    insert into public.profiles (id, display_name)
    values (seed_user, 'Pengguna Fixture')
    on conflict (id) do nothing;

    -- Akun kreator fiktif lokal untuk membuat feed Koneksi terasa nyata.
    insert into auth.users
        (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
         raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    values
        ('00000000-0000-0000-0000-000000000000', alya_user, 'authenticated', 'authenticated',
         'alya.seed@waspadai.local', crypt('WaspadAI-seed-only', gen_salt('bf')), now(),
         '{"provider":"email","providers":["email"]}'::jsonb,
         '{"full_name":"Alya Prameswari"}'::jsonb, now() - interval '40 days', now()),
        ('00000000-0000-0000-0000-000000000000', dimas_user, 'authenticated', 'authenticated',
         'dimas.seed@waspadai.local', crypt('WaspadAI-seed-only', gen_salt('bf')), now(),
         '{"provider":"email","providers":["email"]}'::jsonb,
         '{"full_name":"Dimas Kurniawan"}'::jsonb, now() - interval '35 days', now()),
        ('00000000-0000-0000-0000-000000000000', rifqi_user, 'authenticated', 'authenticated',
         'rifqi.seed@waspadai.local', crypt('WaspadAI-seed-only', gen_salt('bf')), now(),
         '{"provider":"email","providers":["email"]}'::jsonb,
         '{"full_name":"Rifqi Aditya"}'::jsonb, now() - interval '28 days', now())
    on conflict (id) do nothing;

    update public.profiles
       set display_name = case id
           when alya_user then 'Alya Prameswari'
           when dimas_user then 'Dimas Kurniawan'
           when rifqi_user then 'Rifqi Aditya'
           else display_name
       end
     where id in (alya_user, dimas_user, rifqi_user);

    insert into public.verification_conversations
        (id, user_id, title, latest_message_preview, latest_message_role,
         last_verdict, next_turn_index, retention_expires_at)
    values
        (unverified_case, seed_user, 'Kasus komunitas belum terverifikasi',
         'Bukti pada kasus komunitas ini belum cukup untuk memastikan klaim.',
         'ASSISTANT', 'UNVERIFIED', 2, now() + interval '90 days'),
        (verified_case, seed_user, 'Kasus komunitas dengan bukti',
         'Kasus ini sudah memiliki bukti komunitas yang terverifikasi.',
         'ASSISTANT', 'UNVERIFIED', 2, now() + interval '90 days'),
        (private_case, seed_user, 'Kasus privat fixture',
         'Kasus privat ini tersimpan untuk pengguna pemilik.',
         'ASSISTANT', 'UNVERIFIED', 2, now() + interval '90 days')
    on conflict (id) do update set
        title = excluded.title,
        latest_message_preview = excluded.latest_message_preview,
        deleted_at = null;

    insert into public.verification_cases
        (id, user_id, product_request_id, conversation_id, turn_index,
         input_type, input_source, input_hash,
         headline, verdict, risk_level, requires_human_review, save_reason,
         community_state, retention_expires_at)
    values
        (unverified_case, seed_user, unverified_case, unverified_case, 1,
         'TEXT', 'MANUAL', repeat('1', 64),
         'Kasus komunitas belum terverifikasi', 'UNVERIFIED', 'UNKNOWN', true,
         'UNVERIFIED', 'PUBLISHED_UNVERIFIED', now() + interval '90 days'),
        (verified_case, seed_user, verified_case, verified_case, 1,
         'TEXT', 'MANUAL', repeat('2', 64),
         'Kasus komunitas dengan bukti', 'UNVERIFIED', 'UNKNOWN', true,
         'UNVERIFIED', 'VERIFIED_EVIDENCE', now() + interval '90 days'),
        (private_case, seed_user, private_case, private_case, 1,
         'TEXT', 'MANUAL', repeat('3', 64),
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

    insert into public.community_previews
        (id, case_id, user_id, case_revision, redacted_text, content_hash,
         redaction_version, redactions, state, expires_at, consumed_at)
    values
        (verified_preview, verified_case, seed_user, 1,
         'Pesan menawarkan bantuan tunai melalui tautan tidak resmi.',
         '59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f',
         'server-v1', '[]'::jsonb, 'CONSUMED', now() + interval '90 days', now())
    on conflict (id) do update set
        redacted_text = excluded.redacted_text,
        content_hash = excluded.content_hash,
        state = excluded.state,
        expires_at = excluded.expires_at,
        consumed_at = excluded.consumed_at;

    insert into public.contributions
        (id, user_id, case_id, title, summary, reasoning, sanitized_content, status,
         content_hash, revision, submitted_at, verified_at)
    values
        (verified_contribution, seed_user, verified_case,
         'Klaim bantuan tunai melalui tautan tidak resmi',
         'Pesan menawarkan bantuan tunai melalui tautan tidak resmi.',
         'Sumber resmi menyatakan program bantuan diumumkan melalui kanal pemerintah.',
         'Pesan menawarkan bantuan tunai melalui tautan tidak resmi.',
         'VERIFIED',
         '59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f',
         1, now() - interval '1 day', now() - interval '1 day')
    on conflict (id) do update set
        status = excluded.status,
        content_hash = excluded.content_hash,
        revision = excluded.revision,
        submitted_at = excluded.submitted_at,
        verified_at = excluded.verified_at,
        retracted_at = null;

    insert into public.contribution_sources
        (id, contribution_id, source_url, title, publisher, source_hash)
    values
        (verified_source, verified_contribution,
         'https://example.go.id/klarifikasi-bantuan',
         'Klarifikasi program bantuan', 'Instansi resmi',
         '28e27ca6b0c0fcd6ee6340afa4fcabf3c2859b82d545b6c400b7d86cd7e16f2f')
    on conflict (id) do update set
        source_url = excluded.source_url,
        title = excluded.title,
        publisher = excluded.publisher,
        source_hash = excluded.source_hash;

    insert into public.moderation_decisions
        (id, contribution_id, moderator_id, expected_revision, previous_status,
         action, new_status, reason, evidence_ids, sanitized_snapshot,
         publish_to_connection, allow_rag)
    values
        ('00000000-0000-0000-0000-000000000125', verified_contribution, seed_user, 1,
         'SUBMITTED', 'VERIFY', 'VERIFIED',
         'Fixture development: source publik diverifikasi.',
         jsonb_build_array(verified_source::text),
         jsonb_build_object(
             'verified_claim', 'Tautan pada pesan bantuan tunai tersebut bukan kanal resmi program pemerintah.',
             'stance', 'REFUTES',
             'evidence_summary', 'Moderator memverifikasi sumber resmi yang menyatakan program bantuan diumumkan melalui kanal pemerintah.'
         ),
         true, true)
    on conflict (id) do update set
        expected_revision = excluded.expected_revision,
        action = excluded.action,
        new_status = excluded.new_status,
        evidence_ids = excluded.evidence_ids,
        sanitized_snapshot = excluded.sanitized_snapshot,
        publish_to_connection = excluded.publish_to_connection,
        allow_rag = excluded.allow_rag;

    insert into private.consent_records
        (id, user_id, scope, case_id, contribution_id, preview_id, content_hash, policy_version)
    values
        (unverified_consent, seed_user, 'COMMUNITY_PUBLICATION', unverified_case,
         null, null,
         'cbfa931e75e825753f2ca57e404dc7ab359c36af5c294e32e265367446c78688',
         'community-v1'),
        (verified_publication_consent, seed_user, 'COMMUNITY_PUBLICATION', verified_case,
         verified_contribution, verified_preview,
         '59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f',
         'community-v1'),
        (verified_rag_consent, seed_user, 'RAG_REUSE', verified_case,
         verified_contribution, verified_preview,
         '59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f',
         'community-v1')
    on conflict (id) do update set
        scope = excluded.scope,
        case_id = excluded.case_id,
        contribution_id = excluded.contribution_id,
        preview_id = excluded.preview_id,
        content_hash = excluded.content_hash,
        revoked_at = null,
        expires_at = null;

    update public.contributions
       set publication_consent_id = verified_publication_consent,
           rag_consent_id = verified_rag_consent
     where id = verified_contribution;

    insert into public.community_posts
        (id, case_id, owner_id, preview_id, title, redacted_text, status,
         verification_contribution_id, publication_consent_id, rag_consent_id,
         content_hash, revision, verified_at)
    values
        ('00000000-0000-0000-0000-000000000111', unverified_case, seed_user, null,
         'Kasus komunitas belum terverifikasi',
         'Fixture publik untuk menguji feed dan vote.', 'PUBLISHED_UNVERIFIED',
         null, unverified_consent, null,
         'cbfa931e75e825753f2ca57e404dc7ab359c36af5c294e32e265367446c78688',
         1, null),
        (verified_post, verified_case, seed_user, verified_preview,
         'Klaim bantuan tunai melalui tautan tidak resmi',
         'Pesan menawarkan bantuan tunai melalui tautan tidak resmi.', 'VERIFIED_EVIDENCE',
         verified_contribution, verified_publication_consent, verified_rag_consent,
         '59233c84c571aa6bf2bf27eb0e2403c1ed9c71eb37635ff411f20a6a9c3fd52f',
         1, now() - interval '1 day')
    on conflict (case_id) do update set
        owner_id = excluded.owner_id,
        preview_id = excluded.preview_id,
        title = excluded.title,
        redacted_text = excluded.redacted_text,
        status = excluded.status,
        verification_contribution_id = excluded.verification_contribution_id,
        publication_consent_id = excluded.publication_consent_id,
        rag_consent_id = excluded.rag_consent_id,
        content_hash = excluded.content_hash,
        revision = excluded.revision,
        verified_at = excluded.verified_at,
        withdrawn_at = null;

    -- Delapan postingan contoh dengan caption, kreator, waktu, dan hasil final.
    -- Tiga yang paling baru otomatis menjadi isi "Verifikasi Kasus Terbaru".
    insert into public.verification_conversations
        (id, user_id, title, latest_message_preview, latest_message_role,
         last_verdict, next_turn_index, retention_expires_at, created_at, updated_at)
    select seeded.id, seeded.user_id, seeded.title,
           seeded.title, 'ASSISTANT', seeded.verdict, 2,
           now() + interval '90 days', seeded.created_at, seeded.created_at
      from (values
        ('10000000-0000-0000-0000-000000000611'::uuid, alya_user, 'Pesan pemblokiran rekening meminta klik tautan', 'HOAX', now() - interval '20 minutes'),
        ('10000000-0000-0000-0000-000000000612'::uuid, dimas_user, 'Foto banjir lingkungan disebut berasal dari luar negeri', 'VALID', now() - interval '2 hours'),
        ('10000000-0000-0000-0000-000000000613'::uuid, rifqi_user, 'Giveaway meminta data pribadi dan biaya klaim', 'HOAX', now() - interval '5 hours'),
        ('10000000-0000-0000-0000-000000000614'::uuid, seed_user, 'Potongan video pidato tanpa konteks utuh', 'HOAX', now() - interval '1 day'),
        ('10000000-0000-0000-0000-000000000615'::uuid, alya_user, 'Klaim pencopotan pejabat tanpa pengumuman resmi', 'HOAX', now() - interval '2 days'),
        ('10000000-0000-0000-0000-000000000616'::uuid, dimas_user, 'Domain layanan perbankan sesuai kanal resmi', 'VALID', now() - interval '3 days'),
        ('10000000-0000-0000-0000-000000000617'::uuid, rifqi_user, 'Undangan digital berbentuk APK berbahaya', 'HOAX', now() - interval '4 days'),
        ('10000000-0000-0000-0000-000000000618'::uuid, seed_user, 'Jadwal layanan publik sesuai pengumuman resmi', 'VALID', now() - interval '5 days')
      ) as seeded(id, user_id, title, verdict, created_at)
    on conflict (id) do update set
        title = excluded.title,
        latest_message_preview = excluded.latest_message_preview,
        last_verdict = excluded.last_verdict,
        updated_at = excluded.updated_at,
        deleted_at = null;

    insert into public.verification_cases
        (id, user_id, product_request_id, conversation_id, turn_index,
         input_type, input_source, input_hash,
         headline, verdict, risk_level, requires_human_review, save_reason,
         community_state, retention_expires_at, created_at)
    values
        ('10000000-0000-0000-0000-000000000611', alya_user, '10000000-0000-0000-0000-000000000611', '10000000-0000-0000-0000-000000000611', 1, 'IMAGE', 'MANUAL', repeat('a', 64),
         'Pesan pemblokiran rekening meminta klik tautan', 'HOAX', 'HIGH', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '20 minutes'),
        ('10000000-0000-0000-0000-000000000612', dimas_user, '10000000-0000-0000-0000-000000000612', '10000000-0000-0000-0000-000000000612', 1, 'IMAGE', 'MANUAL', repeat('b', 64),
         'Foto banjir lingkungan disebut berasal dari luar negeri', 'VALID', 'LOW', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '2 hours'),
        ('10000000-0000-0000-0000-000000000613', rifqi_user, '10000000-0000-0000-0000-000000000613', '10000000-0000-0000-0000-000000000613', 1, 'IMAGE', 'MANUAL', repeat('c', 64),
         'Giveaway meminta data pribadi dan biaya klaim', 'HOAX', 'HIGH', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '5 hours'),
        ('10000000-0000-0000-0000-000000000614', seed_user, '10000000-0000-0000-0000-000000000614', '10000000-0000-0000-0000-000000000614', 1, 'IMAGE', 'MANUAL', repeat('d', 64),
         'Potongan video pidato tanpa konteks utuh', 'HOAX', 'HIGH', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '1 day'),
        ('10000000-0000-0000-0000-000000000615', alya_user, '10000000-0000-0000-0000-000000000615', '10000000-0000-0000-0000-000000000615', 1, 'IMAGE', 'MANUAL', repeat('e', 64),
         'Klaim pencopotan pejabat tanpa pengumuman resmi', 'HOAX', 'HIGH', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '2 days'),
        ('10000000-0000-0000-0000-000000000616', dimas_user, '10000000-0000-0000-0000-000000000616', '10000000-0000-0000-0000-000000000616', 1, 'TEXT', 'MANUAL', repeat('f', 64),
         'Domain layanan perbankan sesuai kanal resmi', 'VALID', 'LOW', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '3 days'),
        ('10000000-0000-0000-0000-000000000617', rifqi_user, '10000000-0000-0000-0000-000000000617', '10000000-0000-0000-0000-000000000617', 1, 'TEXT', 'MANUAL', repeat('1', 64),
         'Undangan digital berbentuk APK berbahaya', 'HOAX', 'HIGH', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '4 days'),
        ('10000000-0000-0000-0000-000000000618', seed_user, '10000000-0000-0000-0000-000000000618', '10000000-0000-0000-0000-000000000618', 1, 'TEXT', 'MANUAL', repeat('2', 64),
         'Jadwal layanan publik sesuai pengumuman resmi', 'VALID', 'LOW', false, 'ALL_POLICY', 'VERIFIED_EVIDENCE', now() + interval '90 days', now() - interval '5 days')
    on conflict (id) do update set
        headline = excluded.headline,
        verdict = excluded.verdict,
        risk_level = excluded.risk_level,
        requires_human_review = excluded.requires_human_review,
        community_state = excluded.community_state,
        created_at = excluded.created_at,
        deleted_at = null;

    insert into public.verification_results
        (case_id, factual_status, source_authenticity, sender_identity,
         channel_status, scam_risk, content_authenticity, evidence_sufficiency,
         result_json, execution_mode)
    select c.id,
           case when c.verdict = 'HOAX' then 'FALSE' else 'SUPPORTED' end,
           'VERIFIED', 'VERIFIED', 'VERIFIED',
           case when c.verdict = 'HOAX' then 'HIGH' else 'LOW' end,
           'VERIFIED', .92,
           result_json || jsonb_build_object(
               'verdict', c.verdict,
               'risk_level', c.risk_level,
               'headline', c.headline,
               'requires_human_review', false,
               'community_status', 'VERIFIED_EVIDENCE'
           ),
           'MOCK'
      from public.verification_cases c
     where c.id between '10000000-0000-0000-0000-000000000611'::uuid
                    and '10000000-0000-0000-0000-000000000618'::uuid
    on conflict (case_id) do update set
        factual_status = excluded.factual_status,
        scam_risk = excluded.scam_risk,
        result_json = excluded.result_json;

    insert into private.consent_records
        (id, user_id, scope, case_id, content_hash, policy_version, granted_at)
    values
        ('10000000-0000-0000-0000-000000000811', alya_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000611', repeat('a', 64), 'community-v1', now() - interval '20 minutes'),
        ('10000000-0000-0000-0000-000000000812', dimas_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000612', repeat('b', 64), 'community-v1', now() - interval '2 hours'),
        ('10000000-0000-0000-0000-000000000813', rifqi_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000613', repeat('c', 64), 'community-v1', now() - interval '5 hours'),
        ('10000000-0000-0000-0000-000000000814', seed_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000614', repeat('d', 64), 'community-v1', now() - interval '1 day'),
        ('10000000-0000-0000-0000-000000000815', alya_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000615', repeat('e', 64), 'community-v1', now() - interval '2 days'),
        ('10000000-0000-0000-0000-000000000816', dimas_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000616', repeat('f', 64), 'community-v1', now() - interval '3 days'),
        ('10000000-0000-0000-0000-000000000817', rifqi_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000617', repeat('1', 64), 'community-v1', now() - interval '4 days'),
        ('10000000-0000-0000-0000-000000000818', seed_user, 'COMMUNITY_PUBLICATION', '10000000-0000-0000-0000-000000000618', repeat('2', 64), 'community-v1', now() - interval '5 days')
    on conflict (id) do update set revoked_at = null, expires_at = null;

    insert into public.community_posts
        (id, case_id, owner_id, title, redacted_text, status,
         publication_consent_id, content_hash, revision, published_at, verified_at)
    values
        ('10000000-0000-0000-0000-000000000711', '10000000-0000-0000-0000-000000000611', alya_user,
         'Pesan pemblokiran rekening meminta klik tautan',
         'Aku menerima pesan yang mendesak untuk verifikasi rekening lewat tautan pendek. Hasil pemeriksaan menunjukkan halaman tersebut bukan kanal resmi dan bertujuan mengambil data masuk.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000811', repeat('a', 64), 1, now() - interval '20 minutes', now() - interval '18 minutes'),
        ('10000000-0000-0000-0000-000000000712', '10000000-0000-0000-0000-000000000612', dimas_user,
         'Foto banjir ini benar, tetapi narasinya salah lokasi',
         'Foto memang menunjukkan banjir lingkungan setelah hujan deras. Penelusuran konteks membuktikan lokasinya di Indonesia, bukan di negara lain seperti narasi yang beredar.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000812', repeat('b', 64), 1, now() - interval '2 hours', now() - interval '110 minutes'),
        ('10000000-0000-0000-0000-000000000713', '10000000-0000-0000-0000-000000000613', rifqi_user,
         'Giveaway berhadiah meminta biaya klaim',
         'Unggahan mengaku memberikan hadiah besar tetapi meminta data pribadi dan transfer biaya administrasi. Akun serta mekanisme giveaway tidak dapat diverifikasi.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000813', repeat('c', 64), 1, now() - interval '5 hours', now() - interval '290 minutes'),
        ('10000000-0000-0000-0000-000000000714', '10000000-0000-0000-0000-000000000614', seed_user,
         'Potongan video pidato kehilangan konteks',
         'Video pendek yang ramai dibagikan memotong bagian penting dari pidato. Rekaman utuh menunjukkan pembicaraan berbeda dari klaim pada caption viral.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000814', repeat('d', 64), 1, now() - interval '1 day', now() - interval '23 hours'),
        ('10000000-0000-0000-0000-000000000715', '10000000-0000-0000-0000-000000000615', alya_user,
         'Klaim pencopotan pejabat belum pernah diumumkan',
         'Tidak ditemukan keputusan atau pengumuman resmi yang mendukung klaim pencopotan tersebut. Gambar judul berita telah disunting dan sumber aslinya membahas topik lain.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000815', repeat('e', 64), 1, now() - interval '2 days', now() - interval '47 hours'),
        ('10000000-0000-0000-0000-000000000716', '10000000-0000-0000-0000-000000000616', dimas_user,
         'Alamat domain layanan perbankan sesuai kanal resmi',
         'Alamat situs yang diperiksa cocok dengan domain yang tercantum pada aplikasi dan pusat bantuan resmi. Tetap ketik alamat secara mandiri dan jangan masuk dari tautan pesan.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000816', repeat('f', 64), 1, now() - interval '3 days', now() - interval '70 hours'),
        ('10000000-0000-0000-0000-000000000717', '10000000-0000-0000-0000-000000000617', rifqi_user,
         'Undangan digital berbentuk APK adalah modus berbahaya',
         'File undangan dikirim sebagai aplikasi APK dan meminta akses SMS. Format ini bukan undangan biasa dan berisiko mencuri kode OTP serta data perangkat.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000817', repeat('1', 64), 1, now() - interval '4 days', now() - interval '94 hours'),
        ('10000000-0000-0000-0000-000000000718', '10000000-0000-0000-0000-000000000618', seed_user,
         'Jadwal layanan publik sesuai pengumuman resmi',
         'Jadwal yang beredar cocok dengan pengumuman pada kanal resmi instansi. Tanggal, lokasi, dan nomor layanan telah diperiksa ulang.',
         'VERIFIED_EVIDENCE', '10000000-0000-0000-0000-000000000818', repeat('2', 64), 1, now() - interval '5 days', now() - interval '118 hours')
    on conflict (case_id) do update set
        owner_id = excluded.owner_id,
        title = excluded.title,
        redacted_text = excluded.redacted_text,
        status = excluded.status,
        publication_consent_id = excluded.publication_consent_id,
        content_hash = excluded.content_hash,
        published_at = excluded.published_at,
        verified_at = excluded.verified_at,
        withdrawn_at = null;

    -- Media pada bucket khusus ini dibaca dari backend/assets/community saat demo lokal.
    insert into private.stored_assets
        (id, user_id, case_id, bucket, object_path, purpose, mime_type, size_bytes, sha256)
    values
        ('10000000-0000-0000-0000-000000000911', alya_user, '10000000-0000-0000-0000-000000000611', 'seed-assets', 'phishing-account.png', 'SCREENSHOT_OPT_IN', 'image/png', 1939479, repeat('a', 64)),
        ('10000000-0000-0000-0000-000000000912', dimas_user, '10000000-0000-0000-0000-000000000612', 'seed-assets', 'neighborhood-flood.png', 'SCREENSHOT_OPT_IN', 'image/png', 2793477, repeat('b', 64)),
        ('10000000-0000-0000-0000-000000000913', rifqi_user, '10000000-0000-0000-0000-000000000613', 'seed-assets', 'fake-giveaway.png', 'SCREENSHOT_OPT_IN', 'image/png', 1931634, repeat('c', 64)),
        ('10000000-0000-0000-0000-000000000914', seed_user, '10000000-0000-0000-0000-000000000614', 'seed-assets', 'prabowo-video.png', 'SCREENSHOT_OPT_IN', 'image/png', 1070596, repeat('d', 64)),
        ('10000000-0000-0000-0000-000000000915', alya_user, '10000000-0000-0000-0000-000000000615', 'seed-assets', 'gibran-rumor.png', 'SCREENSHOT_OPT_IN', 'image/png', 186941, repeat('e', 64))
    on conflict (id) do update set
        object_path = excluded.object_path,
        size_bytes = excluded.size_bytes,
        deleted_at = null;

    insert into public.community_media
        (id, community_id, asset_id, media_type, sort_order)
    values
        ('10000000-0000-0000-0000-000000000921', '10000000-0000-0000-0000-000000000711', '10000000-0000-0000-0000-000000000911', 'IMAGE', 0),
        ('10000000-0000-0000-0000-000000000922', '10000000-0000-0000-0000-000000000712', '10000000-0000-0000-0000-000000000912', 'IMAGE', 0),
        ('10000000-0000-0000-0000-000000000923', '10000000-0000-0000-0000-000000000713', '10000000-0000-0000-0000-000000000913', 'IMAGE', 0),
        ('10000000-0000-0000-0000-000000000924', '10000000-0000-0000-0000-000000000714', '10000000-0000-0000-0000-000000000914', 'IMAGE', 0),
        ('10000000-0000-0000-0000-000000000925', '10000000-0000-0000-0000-000000000715', '10000000-0000-0000-0000-000000000915', 'IMAGE', 0)
    on conflict (id) do nothing;

    insert into public.community_votes (post_id, user_id, vote, reasoning, created_at)
    values
        ('10000000-0000-0000-0000-000000000711', dimas_user, 'HOAKS', 'Tautan tidak menggunakan domain resmi dan meminta kredensial.', now() - interval '15 minutes'),
        ('10000000-0000-0000-0000-000000000711', rifqi_user, 'HOAKS', 'Bahasa mendesak dan tautan pendek merupakan pola phishing.', now() - interval '14 minutes'),
        ('10000000-0000-0000-0000-000000000712', alya_user, 'VALID', 'Foto asli, tetapi konteks lokasi pada caption awal memang keliru.', now() - interval '100 minutes'),
        ('10000000-0000-0000-0000-000000000712', rifqi_user, 'VALID', 'Konteks telah cocok dengan sumber lokal.', now() - interval '95 minutes'),
        ('10000000-0000-0000-0000-000000000713', alya_user, 'HOAKS', 'Akun tidak resmi dan meminta biaya sebelum hadiah diterima.', now() - interval '4 hours'),
        ('10000000-0000-0000-0000-000000000713', dimas_user, 'HOAKS', 'Mekanisme hadiah tidak mempunyai syarat resmi yang dapat dicek.', now() - interval '4 hours'),
        ('10000000-0000-0000-0000-000000000714', alya_user, 'HOAKS', 'Potongan video menghilangkan kalimat sebelum dan sesudahnya.', now() - interval '22 hours'),
        ('10000000-0000-0000-0000-000000000715', dimas_user, 'HOAKS', 'Tidak ada keputusan resmi yang mendukung klaim.', now() - interval '46 hours'),
        ('10000000-0000-0000-0000-000000000716', alya_user, 'VALID', 'Domain cocok dengan pusat bantuan resmi.', now() - interval '69 hours'),
        ('10000000-0000-0000-0000-000000000717', dimas_user, 'HOAKS', 'File APK dari chat bukan undangan digital yang aman.', now() - interval '93 hours'),
        ('10000000-0000-0000-0000-000000000718', alya_user, 'VALID', 'Tanggal dan lokasi sama dengan pengumuman instansi.', now() - interval '117 hours')
    on conflict (post_id, user_id) do update set
        vote = excluded.vote,
        reasoning = excluded.reasoning,
        created_at = excluded.created_at,
        updated_at = excluded.created_at;

    insert into public.community_likes (post_id, user_id, created_at)
    values
        ('10000000-0000-0000-0000-000000000711', seed_user, now() - interval '12 minutes'),
        ('10000000-0000-0000-0000-000000000711', dimas_user, now() - interval '11 minutes'),
        ('10000000-0000-0000-0000-000000000712', seed_user, now() - interval '80 minutes'),
        ('10000000-0000-0000-0000-000000000712', alya_user, now() - interval '75 minutes'),
        ('10000000-0000-0000-0000-000000000713', seed_user, now() - interval '3 hours'),
        ('10000000-0000-0000-0000-000000000714', alya_user, now() - interval '20 hours'),
        ('10000000-0000-0000-0000-000000000715', dimas_user, now() - interval '44 hours')
    on conflict (post_id, user_id) do nothing;

    insert into public.community_views (post_id, user_id, first_seen_at, last_seen_at)
    select post.id, viewer.id, post.published_at, greatest(post.published_at, now() - interval '5 minutes')
      from public.community_posts post
      cross join (values (seed_user), (alya_user), (dimas_user), (rifqi_user)) viewer(id)
     where post.id between '10000000-0000-0000-0000-000000000711'::uuid
                       and '10000000-0000-0000-0000-000000000718'::uuid
    on conflict (post_id, user_id) do update set last_seen_at = excluded.last_seen_at;

    insert into public.community_shares (id, post_id, user_id, created_at)
    values
        ('10000000-0000-0000-0000-000000000931', '10000000-0000-0000-0000-000000000711', seed_user, now() - interval '8 minutes'),
        ('10000000-0000-0000-0000-000000000932', '10000000-0000-0000-0000-000000000712', alya_user, now() - interval '70 minutes'),
        ('10000000-0000-0000-0000-000000000933', '10000000-0000-0000-0000-000000000713', dimas_user, now() - interval '3 hours')
    on conflict (id) do nothing;

    end if;

    -- LEARNING_SEED_BEGIN
    -- Seed konten Pelajari secara independen dari fixture user/community.
    insert into public.learning_modules
        (id, slug, title, summary, difficulty, display_order, version, status)
    values
        ('00000000-0000-0000-0000-000000000301', 'phishing-otp-pin',
         'Phishing, OTP, dan PIN',
         'Pahami modus pencurian kredensial, peretasan melalui kode OTP, dan cara mengamankan PIN akun Anda dari social engineering.',
         1, 1, 1, 'DRAFT'),
        ('00000000-0000-0000-0000-000000000401', 'impersonation-instansi-resmi',
         'Impersonation Instansi Resmi',
         'Kenali modus penipuan yang mengatasnamakan lembaga perbankan, aparat penegak hukum, ekspedisi paket, atau instansi pemerintah.',
         2, 2, 1, 'DRAFT'),
        ('00000000-0000-0000-0000-000000000501', 'misinformasi-dan-klaim-tanpa-bukti',
         'Misinformasi dan Klaim Tanpa Bukti',
         'Pelajari cara mengenali berita bohong (hoaks), memeriksa rujukan primer, serta membedakan misinformasi dan disinformasi.',
         2, 3, 1, 'DRAFT')
    on conflict (id) do nothing;

    insert into public.learning_lessons
        (id, module_id, title, body_md, duration_minutes, display_order, is_published)
    values
        -- Modul 1: Phishing, OTP, dan PIN
        ('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000301',
         'Jaga Kerahasiaan OTP dan PIN',
         '## Mengapa OTP dan PIN Sangat Rahasia?

One-Time Password (OTP) dan Personal Identification Number (PIN) adalah garis pertahanan terakhir akun digital Anda.

- **OTP** digunakan untuk otorisasi transaksi sensitif atau login dari perangkat baru.
- **PIN** bertindak sebagai kunci permanen otorisasi kartu atau dompet digital.

**Prinsip Utama:** Pihak bank, dompet digital, atau aplikasi resmi **tidak pernah** meminta kode OTP maupun PIN Anda untuk alasan apa pun.',
         5, 1, true),
        ('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000301',
         'Ciri-Ciri Pesan & Situs Phishing',
         '## Mengenali Jebakan Phishing

Phishing sering kali memanfaatkan rasa cemas atau tawaran hadiah menggiurkan.

1. **Pengirim tidak resmi**: Nomor asing di aplikasi perpesanan atau domain email gratisan.
2. **Tautan palsu**: Menggunakan domain tiruan dengan ejaan mirip (typosquatting).
3. **Mendesak/Urgen**: Mengancam pemblokiran akun jika tidak segera login dalam waktu singkat.',
         5, 2, true),

        -- Modul 2: Impersonation Instansi Resmi
        ('00000000-0000-0000-0000-000000000411', '00000000-0000-0000-0000-000000000401',
         'Modus Penipuan Mengaku Petugas Resmi',
         '## Siapa yang Sering Ditiru Penipu?

Penipu kerap menyamar menjadi pihak yang memiliki otoritas tinggi:

- **Kurir Ekspedisi**: Mengirim file berbahaya berkedok resi foto paket (.APK).
- **Polisi / OJK / Pajak**: Menakut-nakuti korban tentang kasus kriminal palsu atau denda mendadak.
- **Customer Service**: Menghubungi korban yang mengeluh di kolom komentar media sosial terbuka.',
         5, 1, true),
        ('00000000-0000-0000-0000-000000000412', '00000000-0000-0000-0000-000000000401',
         'Cara Memverifikasi Identitas Petugas & Dokumen',
         '## Langkah Verifikasi Cepat

Saat menerima panggilan atau pesan dari seseorang yang mengaku petugas resmi:

1. **Putus komunikasi**: Jangan panik dan jangan ikuti instruksi transfer uang.
2. **Cek saluran resmi**: Hubungi nomor hotline resmi yang tertera di website terverifikasi.
3. **Waspadai file APK**: Jangan pernah menginstal aplikasi di luar Google Play Store.',
         5, 2, true),

        -- Modul 3: Misinformasi dan Klaim Tanpa Bukti
        ('00000000-0000-0000-0000-000000000511', '00000000-0000-0000-0000-000000000501',
         'Memahami Perbedaan Hoaks, Misinformasi, dan Disinformasi',
         '## Memahami Ekosistem Gangguan Informasi

- **Misinformasi**: Informasi salah yang disebarkan tanpa niat jahat (tidak sengaja).
- **Disinformasi**: Informasi palsu yang sengaja dibuat dan disebarkan untuk menyesatkan publik.
- **Malinformasi**: Informasi benar yang disalahgunakan untuk merusak reputasi pihak lain.',
         5, 1, true),
        ('00000000-0000-0000-0000-000000000512', '00000000-0000-0000-0000-000000000501',
         'Langkah Sederhana Fact-Checking Pesan Berantai',
         '## Menjadi Pemeriksa Fakta Mandiri

Sebelum meneruskan pesan berantai ke keluarga atau grup:

1. **Baca utuh**: Jangan hanya membaca judul sensasional.
2. **Cari rujukan primer**: Apakah kantor berita resmi atau jurnal sains memberitakannya?
3. **Gunakan WaspadAI**: Masukkan teks atau tangkapan layar untuk verifikasi berbasis bukti.',
         5, 2, true)
    on conflict (id) do nothing;

    insert into public.learning_cases
        (id, module_id, title, description, display_order)
    values
        ('00000000-0000-0000-0000-000000000341', '00000000-0000-0000-0000-000000000301',
         'Pesan meminta OTP', 'Pengirim mengaku petugas resmi dan meminta OTP untuk membatalkan transaksi.', 1),
        ('00000000-0000-0000-0000-000000000441', '00000000-0000-0000-0000-000000000401',
         'Kurir mengirim APK', 'Pengirim mengaku kurir dan meminta pengguna memasang file APK.', 1),
        ('00000000-0000-0000-0000-000000000541', '00000000-0000-0000-0000-000000000501',
         'Pesan broadcast tanpa sumber', 'Pesan sensasional meminta penerima segera menyebarkan klaim tanpa rujukan primer.', 1)
    on conflict (id) do update set
        title = excluded.title,
        description = excluded.description,
        display_order = excluded.display_order;

    insert into public.learning_media
        (id, module_id, media_type, url, title, alt_text, display_order)
    values
        ('00000000-0000-0000-0000-000000000342', '00000000-0000-0000-0000-000000000301', 'IMAGE',
         '/api/v1/learning/media/learning/phishing-otp-pin.jpg',
         'Ilustrasi Keamanan OTP dan Perlindungan Akun Digital',
         'Ilustrasi keamanan akun digital menggunakan OTP dan perlindungan terhadap serangan phishing', 1),
        ('00000000-0000-0000-0000-000000000442', '00000000-0000-0000-0000-000000000401', 'IMAGE',
         '/api/v1/learning/media/learning/impersonation-instansi-resmi.jpg',
         'Ilustrasi Modus Penipuan Impersonation',
         'Ilustrasi seseorang menyamar sebagai pihak resmi untuk melakukan penipuan', 1),
        ('00000000-0000-0000-0000-000000000542', '00000000-0000-0000-0000-000000000501', 'IMAGE',
         '/api/v1/learning/media/learning/misinformasi-fact-checking.png',
         'Ilustrasi Verifikasi Informasi dan Fact Checking',
         'Ilustrasi proses memeriksa kebenaran informasi dan membedakan berita palsu', 1)
    on conflict (id) do update set
        media_type = excluded.media_type,
        url = excluded.url,
        title = excluded.title,
        alt_text = excluded.alt_text,
        display_order = excluded.display_order;

    insert into public.quiz_questions
        (id, module_id, lesson_id, question_text, explanation, version, display_order, is_active)
    values
        -- Soal Modul 1
        ('00000000-0000-0000-0000-000000000321', '00000000-0000-0000-0000-000000000301',
         '00000000-0000-0000-0000-000000000311',
         'Apakah petugas bank atau penyedia layanan dompet digital berhak meminta kode OTP Anda?',
         'Petugas bank atau layanan resmi tidak pernah meminta OTP Anda untuk alasan apa pun. OTP hanya untuk Anda masukkan sendiri.',
         1, 1, true),
        ('00000000-0000-0000-0000-000000000322', '00000000-0000-0000-0000-000000000301',
         '00000000-0000-0000-0000-000000000312',
         'Apa tindakan yang paling tepat saat menerima SMS mencurigakan berisi tautan login pembatalan biaya transaksi?',
         'Abaikan tautan tersebut dan buka aplikasi resmi secara mandiri untuk memastikan kebenaran transaksi.',
         1, 2, true),
        ('00000000-0000-0000-0000-000000000323', '00000000-0000-0000-0000-000000000301',
         '00000000-0000-0000-0000-000000000311',
         'Mengapa Anda tidak boleh menggunakan kombinasi tanggal lahir sebagai kode PIN transaksi?',
         'Tanggal lahir sangat mudah ditemukan penipu melalui media sosial atau data pribadi yang bocor.',
         1, 3, true),

        -- Soal Modul 2
        ('00000000-0000-0000-0000-000000000421', '00000000-0000-0000-0000-000000000401',
         '00000000-0000-0000-0000-000000000411',
         'Pengirim pesan mengaku kurir paket dan mengirim file dengan ekstensi .APK untuk melacak kiriman. Apa yang harus Anda lakukan?',
         'File APK berbahaya dapat mencuri SMS OTP dan data perbankan jika diinstal ke ponsel Anda. Hapus pesan tersebut.',
         1, 1, true),
        ('00000000-0000-0000-0000-000000000422', '00000000-0000-0000-0000-000000000401',
         '00000000-0000-0000-0000-000000000412',
         'Bagaimana cara memastikan apakah akun media sosial customer service benar-benar resmi?',
         'Periksa lencana verifikasi centang resmi dan cocokkan nomor hotline pada situs resmi instansi terkait.',
         1, 2, true),
        ('00000000-0000-0000-0000-000000000423', '00000000-0000-0000-0000-000000000401',
         '00000000-0000-0000-0000-000000000411',
         'Jika seseorang mengaku aparat penegak hukum menelepon dan menuntut transfer uang agar kasus tidak dilanjutkan, hal itu adalah:',
         'Aparat penegak hukum tidak pernah meminta transfer uang melalui telepon untuk penghentian perkara hukum.',
         1, 3, true),

        -- Soal Modul 3
        ('00000000-0000-0000-0000-000000000521', '00000000-0000-0000-0000-000000000501',
         '00000000-0000-0000-0000-000000000511',
         'Apa ciri khas pesan broadcast hoaks yang dirancang untuk mudah viral di media sosial?',
         'Pesan hoaks umumnya sensasional, mencantumkan ajakan sebarkan sekarang juga, dan tanpa rujukan institusi valid.',
         1, 1, true),
        ('00000000-0000-0000-0000-000000000522', '00000000-0000-0000-0000-000000000501',
         '00000000-0000-0000-0000-000000000512',
         'Langkah bijak apa yang harus dilakukan sebelum membagikan informasi pengobatan herbal ajaib di grup keluarga?',
         'Lakukan fact-checking terhadap rujukan medis resmi sebelum menyebarkan klaim kesehatan yang belum teruji.',
         1, 2, true),
        ('00000000-0000-0000-0000-000000000523', '00000000-0000-0000-0000-000000000501',
         '00000000-0000-0000-0000-000000000512',
         'Apa peran utama fitur verifikasi WaspadAI ketika Anda menemukan klaim mencurigakan?',
         'WaspadAI menganalisis indikasi kebenaran, mencocokkan bukti faktual, dan menyajikan narasi hasil pemeriksaan.',
         1, 3, true)
    on conflict (id) do nothing;

    insert into public.quiz_options
        (id, question_id, option_text, display_order, is_correct)
    values
        -- Opsi Modul 1 Soal 1
        ('00000000-0000-0000-0000-000000000331', '00000000-0000-0000-0000-000000000321',
         'Tidak pernah. OTP bersifat rahasia dan hanya diinput oleh pemilik akun.', 1, true),
        ('00000000-0000-0000-0000-000000000332', '00000000-0000-0000-0000-000000000321',
         'Boleh, jika petugas menyebutkan nama lengkap dan nomor rekening Anda.', 2, false),

        -- Opsi Modul 1 Soal 2
        ('00000000-0000-0000-0000-000000000333', '00000000-0000-0000-0000-000000000322',
         'Jangan klik tautan tersebut dan cek riwayat mutasi lewat aplikasi resmi.', 1, true),
        ('00000000-0000-0000-0000-000000000334', '00000000-0000-0000-0000-000000000322',
         'Segera klik tautan dan masukkan PIN kartu untuk membatalkan tagihan.', 2, false),

        -- Opsi Modul 1 Soal 3
        ('00000000-0000-0000-0000-000000000335', '00000000-0000-0000-0000-000000000323',
         'Sangat mudah ditebak oleh pelaku kejahatan siber melalui profiling sosial.', 1, true),
        ('00000000-0000-0000-0000-000000000336', '00000000-0000-0000-0000-000000000323',
         'Mesin ATM akan otomatis menolak transaksi jika PIN sama dengan tanggal lahir.', 2, false),

        -- Opsi Modul 2 Soal 1
        ('00000000-0000-0000-0000-000000000431', '00000000-0000-0000-0000-000000000421',
         'Jangan unduh atau buka file APK tersebut dan segera hapus pesannya.', 1, true),
        ('00000000-0000-0000-0000-000000000432', '00000000-0000-0000-0000-000000000421',
         'Instal aplikasinya untuk memastikan paket Anda tidak hilang.', 2, false),

        -- Opsi Modul 2 Soal 2
        ('00000000-0000-0000-0000-000000000433', '00000000-0000-0000-0000-000000000422',
         'Memiliki centang verifikasi resmi dan saluran kontak sesuai web resmi.', 1, true),
        ('00000000-0000-0000-0000-000000000434', '00000000-0000-0000-0000-000000000422',
         'Menghubungi Anda lewat direct message (DM) meminta nomor kartu debit.', 2, false),

        -- Opsi Modul 2 Soal 3
        ('00000000-0000-0000-0000-000000000435', '00000000-0000-0000-0000-000000000423',
         'Modus pemerasan dan penipuan impersonation aparat.', 1, true),
        ('00000000-0000-0000-0000-000000000436', '00000000-0000-0000-0000-000000000423',
         'Prosedur resmi penyelesaian restorative justice kepolisian.', 2, false),

        -- Opsi Modul 3 Soal 1
        ('00000000-0000-0000-0000-000000000531', '00000000-0000-0000-0000-000000000521',
         'Menggunakan bahasa bombastis, memicu kepanikan, tanpa sumber terpercaya.', 1, true),
        ('00000000-0000-0000-0000-000000000532', '00000000-0000-0000-0000-000000000521',
         'Menyertakan rujukan tautan jurnal ilmiah terindeks internasional.', 2, false),

        -- Opsi Modul 3 Soal 2
        ('00000000-0000-0000-0000-000000000533', '00000000-0000-0000-0000-000000000522',
         'Verifikasi kebenarannya ke sumber kredibel sebelum meneruskan pesan.', 1, true),
        ('00000000-0000-0000-0000-000000000534', '00000000-0000-0000-0000-000000000522',
         'Langsung meneruskan pesan agar keluarga cepat waspada.', 2, false),

        -- Opsi Modul 3 Soal 3
        ('00000000-0000-0000-0000-000000000535', '00000000-0000-0000-0000-000000000523',
         'Menyediakan analisis multi-dimensi faktual dan panduan tindakan pengguna.', 1, true),
        ('00000000-0000-0000-0000-000000000536', '00000000-0000-0000-0000-000000000523',
         'Menghapus secara otomatis kontak pengirim pesan berantai dari telepon.', 2, false),

        -- Tambahan opsi C dan D agar setiap soal memiliki empat pilihan
        ('00000000-0000-0000-0000-000000000337', '00000000-0000-0000-0000-000000000321',
         'Boleh jika permintaan disampaikan melalui telepon kantor bank.', 3, false),
        ('00000000-0000-0000-0000-000000000338', '00000000-0000-0000-0000-000000000321',
         'Boleh selama kode tersebut belum pernah digunakan sebelumnya.', 4, false),
        ('00000000-0000-0000-0000-000000000339', '00000000-0000-0000-0000-000000000322',
         'Balas SMS tersebut dan minta pengirim menjelaskan transaksinya.', 3, false),
        ('00000000-0000-0000-0000-000000000340', '00000000-0000-0000-0000-000000000322',
         'Teruskan tautannya kepada teman untuk meminta pendapat.', 4, false),
        ('00000000-0000-0000-0000-000000000341', '00000000-0000-0000-0000-000000000323',
         'Karena tanggal lahir hanya dapat digunakan untuk PIN kartu kredit.', 3, false),
        ('00000000-0000-0000-0000-000000000342', '00000000-0000-0000-0000-000000000323',
         'Karena PIN dengan angka berurutan selalu diblokir oleh bank.', 4, false),

        ('00000000-0000-0000-0000-000000000437', '00000000-0000-0000-0000-000000000421',
         'Balas pesan dan minta kurir mengirim file APK versi terbaru.', 3, false),
        ('00000000-0000-0000-0000-000000000438', '00000000-0000-0000-0000-000000000421',
         'Buka file menggunakan ponsel milik orang lain terlebih dahulu.', 4, false),
        ('00000000-0000-0000-0000-000000000439', '00000000-0000-0000-0000-000000000422',
         'Akun tersebut sering mengadakan undian berhadiah untuk pengikutnya.', 3, false),
        ('00000000-0000-0000-0000-000000000440', '00000000-0000-0000-0000-000000000422',
         'Nama akun menggunakan huruf kapital dan logo perusahaan.', 4, false),
        ('00000000-0000-0000-0000-000000000441', '00000000-0000-0000-0000-000000000423',
         'Layanan percepatan perkara yang berlaku untuk keadaan darurat.', 3, false),
        ('00000000-0000-0000-0000-000000000442', '00000000-0000-0000-0000-000000000423',
         'Prosedur pembayaran denda resmi melalui rekening pribadi aparat.', 4, false),

        ('00000000-0000-0000-0000-000000000537', '00000000-0000-0000-0000-000000000521',
         'Ditulis singkat dan selalu menggunakan bahasa yang baku.', 3, false),
        ('00000000-0000-0000-0000-000000000538', '00000000-0000-0000-0000-000000000521',
         'Memiliki banyak komentar dan sudah diteruskan berkali-kali.', 4, false),
        ('00000000-0000-0000-0000-000000000539', '00000000-0000-0000-0000-000000000522',
         'Tambahkan catatan bahwa informasi tersebut belum tentu benar.', 3, false),
        ('00000000-0000-0000-0000-000000000540', '00000000-0000-0000-0000-000000000522',
         'Tanyakan kepada pengirim apakah ia sudah mencoba pengobatannya.', 4, false),
        ('00000000-0000-0000-0000-000000000541', '00000000-0000-0000-0000-000000000523',
         'Menentukan kebenaran hanya berdasarkan jumlah orang yang membagikan.', 3, false),
        ('00000000-0000-0000-0000-000000000542', '00000000-0000-0000-0000-000000000523',
         'Meneruskan semua klaim mencurigakan langsung kepada seluruh kontak.', 4, false)
    on conflict (id) do nothing;

    -- Publikasikan modul setelah seluruh lesson dan pertanyaan siap (validasi trigger)
    update public.learning_modules
       set status = 'PUBLISHED'
     where id in (
        '00000000-0000-0000-0000-000000000301',
        '00000000-0000-0000-0000-000000000401',
        '00000000-0000-0000-0000-000000000501'
     );
    -- LEARNING_SEED_END
end $$;
