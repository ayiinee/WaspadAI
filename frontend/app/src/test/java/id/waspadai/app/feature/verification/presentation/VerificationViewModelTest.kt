package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryUseCase
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.VerificationRepository
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
class VerificationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial conversation is empty`() {
        val viewModel = viewModel(FakeRepository())

        assertEquals(emptyList<VerificationConversationItem>(), viewModel.state.value.conversation)
    }

    @Test
    fun `submit success adds user message and analysis`() = runTest {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.conversation.size)
        assertTrue(viewModel.state.value.conversation[0] is VerificationConversationItem.UserMessage)
        assertTrue(viewModel.state.value.conversation[1] is VerificationConversationItem.Analysis)
    }

    @Test
    fun `loading history does not replace active conversation`() = runTest {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.RefreshHistory)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.conversation.size)
        assertEquals(1, viewModel.state.value.history.size)
    }

    @Test
    fun `opening history restores chat from detail`() = runTest {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.OpenHistory("case-1"))
        dispatcher.scheduler.advanceUntilIdle()

        val conversation = viewModel.state.value.conversation
        assertEquals(2, conversation.size)
        assertEquals(
            "Pesan meminta OTP",
            (conversation[0] as VerificationConversationItem.UserMessage).text
        )
    }

    private fun viewModel(repository: VerificationRepository): VerificationViewModel =
        VerificationViewModel(
            submitTextVerification = SubmitTextVerificationUseCase(repository),
            submitImageVerification = SubmitImageVerificationUseCase(repository),
            loadHistory = LoadVerificationHistoryUseCase(repository),
            loadHistoryDetail = LoadVerificationHistoryDetailUseCase(repository),
            isRemoteEnabled = true
        )

    private class FakeRepository : VerificationRepository {
        private val result = VerificationResult(
            narrative = "Jangan bagikan kode OTP.",
            riskLevel = RiskLevel.HIGH,
            reasons = listOf("Meminta kode OTP."),
            recommendedActions = listOf("Jangan kirim OTP.")
        )

        override suspend fun submitText(text: String): AppResult<VerificationResult> =
            AppResult.Success(result)

        override suspend fun submitImage(
            imageBytes: ByteArray,
            contentType: String,
            fileName: String,
            question: String?,
            overlayModeEnabled: Boolean,
        ): AppResult<VerificationResult> = AppResult.Success(result)

        override suspend fun listHistory(): AppResult<List<VerificationHistoryItem>> =
            AppResult.Success(
                listOf(
                    VerificationHistoryItem(
                        caseId = "case-1",
                        headline = "Pesan OTP",
                        verdict = "UNVERIFIED",
                        createdAt = "2026-09-17T10:00:00Z"
                    )
                )
            )

        override suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail> =
            AppResult.Success(
                VerificationHistoryDetail(
                    caseId = caseId,
                    inputText = "Pesan meminta OTP",
                    result = result
                )
            )
    }
}
