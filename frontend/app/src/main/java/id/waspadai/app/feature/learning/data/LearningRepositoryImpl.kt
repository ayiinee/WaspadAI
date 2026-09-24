package id.waspadai.app.feature.learning.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.learning.data.dto.LearningLessonDto
import id.waspadai.app.feature.learning.data.dto.LearningCaseDto
import id.waspadai.app.feature.learning.data.dto.LearningMediaDto
import id.waspadai.app.feature.learning.data.dto.LearningModuleDetailDto
import id.waspadai.app.feature.learning.data.dto.LearningModuleItemDto
import id.waspadai.app.feature.learning.data.dto.LearningProgressItemDto
import id.waspadai.app.feature.learning.data.dto.LearningProgressResponseDto
import id.waspadai.app.feature.learning.data.dto.LearningQuizDto
import id.waspadai.app.feature.learning.data.dto.LessonCompleteResponseDto
import id.waspadai.app.feature.learning.data.dto.QuizAttemptAnswerDto
import id.waspadai.app.feature.learning.data.dto.QuizAttemptRequestDto
import id.waspadai.app.feature.learning.data.dto.QuizAttemptResultDto
import id.waspadai.app.feature.learning.data.dto.QuizOptionDto
import id.waspadai.app.feature.learning.data.dto.QuizQuestionDto
import id.waspadai.app.feature.learning.data.dto.QuizQuestionFeedbackDto
import id.waspadai.app.feature.learning.domain.LearningLesson
import id.waspadai.app.feature.learning.domain.LearningCase
import id.waspadai.app.feature.learning.domain.LearningMedia
import id.waspadai.app.feature.learning.domain.LearningModuleDetail
import id.waspadai.app.feature.learning.domain.LearningModuleItem
import id.waspadai.app.feature.learning.domain.LearningProgressItem
import id.waspadai.app.feature.learning.domain.LearningQuiz
import id.waspadai.app.feature.learning.domain.LearningRepository
import id.waspadai.app.feature.learning.domain.LessonCompleteResult
import id.waspadai.app.feature.learning.domain.QuizAttemptResult
import id.waspadai.app.feature.learning.domain.QuizOption
import id.waspadai.app.feature.learning.domain.QuizQuestion
import id.waspadai.app.feature.learning.domain.QuizQuestionFeedback
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException

class LearningRepositoryImpl(
    private val client: HttpClient,
) : LearningRepository {

    override suspend fun loadModules(
        baseUrl: String,
        accessToken: String,
    ): AppResult<List<LearningModuleItem>> = runLearningRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/learning/modules") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<List<LearningModuleItemDto>>().map(LearningModuleItemDto::toDomain)
    }

    override suspend fun loadModuleDetail(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
    ): AppResult<LearningModuleDetail> = runLearningRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/learning/modules/$moduleId") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<LearningModuleDetailDto>().toDomain(baseUrl.normalized())
    }

    override suspend fun completeLesson(
        baseUrl: String,
        accessToken: String,
        lessonId: String,
        idempotencyKey: String,
    ): AppResult<LessonCompleteResult> = runLearningRequest {
        val response = client.post("${baseUrl.normalized()}/api/v1/learning/lessons/$lessonId/complete") {
            authorize(accessToken)
            headers {
                append("Idempotency-Key", idempotencyKey.trim())
            }
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<LessonCompleteResponseDto>().toDomain()
    }

    override suspend fun loadQuiz(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
    ): AppResult<LearningQuiz> = runLearningRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/learning/modules/$moduleId/quiz") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<LearningQuizDto>().toDomain()
    }

    override suspend fun submitQuiz(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
        idempotencyKey: String,
        answers: Map<String, String>,
        moduleVersion: Int,
    ): AppResult<QuizAttemptResult> = runLearningRequest {
        val payload = QuizAttemptRequestDto(
            moduleVersion = moduleVersion,
            answers = answers.map { (qId, optId) ->
                QuizAttemptAnswerDto(questionId = qId, selectedOptionId = optId)
            },
        )
        val response = client.post("${baseUrl.normalized()}/api/v1/learning/modules/$moduleId/quiz-attempts") {
            authorize(accessToken)
            headers {
                append("Idempotency-Key", idempotencyKey.trim())
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<QuizAttemptResultDto>().toDomain()
    }

    override suspend fun loadProgress(
        baseUrl: String,
        accessToken: String,
    ): AppResult<List<LearningProgressItem>> = runLearningRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/learning/progress") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) {
            throw LearningApiException(response.status)
        }
        response.body<LearningProgressResponseDto>().items.map(LearningProgressItemDto::toDomain)
    }

    override suspend fun openModule(baseUrl: String, accessToken: String, moduleId: String): AppResult<Unit> =
        runLearningRequest {
            val response = client.post("${baseUrl.normalized()}/api/v1/learning/modules/$moduleId/open") {
                authorize(accessToken)
            }
            if (!response.status.isSuccess()) throw LearningApiException(response.status)
        }

    override suspend fun loadCases(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
    ): AppResult<List<LearningCase>> = runLearningRequest {
        val response = client.get("${baseUrl.normalized()}/api/v1/learning/modules/$moduleId/cases") {
            authorize(accessToken)
        }
        if (!response.status.isSuccess()) throw LearningApiException(response.status)
        response.body<List<LearningCaseDto>>().map(LearningCaseDto::toDomain)
    }

    private suspend fun <T> runLearningRequest(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: LearningApiException) {
        AppResult.Failure(error.status.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Koneksi backend terlalu lama merespons.")
    } catch (error: IOException) {
        AppResult.Failure("Backend belum dapat dihubungi. Periksa jaringan dan konfigurasi URL.")
    } catch (error: Exception) {
        AppResult.Failure("Data Pelajari belum dapat dimuat.")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize(accessToken: String) {
        headers {
            append(HttpHeaders.Authorization, "Bearer ${accessToken.trim()}")
        }
        accept(ContentType.Application.Json)
    }
}

private fun String.normalized(): String = trim().trimEnd('/')

private fun LearningModuleItemDto.toDomain(): LearningModuleItem = LearningModuleItem(
    moduleId = moduleId,
    slug = slug,
    title = title,
    summary = summary,
    difficulty = difficulty,
    version = version,
    totalLessons = totalLessons,
    completedLessons = completedLessons,
    progressPercent = progressPercent,
)

private fun LearningLessonDto.toDomain(): LearningLesson = LearningLesson(
    lessonId = lessonId,
    title = title,
    bodyMd = bodyMd,
    durationMinutes = durationMinutes,
    displayOrder = displayOrder,
    completed = completed,
)

private fun LearningModuleDetailDto.toDomain(baseUrl: String): LearningModuleDetail = LearningModuleDetail(
    moduleId = moduleId,
    slug = slug,
    title = title,
    summary = summary,
    difficulty = difficulty,
    version = version,
    totalLessons = totalLessons,
    completedLessons = completedLessons,
    progressPercent = progressPercent,
    lessons = lessons.map(LearningLessonDto::toDomain),
    topic = topic,
    coverImageUrl = coverImageUrl,
    cases = cases.map(LearningCaseDto::toDomain),
    media = media.map { it.toDomain(baseUrl) },
)

private fun LearningCaseDto.toDomain(): LearningCase = LearningCase(caseId, title, description, referenceUrl)

private fun LearningMediaDto.toDomain(baseUrl: String): LearningMedia = LearningMedia(
    mediaId,
    mediaType,
    if (url.startsWith("/")) "$baseUrl$url" else url,
    title,
    altText,
)

private fun LessonCompleteResponseDto.toDomain(): LessonCompleteResult = LessonCompleteResult(
    lessonId = lessonId,
    moduleId = moduleId,
    completed = completed,
    completedAt = completedAt,
    progressPercent = progressPercent,
)

private fun QuizOptionDto.toDomain(): QuizOption = QuizOption(
    optionId = optionId,
    text = text,
)

private fun QuizQuestionDto.toDomain(): QuizQuestion = QuizQuestion(
    questionId = questionId,
    text = text,
    options = options.map(QuizOptionDto::toDomain),
)

private fun LearningQuizDto.toDomain(): LearningQuiz = LearningQuiz(
    moduleId = moduleId,
    moduleVersion = moduleVersion,
    questions = questions.map(QuizQuestionDto::toDomain),
)

private fun QuizQuestionFeedbackDto.toDomain(): QuizQuestionFeedback = QuizQuestionFeedback(
    questionId = questionId,
    selectedOptionId = selectedOptionId,
    correctOptionId = correctOptionId,
    correct = correct,
    explanation = explanation,
)

private fun QuizAttemptResultDto.toDomain(): QuizAttemptResult = QuizAttemptResult(
    attemptId = attemptId,
    score = score,
    correctAnswers = correctAnswers,
    totalQuestions = totalQuestions,
    feedback = feedback.map(QuizQuestionFeedbackDto::toDomain),
)

private fun LearningProgressItemDto.toDomain(): LearningProgressItem = LearningProgressItem(
    moduleId = moduleId,
    completedLessons = completedLessons,
    totalLessons = totalLessons,
    progressPercent = progressPercent,
    latestScore = latestScore,
    bestScore = bestScore,
    updatedAt = updatedAt,
    latestCorrectAnswers = latestCorrectAnswers,
    latestTotalQuestions = latestTotalQuestions,
    firstOpenedAt = firstOpenedAt,
    lastOpenedAt = lastOpenedAt,
)

private fun HttpStatusCode.toSafeMessage(): String = when (value) {
    401 -> "Token autentikasi tidak valid atau sudah kedaluwarsa."
    403 -> "Aksi ini tidak diizinkan untuk akun ini."
    404 -> "Modul, lesson, atau quiz tidak ditemukan."
    409 -> "Versi materi atau quiz telah diperbarui. Silakan muat ulang modul."
    422 -> "Jawaban kuis tidak valid atau tidak lengkap."
    in 500..599 -> "Layanan backend Pelajari sedang mengalami kendala."
    else -> "Request Pelajari ditolak oleh server."
}

private class LearningApiException(val status: HttpStatusCode) : RuntimeException()

