package id.waspadai.app.feature.learning.domain

import id.waspadai.app.core.common.AppResult

interface LearningRepository {
    suspend fun loadModules(
        baseUrl: String,
        accessToken: String,
    ): AppResult<List<LearningModuleItem>>

    suspend fun loadModuleDetail(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
    ): AppResult<LearningModuleDetail>

    suspend fun completeLesson(
        baseUrl: String,
        accessToken: String,
        lessonId: String,
        idempotencyKey: String,
    ): AppResult<LessonCompleteResult>

    suspend fun loadQuiz(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
    ): AppResult<LearningQuiz>

    suspend fun submitQuiz(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
        idempotencyKey: String,
        answers: Map<String, String>,
        moduleVersion: Int,
    ): AppResult<QuizAttemptResult>

    suspend fun loadProgress(
        baseUrl: String,
        accessToken: String,
    ): AppResult<List<LearningProgressItem>>

    suspend fun openModule(baseUrl: String, accessToken: String, moduleId: String): AppResult<Unit>

    suspend fun loadCases(baseUrl: String, accessToken: String, moduleId: String): AppResult<List<LearningCase>>
}

data class LearningModuleItem(
    val moduleId: String,
    val slug: String,
    val title: String,
    val summary: String,
    val difficulty: Int,
    val version: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val progressPercent: Double,
    val latestScore: Double? = null,
    val progressUpdatedAt: String? = null,
    val latestCorrectAnswers: Int? = null,
    val latestTotalQuestions: Int? = null,
)

data class LearningLesson(
    val lessonId: String,
    val title: String,
    val bodyMd: String,
    val durationMinutes: Int,
    val displayOrder: Int,
    val completed: Boolean,
)

data class LearningModuleDetail(
    val moduleId: String,
    val slug: String,
    val title: String,
    val summary: String,
    val difficulty: Int,
    val version: Int,
    val totalLessons: Int,
    val completedLessons: Int,
    val progressPercent: Double,
    val lessons: List<LearningLesson>,
    val topic: String?,
    val coverImageUrl: String?,
    val cases: List<LearningCase>,
    val media: List<LearningMedia>,
)

data class LearningCase(
    val caseId: String,
    val title: String,
    val description: String,
    val referenceUrl: String?,
)

data class LearningMedia(
    val mediaId: String,
    val mediaType: String,
    val url: String,
    val title: String,
    val altText: String,
)

data class LessonCompleteResult(
    val lessonId: String,
    val moduleId: String,
    val completed: Boolean,
    val completedAt: String,
    val progressPercent: Double,
)

data class QuizOption(
    val optionId: String,
    val text: String,
)

data class QuizQuestion(
    val questionId: String,
    val text: String,
    val options: List<QuizOption>,
)

data class LearningQuiz(
    val moduleId: String,
    val moduleVersion: Int,
    val questions: List<QuizQuestion>,
)

data class QuizQuestionFeedback(
    val questionId: String,
    val selectedOptionId: String,
    val correct: Boolean,
    val explanation: String,
    val correctOptionId: String? = null,
)

data class QuizAttemptResult(
    val attemptId: String,
    val score: Double,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val feedback: List<QuizQuestionFeedback>,
)

data class LearningProgressItem(
    val moduleId: String,
    val completedLessons: Int,
    val totalLessons: Int,
    val progressPercent: Double,
    val latestScore: Double?,
    val bestScore: Double?,
    val updatedAt: String,
    val latestCorrectAnswers: Int? = null,
    val latestTotalQuestions: Int? = null,
    val firstOpenedAt: String? = null,
    val lastOpenedAt: String? = null,
)

