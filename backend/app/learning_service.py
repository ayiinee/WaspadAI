from __future__ import annotations

from datetime import UTC, datetime
from typing import Any
from uuid import UUID

from psycopg.rows import DictRow
from psycopg.types.json import Jsonb
from psycopg_pool import AsyncConnectionPool

from app.config import Settings
from app.database import user_transaction
from app.errors import ProductAPIError
from app.models import (
    LearningLesson,
    LearningModuleDetail,
    LearningModuleItem,
    LearningProgressItem,
    LearningProgressResponse,
    LearningQuiz,
    LessonCompleteResponse,
    QuizAttemptRequest,
    QuizAttemptResult,
    QuizOption,
    QuizQuestion,
    QuizQuestionFeedback,
)


async def list_learning_modules(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
) -> list[LearningModuleItem]:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select m.id, m.slug, m.title, m.summary, m.difficulty, m.version,
                   count(l.id)::int as total_lessons,
                   count(lp.lesson_id)::int as completed_lessons
              from public.published_learning_modules m
              left join public.published_learning_lessons l on l.module_id = m.id
              left join public.lesson_progress lp
                on lp.lesson_id = l.id and lp.user_id = %s
             group by m.id, m.slug, m.title, m.summary, m.difficulty,
                      m.display_order, m.version
             order by m.display_order, m.id
            """,
            (user_id,),
        )
        rows = await query.fetchall()
    return [_module_item(row) for row in rows]


async def get_learning_module_detail(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    module_id: UUID,
) -> LearningModuleDetail:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        module = await _fetch_module_progress_row(connection, user_id, module_id)
        if module is None:
            raise ProductAPIError(404, "LEARNING_MODULE_NOT_FOUND", "Modul tidak ditemukan.")
        lessons_query = await connection.execute(
            """
            select l.id, l.title, l.body_md, l.duration_minutes, l.display_order,
                   (lp.lesson_id is not null) as completed
              from public.published_learning_lessons l
              left join public.lesson_progress lp
                on lp.lesson_id = l.id and lp.user_id = %s
             where l.module_id = %s
             order by l.display_order, l.id
            """,
            (user_id, module_id),
        )
        lesson_rows = await lessons_query.fetchall()
    item = _module_item(module)
    return LearningModuleDetail(
        module_id=item.module_id,
        slug=item.slug,
        title=item.title,
        summary=item.summary,
        difficulty=item.difficulty,
        version=item.version,
        total_lessons=item.total_lessons,
        completed_lessons=item.completed_lessons,
        progress_percent=item.progress_percent,
        lessons=[
            LearningLesson(
                lesson_id=row["id"],
                title=row["title"],
                body_md=row["body_md"],
                duration_minutes=row["duration_minutes"],
                display_order=row["display_order"],
                completed=row["completed"],
            )
            for row in lesson_rows
        ],
    )


async def complete_lesson(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    lesson_id: UUID,
    idempotency_key: UUID,
) -> LessonCompleteResponse:
    _ = idempotency_key
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        lesson_query = await connection.execute(
            "select id, module_id from public.published_learning_lessons where id = %s",
            (lesson_id,),
        )
        lesson = await lesson_query.fetchone()
        if lesson is None:
            raise ProductAPIError(404, "LEARNING_LESSON_NOT_FOUND", "Lesson tidak ditemukan.")
        await connection.execute(
            """
            insert into public.lesson_progress (user_id, lesson_id)
            values (%s, %s)
            on conflict (user_id, lesson_id) do nothing
            """,
            (user_id, lesson_id),
        )
        progress_query = await connection.execute(
            "select completed_at from public.lesson_progress where user_id = %s and lesson_id = %s",
            (user_id, lesson_id),
        )
        progress = await progress_query.fetchone()
        module = await _fetch_module_progress_row(connection, user_id, lesson["module_id"])
        if module is None:
            raise ProductAPIError(404, "LEARNING_MODULE_NOT_FOUND", "Modul tidak ditemukan.")
    return LessonCompleteResponse(
        lesson_id=lesson_id,
        module_id=lesson["module_id"],
        completed=True,
        completed_at=_iso8601(progress["completed_at"]),
        progress_percent=_progress_percent(module["completed_lessons"], module["total_lessons"]),
    )


async def get_module_quiz(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    module_id: UUID,
) -> LearningQuiz:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        module_query = await connection.execute(
            "select id, version from public.published_learning_modules where id = %s",
            (module_id,),
        )
        module = await module_query.fetchone()
        if module is None:
            raise ProductAPIError(404, "LEARNING_MODULE_NOT_FOUND", "Modul tidak ditemukan.")
        questions = await _fetch_public_quiz_rows(connection, module_id)
    return LearningQuiz(
        module_id=module_id,
        module_version=module["version"],
        questions=_questions_from_public_rows(questions),
    )


async def submit_quiz_attempt(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
    module_id: UUID,
    idempotency_key: UUID,
    payload: QuizAttemptRequest,
) -> QuizAttemptResult:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        existing = await _fetch_attempt_by_key(connection, user_id, module_id, idempotency_key)
        if existing is not None:
            return _attempt_result(existing)

        module_query = await connection.execute(
            "select id, version from public.published_learning_modules where id = %s",
            (module_id,),
        )
        module = await module_query.fetchone()
        if module is None:
            raise ProductAPIError(404, "LEARNING_MODULE_NOT_FOUND", "Modul tidak ditemukan.")
        if module["version"] != payload.module_version:
            raise ProductAPIError(
                409, "MODULE_VERSION_MISMATCH", "Versi modul berubah. Muat ulang quiz."
            )

        rows = await _fetch_scoring_rows(connection, module_id, payload.module_version)
        if not rows:
            raise ProductAPIError(404, "QUIZ_NOT_FOUND", "Quiz modul tidak ditemukan.")
        scoring = _score_answers(rows, payload)
        inserted = await connection.execute(
            """
            insert into public.quiz_attempts
                (user_id, module_id, module_version, submission_key, total_questions,
                 correct_answers, score, question_snapshot)
            values (%s, %s, %s, %s, %s, %s, %s, %s)
            on conflict (user_id, module_id, submission_key) do nothing
            returning id, score, correct_answers, total_questions, question_snapshot
            """,
            (
                user_id,
                module_id,
                payload.module_version,
                idempotency_key,
                scoring["total_questions"],
                scoring["correct_answers"],
                scoring["score"],
                Jsonb(scoring["snapshot"]),
            ),
        )
        attempt = await inserted.fetchone()
        if attempt is None:
            existing = await _fetch_attempt_by_key(connection, user_id, module_id, idempotency_key)
            if existing is not None:
                return _attempt_result(existing)
            raise ProductAPIError(
                409,
                "QUIZ_ATTEMPT_CONFLICT",
                "Percobaan quiz dengan kunci idempotensi ini tidak dapat diproses.",
            )
        for answer in scoring["answers"]:
            await connection.execute(
                """
                insert into public.quiz_answers
                    (attempt_id, question_id, selected_option_id, is_correct)
                values (%s, %s, %s, %s)
                """,
                (
                    attempt["id"],
                    answer["question_id"],
                    answer["selected_option_id"],
                    answer["is_correct"],
                ),
            )
    return _attempt_result(attempt)


async def get_learning_progress(
    pool: AsyncConnectionPool,
    settings: Settings,
    user_id: UUID,
) -> LearningProgressResponse:
    async with user_transaction(pool, user_id, settings.db_statement_timeout_seconds) as connection:
        query = await connection.execute(
            """
            select m.id as module_id,
                   count(distinct l.id)::int as total_lessons,
                   count(distinct lp.lesson_id)::int as completed_lessons,
                   max(lp.completed_at) as last_lesson_completed_at,
                   latest.score::float as latest_score,
                   best.best_score::float as best_score,
                   latest.completed_at as latest_quiz_completed_at
              from public.published_learning_modules m
              left join public.published_learning_lessons l on l.module_id = m.id
              left join public.lesson_progress lp
                on lp.lesson_id = l.id and lp.user_id = %s
              left join lateral (
                  select a.score, a.completed_at
                    from public.quiz_attempts a
                   where a.user_id = %s and a.module_id = m.id
                   order by a.completed_at desc, a.id desc
                   limit 1
              ) latest on true
              left join lateral (
                  select max(a.score) as best_score
                    from public.quiz_attempts a
                   where a.user_id = %s and a.module_id = m.id
              ) best on true
             group by m.id, m.display_order, latest.score, latest.completed_at, best.best_score
             order by m.display_order, m.id
            """,
            (user_id, user_id, user_id),
        )
        rows = await query.fetchall()
    return LearningProgressResponse(items=[_progress_item(row) for row in rows])


async def _fetch_module_progress_row(
    connection: object, user_id: UUID, module_id: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select m.id, m.slug, m.title, m.summary, m.difficulty, m.version,
               count(l.id)::int as total_lessons,
               count(lp.lesson_id)::int as completed_lessons
          from public.published_learning_modules m
          left join public.published_learning_lessons l on l.module_id = m.id
          left join public.lesson_progress lp
            on lp.lesson_id = l.id and lp.user_id = %s
         where m.id = %s
         group by m.id, m.slug, m.title, m.summary, m.difficulty, m.version
        """,
        (user_id, module_id),
    )
    return await query.fetchone()


async def _fetch_public_quiz_rows(connection: object, module_id: UUID) -> list[DictRow]:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select q.id as question_id, q.question_text,
               o.id as option_id, o.option_text, o.display_order as option_order,
               q.display_order as question_order
          from public.published_quiz_questions q
          left join public.published_quiz_options o on o.question_id = q.id
         where q.module_id = %s
         order by q.display_order, q.id, o.display_order, o.id
        """,
        (module_id,),
    )
    return await query.fetchall()


async def _fetch_scoring_rows(
    connection: object, module_id: UUID, module_version: int
) -> list[DictRow]:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select q.id as question_id, q.question_text, q.explanation, q.version,
               q.display_order as question_order,
               o.id as option_id, o.option_text, o.is_correct,
               o.display_order as option_order
          from public.quiz_questions q
          join public.learning_modules m on m.id = q.module_id and m.status = 'PUBLISHED'
          join public.quiz_options o on o.question_id = q.id
         where q.module_id = %s and q.is_active and q.version = %s
         order by q.display_order, q.id, o.display_order, o.id
        """,
        (module_id, module_version),
    )
    return await query.fetchall()


async def _fetch_attempt_by_key(
    connection: object, user_id: UUID, module_id: UUID, submission_key: UUID
) -> DictRow | None:
    query = await connection.execute(  # type: ignore[attr-defined]
        """
        select id, score, correct_answers, total_questions, question_snapshot
          from public.quiz_attempts
         where user_id = %s and module_id = %s and submission_key = %s
        """,
        (user_id, module_id, submission_key),
    )
    return await query.fetchone()


def _module_item(row: DictRow) -> LearningModuleItem:
    return LearningModuleItem(
        module_id=row["id"],
        slug=row["slug"],
        title=row["title"],
        summary=row["summary"],
        difficulty=row["difficulty"],
        version=row["version"],
        total_lessons=row["total_lessons"],
        completed_lessons=row["completed_lessons"],
        progress_percent=_progress_percent(row["completed_lessons"], row["total_lessons"]),
    )


def _questions_from_public_rows(rows: list[DictRow]) -> list[QuizQuestion]:
    questions: dict[UUID, dict[str, Any]] = {}
    for row in rows:
        question = questions.setdefault(
            row["question_id"],
            {"text": row["question_text"], "options": []},
        )
        if row["option_id"] is not None:
            question["options"].append(
                QuizOption(option_id=row["option_id"], text=row["option_text"])
            )
    return [
        QuizQuestion(question_id=question_id, text=data["text"], options=data["options"])
        for question_id, data in questions.items()
    ]


def _score_answers(rows: list[DictRow], payload: QuizAttemptRequest) -> dict[str, Any]:
    questions: dict[UUID, dict[str, Any]] = {}
    for row in rows:
        question = questions.setdefault(
            row["question_id"],
            {
                "question_text": row["question_text"],
                "explanation": row["explanation"],
                "version": row["version"],
                "options": {},
            },
        )
        question["options"][row["option_id"]] = {
            "option_text": row["option_text"],
            "is_correct": row["is_correct"],
        }

    provided = {answer.question_id: answer.selected_option_id for answer in payload.answers}
    expected_ids = set(questions)
    if set(provided) != expected_ids:
        raise ProductAPIError(
            422,
            "QUIZ_ANSWERS_INCOMPLETE",
            "Jawaban harus mencakup seluruh pertanyaan aktif pada modul.",
        )

    correct_answers = 0
    answer_rows: list[dict[str, Any]] = []
    snapshot: list[dict[str, Any]] = []
    for question_id, question in questions.items():
        selected_option_id = provided[question_id]
        selected = question["options"].get(selected_option_id)
        if selected is None:
            raise ProductAPIError(
                422,
                "QUIZ_OPTION_MISMATCH",
                "Pilihan jawaban tidak cocok dengan pertanyaan.",
            )
        is_correct = bool(selected["is_correct"])
        if is_correct:
            correct_answers += 1
        answer_rows.append(
            {
                "question_id": question_id,
                "selected_option_id": selected_option_id,
                "is_correct": is_correct,
            }
        )
        snapshot.append(
            {
                "question_id": str(question_id),
                "question_text": question["question_text"],
                "question_version": question["version"],
                "selected_option_id": str(selected_option_id),
                "selected_option_text": selected["option_text"],
                "is_correct": is_correct,
                "explanation": question["explanation"],
            }
        )

    total_questions = len(questions)
    score = round((correct_answers / total_questions) * 100, 2)
    return {
        "total_questions": total_questions,
        "correct_answers": correct_answers,
        "score": score,
        "answers": answer_rows,
        "snapshot": snapshot,
    }


def _attempt_result(row: DictRow) -> QuizAttemptResult:
    snapshot = row["question_snapshot"]
    feedback = [
        QuizQuestionFeedback(
            question_id=item["question_id"],
            selected_option_id=item["selected_option_id"],
            correct=item["is_correct"],
            explanation=item["explanation"],
        )
        for item in snapshot
    ]
    return QuizAttemptResult(
        attempt_id=row["id"],
        score=float(row["score"]),
        correct_answers=row["correct_answers"],
        total_questions=row["total_questions"],
        feedback=feedback,
    )


def _progress_item(row: DictRow) -> LearningProgressItem:
    updated_at = (
        row["latest_quiz_completed_at"]
        or row["last_lesson_completed_at"]
        or datetime.now(UTC)
    )
    return LearningProgressItem(
        module_id=row["module_id"],
        completed_lessons=row["completed_lessons"],
        total_lessons=row["total_lessons"],
        progress_percent=_progress_percent(row["completed_lessons"], row["total_lessons"]),
        latest_score=float(row["latest_score"]) if row["latest_score"] is not None else None,
        best_score=float(row["best_score"]) if row["best_score"] is not None else None,
        updated_at=_iso8601(updated_at),
    )


def _progress_percent(completed: int, total: int) -> float:
    if total <= 0:
        return 0.0
    return round((completed / total) * 100, 2)


def _iso8601(value: datetime) -> str:
    return value.astimezone(UTC).isoformat().replace("+00:00", "Z")
