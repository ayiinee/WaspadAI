"""Idempotent learning content seeder.

Run with: python -m scripts.seed_learning (from backend).
The seed uses stable slugs and is safe to run repeatedly.
"""
from __future__ import annotations

from typing import Any

from psycopg import AsyncConnection


SEED_CONTENT: list[dict[str, Any]] = [
    {
        "topic": ("literasi-digital", "Literasi digital", "Kebiasaan memahami, memeriksa, dan membagikan informasi secara bertanggung jawab."),
        "slug": "kenali-ciri-hoaks",
        "title": "Kenali ciri-ciri hoaks",
        "summary": "Temukan tanda-tanda informasi yang perlu diperiksa ulang.",
        "difficulty": 1,
        "lessons": [
            ("Apa itu hoaks?", "Hoaks adalah informasi palsu atau menyesatkan yang dibuat seolah-olah benar. Berhenti sejenak sebelum mempercayai atau membagikannya.", 5),
            ("Perhatikan judul dan bahasa", "Judul berlebihan, tekanan untuk segera menyebarkan, dan klaim tanpa sumber adalah tanda untuk melakukan pemeriksaan tambahan.", 6),
        ],
        "cases": [("Pesan berantai mendesak", "Pesan meminta penerima menyebarkan kabar sebelum batas waktu tertentu, tetapi tidak menyertakan sumber yang dapat diverifikasi.")],
        "media": [("IMAGE", "https://placehold.co/1200x675/png?text=Cek+Hoaks", "Poster langkah cek hoaks"), ("YOUTUBE", "https://www.youtube.com/watch?v=example", "Video pengantar literasi digital")],
        "quiz": [("Kalimat mana yang paling perlu dicurigai?", "Tekanan untuk segera menyebarkan tanpa sumber adalah tanda bahaya.", [("Baca laporan lengkap di situs resmi", False), ("Sebarkan sekarang juga sebelum dihapus!", True), ("Data dirangkum dari tiga sumber", False)])],
    },
    {
        "topic": ("verifikasi-sumber", "Verifikasi sumber", "Cara menelusuri sumber pertama, tanggal, konteks, dan bukti sebuah klaim."),
        "slug": "periksa-sumber-informasi",
        "title": "Periksa sumber informasi",
        "summary": "Latih kebiasaan mengecek sumber sebelum percaya atau berbagi.",
        "difficulty": 2,
        "lessons": [("Cari sumber pertama", "Telusuri siapa yang pertama kali menerbitkan informasi. Sumber asli biasanya memiliki konteks, tanggal, dan identitas yang dapat diperiksa.", 7), ("Bandingkan bukti", "Bandingkan klaim dengan sumber resmi atau media kredibel. Jika konteks berbeda, tunda kesimpulan.", 8)],
        "cases": [("Klaim tanpa tanggal", "Sebuah gambar lama dibagikan sebagai kejadian baru. Periksa tanggal dan konteks sebelum menyimpulkan.")],
        "media": [("IMAGE", "https://placehold.co/1200x675/png?text=Verifikasi+Sumber", "Diagram verifikasi sumber"), ("YOUTUBE", "https://www.youtube.com/watch?v=example", "Video cara memeriksa sumber")],
        "quiz": [("Mengapa tanggal publikasi perlu diperiksa?", "Tanggal membantu memahami konteks dan kebaruan informasi.", [("Agar tahu konteks dan kebaruan informasi", True), ("Supaya unggahan terlihat populer", False), ("Karena semua informasi lama pasti salah", False)])],
    },
]


async def seed_learning_content(connection: AsyncConnection) -> int:
    """Insert/update curated content and return the number of modules seeded."""
    for order, module in enumerate(SEED_CONTENT):
        topic_slug, topic_title, topic_description = module["topic"]
        topic = await connection.execute(
            """insert into public.learning_topics(slug, title, description)
               values (%s, %s, %s)
               on conflict (slug) do update set title = excluded.title, description = excluded.description
               returning id""",
            (topic_slug, topic_title, topic_description),
        )
        topic_id = (await topic.fetchone())["id"]
        row = await connection.execute(
            """insert into public.learning_modules(slug, title, summary, difficulty, display_order, version, status, topic_id)
               values (%s, %s, %s, %s, %s, 1, 'DRAFT', %s)
               on conflict (slug, version) do update set title = excluded.title, summary = excluded.summary,
                 difficulty = excluded.difficulty, display_order = excluded.display_order, status = 'DRAFT', topic_id = excluded.topic_id
               returning id""",
            (module["slug"], module["title"], module["summary"], module["difficulty"], order, topic_id),
        )
        module_id = (await row.fetchone())["id"]
        for lesson_order, (title, body, duration) in enumerate(module["lessons"]):
            await connection.execute(
                """insert into public.learning_lessons(module_id, title, body_md, duration_minutes, display_order, is_published)
                   values (%s, %s, %s, %s, %s, true)
                   on conflict (module_id, display_order) do update set title = excluded.title, body_md = excluded.body_md,
                     duration_minutes = excluded.duration_minutes, is_published = true""",
                (module_id, title, body, duration, lesson_order),
            )
        await connection.execute("delete from public.learning_cases where module_id = %s", (module_id,))
        for case_order, (title, description) in enumerate(module["cases"]):
            await connection.execute(
                "insert into public.learning_cases(module_id, title, description, display_order) values (%s, %s, %s, %s)",
                (module_id, title, description, case_order),
            )
        await connection.execute("delete from public.learning_media where module_id = %s", (module_id,))
        for media_order, (media_type, url, title) in enumerate(module["media"]):
            await connection.execute(
                "insert into public.learning_media(module_id, media_type, url, title, display_order) values (%s, %s, %s, %s, %s)",
                (module_id, media_type, url, title, media_order),
            )
        for question_order, (question, explanation, options) in enumerate(module["quiz"]):
            question_row = await connection.execute(
                """insert into public.quiz_questions(module_id, question_text, explanation, version, display_order, is_active)
                   values (%s, %s, %s, 1, %s, true)
                   on conflict (module_id, display_order, version) do update set question_text = excluded.question_text,
                     explanation = excluded.explanation, is_active = true returning id""",
                (module_id, question, explanation, question_order),
            )
            question_id = (await question_row.fetchone())["id"]
            await connection.execute("delete from public.quiz_options where question_id = %s", (question_id,))
            for option_order, (text, is_correct) in enumerate(options):
                await connection.execute(
                    "insert into public.quiz_options(question_id, option_text, display_order, is_correct) values (%s, %s, %s, %s)",
                    (question_id, text, option_order, is_correct),
                )
        await connection.execute("update public.learning_modules set status = 'PUBLISHED' where id = %s", (module_id,))
    return len(SEED_CONTENT)
