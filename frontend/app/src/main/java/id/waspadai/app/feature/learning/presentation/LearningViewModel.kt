package id.waspadai.app.feature.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.learning.domain.LearningModuleDetail
import id.waspadai.app.feature.learning.domain.LearningModuleItem
import id.waspadai.app.feature.learning.domain.LearningQuiz
import id.waspadai.app.feature.learning.domain.LearningRepository
import id.waspadai.app.feature.learning.domain.QuizAttemptResult
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearningUiState(
    val modules: List<LearningModuleItem> = emptyList(),
    val selectedModule: LearningModuleDetail? = null,
    val quiz: LearningQuiz? = null,
    val answers: Map<String, String> = emptyMap(),
    val quizResult: QuizAttemptResult? = null,
    val loading: Boolean = true,
    val detailLoading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val accessToken: String = "",
)

sealed interface LearningAction {
    data object Refresh : LearningAction
    data class OpenModule(val moduleId: String) : LearningAction
    data object CloseModule : LearningAction
    data class CompleteLesson(val lessonId: String) : LearningAction
    data class SelectAnswer(val questionId: String, val optionId: String) : LearningAction
    data object SubmitQuiz : LearningAction
    data object DismissQuizResult : LearningAction
}

class LearningViewModel(
    private val repository: LearningRepository,
    private val accessTokenProvider: AccessTokenProvider,
    private val baseUrl: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    init { loadModules() }

    fun onAction(action: LearningAction) {
        when (action) {
            LearningAction.Refresh -> loadModules()
            is LearningAction.OpenModule -> openModule(action.moduleId)
            LearningAction.CloseModule -> _uiState.update {
                it.copy(selectedModule = null, quiz = null, answers = emptyMap(), quizResult = null, error = null)
            }
            is LearningAction.CompleteLesson -> completeLesson(action.lessonId)
            is LearningAction.SelectAnswer -> _uiState.update {
                it.copy(answers = it.answers + (action.questionId to action.optionId))
            }
            LearningAction.SubmitQuiz -> submitQuiz()
            LearningAction.DismissQuizResult -> _uiState.update { it.copy(quizResult = null) }
        }
    }

    private fun loadModules() = viewModelScope.launch {
        val token = accessTokenProvider.currentAccessToken()
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(loading = false, error = "Silakan masuk untuk membuka materi Pelajari.") }
            return@launch
        }
        _uiState.update { it.copy(loading = it.modules.isEmpty(), error = null, accessToken = token) }
        val modulesRequest = async { repository.loadModules(baseUrl, token) }
        val progressRequest = async { repository.loadProgress(baseUrl, token) }
        when (val result = modulesRequest.await()) {
            is AppResult.Success -> {
                val progress = (progressRequest.await() as? AppResult.Success)?.value.orEmpty().associateBy { it.moduleId }
                _uiState.update { state ->
                    state.copy(
                        modules = result.value.map { module ->
                            progress[module.moduleId]?.let {
                                module.copy(
                                    completedLessons = it.completedLessons,
                                    totalLessons = it.totalLessons,
                                    progressPercent = it.progressPercent,
                                    latestScore = it.latestScore,
                                    progressUpdatedAt = it.updatedAt,
                                    latestCorrectAnswers = it.latestCorrectAnswers,
                                    latestTotalQuestions = it.latestTotalQuestions,
                                )
                            } ?: module
                        },
                        loading = false,
                    )
                }
            }
            is AppResult.Failure -> _uiState.update { it.copy(loading = false, error = result.message) }
        }
    }

    private fun openModule(moduleId: String) = viewModelScope.launch {
        val token = accessTokenProvider.currentAccessToken() ?: return@launch
        _uiState.update { it.copy(detailLoading = true, error = null, answers = emptyMap(), quizResult = null) }
        val detail = async { repository.loadModuleDetail(baseUrl, token, moduleId) }
        val quiz = async { repository.loadQuiz(baseUrl, token, moduleId) }
        launch { repository.openModule(baseUrl, token, moduleId) }
        val detailResult = detail.await()
        val quizResult = quiz.await()
        if (detailResult is AppResult.Success) {
            _uiState.update {
                it.copy(
                    selectedModule = detailResult.value,
                    quiz = (quizResult as? AppResult.Success)?.value,
                    detailLoading = false,
                    error = (quizResult as? AppResult.Failure)?.message,
                )
            }
        } else {
            _uiState.update { it.copy(detailLoading = false, error = (detailResult as AppResult.Failure).message) }
        }
    }

    private fun completeLesson(lessonId: String) = viewModelScope.launch {
        val token = accessTokenProvider.currentAccessToken() ?: return@launch
        val module = _uiState.value.selectedModule ?: return@launch
        when (val result = repository.completeLesson(baseUrl, token, lessonId, UUID.randomUUID().toString())) {
            is AppResult.Success -> {
                _uiState.update { state ->
                    state.copy(
                        selectedModule = module.copy(
                            lessons = module.lessons.map { if (it.lessonId == lessonId) it.copy(completed = true) else it },
                            completedLessons = module.lessons.count { it.completed || it.lessonId == lessonId },
                            progressPercent = result.value.progressPercent,
                        ),
                        modules = state.modules.map {
                            if (it.moduleId == module.moduleId) it.copy(
                                completedLessons = module.lessons.count { lesson -> lesson.completed || lesson.lessonId == lessonId },
                                progressPercent = result.value.progressPercent,
                            ) else it
                        },
                    )
                }
            }
            is AppResult.Failure -> _uiState.update { it.copy(error = result.message) }
        }
    }

    private fun submitQuiz() = viewModelScope.launch {
        val token = accessTokenProvider.currentAccessToken() ?: return@launch
        val module = _uiState.value.selectedModule ?: return@launch
        val quiz = _uiState.value.quiz ?: return@launch
        if (_uiState.value.answers.size != quiz.questions.size) return@launch
        _uiState.update { it.copy(submitting = true, error = null) }
        when (val result = repository.submitQuiz(
            baseUrl, token, module.moduleId, UUID.randomUUID().toString(), _uiState.value.answers, module.version,
        )) {
            is AppResult.Success -> {
                _uiState.update { it.copy(submitting = false, quizResult = result.value) }
                loadModules()
            }
            is AppResult.Failure -> _uiState.update { it.copy(submitting = false, error = result.message) }
        }
    }

    class Factory(
        private val repository: LearningRepository,
        private val accessTokenProvider: AccessTokenProvider,
        private val baseUrl: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LearningViewModel(repository, accessTokenProvider, baseUrl) as T
    }
}
