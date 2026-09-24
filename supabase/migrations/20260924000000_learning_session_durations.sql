alter table public.quiz_attempts
    add column reading_duration_seconds integer not null default 0
        check (reading_duration_seconds between 0 and 86400),
    add column quiz_duration_seconds integer not null default 0
        check (quiz_duration_seconds between 0 and 86400);

comment on column public.quiz_attempts.reading_duration_seconds is
    'Durasi membaca materi pada sesi yang menghasilkan percobaan latihan, dalam detik.';

comment on column public.quiz_attempts.quiz_duration_seconds is
    'Durasi mengerjakan latihan pada percobaan ini, dalam detik.';
