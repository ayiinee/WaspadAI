package id.waspadai.app.feature.learning.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LearningModuleItemDto(
    @SerialName("module_id") val moduleId: String = "",
    val slug: String = "",
    val title: String = "",
    val summary: String = "",
    val difficulty: Int = 1,
    val version: Int = 1,
    @SerialName("total_lessons") val totalLessons: Int = 0,
    @SerialName("completed_lessons") val completedLessons: Int = 0,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
)

@Serializable
data class LearningLessonDto(
    @SerialName("lesson_id") val lessonId: String = "",
    val title: String = "",
    @SerialName("body_md") val bodyMd: String = "",
    @SerialName("duration_minutes") val durationMinutes: Int = 1,
    @SerialName("display_order") val displayOrder: Int = 0,
    val completed: Boolean = false,
)

@Serializable
data class LearningModuleDetailDto(
    @SerialName("module_id") val moduleId: String = "",
    val slug: String = "",
    val title: String = "",
    val summary: String = "",
    val difficulty: Int = 1,
    val version: Int = 1,
    @SerialName("total_lessons") val totalLessons: Int = 0,
    @SerialName("completed_lessons") val completedLessons: Int = 0,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
    val lessons: List<LearningLessonDto> = emptyList(),
    val topic: String? = null,
    @SerialName("cover_image_url") val coverImageUrl: String? = null,
    val cases: List<LearningCaseDto> = emptyList(),
    val media: List<LearningMediaDto> = emptyList(),
)

@Serializable
data class LearningCaseDto(
    @SerialName("case_id") val caseId: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("reference_url") val referenceUrl: String? = null,
)

@Serializable
data class LearningMediaDto(
    @SerialName("media_id") val mediaId: String = "",
    @SerialName("media_type") val mediaType: String = "IMAGE",
    val url: String = "",
    val title: String = "",
    @SerialName("alt_text") val altText: String = "",
)

@Serializable
data class LessonCompleteResponseDto(
    @SerialName("lesson_id") val lessonId: String = "",
    @SerialName("module_id") val moduleId: String = "",
    val completed: Boolean = true,
    @SerialName("completed_at") val completedAt: String = "",
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
)

@Serializable
data class QuizOptionDto(
    @SerialName("option_id") val optionId: String = "",
    val text: String = "",
)

@Serializable
data class QuizQuestionDto(
    @SerialName("question_id") val questionId: String = "",
    val text: String = "",
    val options: List<QuizOptionDto> = emptyList(),
)

@Serializable
data class LearningQuizDto(
    @SerialName("module_id") val moduleId: String = "",
    @SerialName("module_version") val moduleVersion: Int = 1,
    val questions: List<QuizQuestionDto> = emptyList(),
)

@Serializable
data class QuizAttemptAnswerDto(
    @SerialName("question_id") val questionId: String,
    @SerialName("selected_option_id") val selectedOptionId: String,
)

@Serializable
data class QuizAttemptRequestDto(
    @SerialName("module_version") val moduleVersion: Int,
    val answers: List<QuizAttemptAnswerDto>,
)

@Serializable
data class QuizQuestionFeedbackDto(
    @SerialName("question_id") val questionId: String = "",
    @SerialName("selected_option_id") val selectedOptionId: String = "",
    @SerialName("correct_option_id") val correctOptionId: String? = null,
    val correct: Boolean = false,
    val explanation: String = "",
)

@Serializable
data class QuizAttemptResultDto(
    @SerialName("attempt_id") val attemptId: String = "",
    val score: Double = 0.0,
    @SerialName("correct_answers") val correctAnswers: Int = 0,
    @SerialName("total_questions") val totalQuestions: Int = 0,
    val feedback: List<QuizQuestionFeedbackDto> = emptyList(),
)

@Serializable
data class LearningProgressItemDto(
    @SerialName("module_id") val moduleId: String = "",
    @SerialName("completed_lessons") val completedLessons: Int = 0,
    @SerialName("total_lessons") val totalLessons: Int = 0,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
    @SerialName("latest_score") val latestScore: Double? = null,
    @SerialName("best_score") val bestScore: Double? = null,
    @SerialName("latest_correct_answers") val latestCorrectAnswers: Int? = null,
    @SerialName("latest_total_questions") val latestTotalQuestions: Int? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("first_opened_at") val firstOpenedAt: String? = null,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
)

@Serializable
data class LearningProgressResponseDto(
    val items: List<LearningProgressItemDto> = emptyList(),
)

