package id.waspadai.app.feature.learning.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.learning.domain.*
import id.waspadai.app.feature.verification.data.StaticAccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads backend modules and records module open`() = runTest {
        val repository = FakeLearningRepository()
        val viewModel = LearningViewModel(repository, StaticAccessTokenProvider("token"), "https://api.test")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Backend module", viewModel.uiState.value.modules.single().title)

        viewModel.onAction(LearningAction.OpenModule("module-1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("module-1", repository.openedModule)
        assertEquals("Backend lesson", viewModel.uiState.value.selectedModule?.lessons?.single()?.title)
    }

    @Test fun `lesson completion and quiz result come from backend`() = runTest {
        val repository = FakeLearningRepository()
        val viewModel = LearningViewModel(repository, StaticAccessTokenProvider("token"), "https://api.test")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(LearningAction.OpenModule("module-1"))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(LearningAction.CompleteLesson("lesson-1"))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedModule!!.lessons.single().completed)

        viewModel.onAction(LearningAction.SelectAnswer("question-1", "option-1"))
        viewModel.onAction(
            LearningAction.SubmitQuiz(
                readingDurationSeconds = 125,
                quizDurationSeconds = 48,
            )
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(100.0, viewModel.uiState.value.quizResult?.score ?: 0.0, 0.0)
        assertEquals(mapOf("question-1" to "option-1"), repository.submittedAnswers)
        assertEquals(125L, repository.submittedReadingSeconds)
        assertEquals(48L, repository.submittedQuizSeconds)
    }

    @Test fun `practice opens first module in quiz mode and close clears it`() = runTest {
        val repository = FakeLearningRepository()
        val viewModel = LearningViewModel(repository, StaticAccessTokenProvider("token"), "https://api.test")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(LearningAction.OpenPractice)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("module-1", repository.openedModule)
        assertEquals("module-1", viewModel.uiState.value.selectedModule?.moduleId)
        assertTrue(viewModel.uiState.value.openQuizOnModuleLoad)
        assertTrue(viewModel.uiState.value.quiz != null)

        viewModel.onAction(LearningAction.CloseModule)
        assertTrue(!viewModel.uiState.value.openQuizOnModuleLoad)
    }
}

private class FakeLearningRepository : LearningRepository {
    var openedModule: String? = null
    var submittedAnswers: Map<String, String> = emptyMap()
    var submittedReadingSeconds: Long = 0
    var submittedQuizSeconds: Long = 0
    private val module = LearningModuleItem("module-1", "backend", "Backend module", "Summary", 1, 1, 1, 0, 0.0)
    private val detail = LearningModuleDetail(
        "module-1", "backend", "Backend module", "Summary", 1, 1, 1, 0, 0.0,
        listOf(LearningLesson("lesson-1", "Backend lesson", "Body", 5, 0, false)),
        "Topic", null, listOf(LearningCase("case-1", "Case", "Description", null)), emptyList(),
    )
    override suspend fun loadModules(baseUrl: String, accessToken: String) = AppResult.Success(listOf(module))
    override suspend fun loadModuleDetail(baseUrl: String, accessToken: String, moduleId: String) = AppResult.Success(detail)
    override suspend fun completeLesson(baseUrl: String, accessToken: String, lessonId: String, idempotencyKey: String) =
        AppResult.Success(LessonCompleteResult(lessonId, "module-1", true, "now", 100.0))
    override suspend fun loadQuiz(baseUrl: String, accessToken: String, moduleId: String) = AppResult.Success(
        LearningQuiz(moduleId, 1, listOf(QuizQuestion("question-1", "Question", listOf(QuizOption("option-1", "Answer")))))
    )
    override suspend fun submitQuiz(
        baseUrl: String,
        accessToken: String,
        moduleId: String,
        idempotencyKey: String,
        answers: Map<String, String>,
        moduleVersion: Int,
        readingDurationSeconds: Long,
        quizDurationSeconds: Long,
    ): AppResult<QuizAttemptResult> {
        submittedAnswers = answers
        submittedReadingSeconds = readingDurationSeconds
        submittedQuizSeconds = quizDurationSeconds
        return AppResult.Success(QuizAttemptResult("attempt-1", 100.0, 1, 1, emptyList()))
    }
    override suspend fun loadProgress(baseUrl: String, accessToken: String) = AppResult.Success(emptyList<LearningProgressItem>())
    override suspend fun openModule(baseUrl: String, accessToken: String, moduleId: String): AppResult<Unit> {
        openedModule = moduleId
        return AppResult.Success(Unit)
    }
    override suspend fun loadCases(baseUrl: String, accessToken: String, moduleId: String) = AppResult.Success(detail.cases)
}
