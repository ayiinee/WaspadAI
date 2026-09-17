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

    -- Seed konten Pelajari (Learning Modules, Lessons, Quiz Questions & Options)
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
         'Bahaya Membagikan Kode OTP & PIN',
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
         'Menghapus secara otomatis kontak pengirim pesan berantai dari telepon.', 2, false)
    on conflict (id) do nothing;

    -- Publikasikan modul setelah seluruh lesson dan pertanyaan siap (validasi trigger)
    update public.learning_modules
       set status = 'PUBLISHED'
     where id in (
        '00000000-0000-0000-0000-000000000301',
        '00000000-0000-0000-0000-000000000401',
        '00000000-0000-0000-0000-000000000501'
     );
end $$;
