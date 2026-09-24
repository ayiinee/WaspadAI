package id.waspadai.app.core.assistant

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.domain.ImageVerificationInput
import id.waspadai.app.feature.verification.domain.TextVerificationInput
import id.waspadai.app.feature.verification.domain.VerificationConversationDetail
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.VerificationRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantVerificationControllerTest {
    @Test
    fun `preview does not submit before explicit confirmation`() = runTest {
        val repository = FakeRepository()
        val controller = AssistantVerificationController(repository, this)

        controller.offerText(ExtractedAssistContext("Pesan transfer mencurigakan", null))
        advanceUntilIdle()

        assertEquals(0, repository.textRequests)
        assertTrue(controller.state.value.phase is AssistantSessionPhase.PreviewText)

        controller.confirmText()
        advanceUntilIdle()

        assertEquals(1, repository.textRequests)
        assertTrue(controller.state.value.phase is AssistantSessionPhase.Result)
    }

    @Test
    fun `selected image is reviewed before it is submitted`() = runTest {
        val repository = FakeRepository()
        val controller = AssistantVerificationController(repository, this)

        controller.selectImageArea(byteArrayOf(1, 2, 3, 4))
        advanceUntilIdle()

        assertEquals(0, repository.imageRequests)
        assertTrue(controller.state.value.phase is AssistantSessionPhase.ReviewImage)

        controller.confirmImage()
        advanceUntilIdle()

        assertEquals(1, repository.imageRequests)
        assertTrue(controller.state.value.phase is AssistantSessionPhase.Result)
    }

    @Test
    fun `clear closes session and removes conversation`() = runTest {
        val controller = AssistantVerificationController(FakeRepository(), this)
        controller.offerText(ExtractedAssistContext("Pesan transfer mencurigakan", null))
        controller.confirmText()
        advanceUntilIdle()

        controller.clear()

        assertTrue(controller.state.value.phase is AssistantSessionPhase.Closed)
        assertTrue(controller.state.value.conversation.isEmpty())
    }

    @Test
    fun `image handoff keeps a safe copy after assistant session is cleared`() = runTest {
        val controller = AssistantVerificationController(FakeRepository(), this)
        val selected = byteArrayOf(9, 8, 7, 6)
        controller.selectImageArea(selected)
        controller.confirmImage()
        advanceUntilIdle()

        val handoff = controller.snapshotForApp() as AssistantSessionHandoff.Image
        controller.clear()

        assertArrayEquals(byteArrayOf(9, 8, 7, 6), handoff.imageBytes)
        assertEquals(1, handoff.turns.size)
        assertTrue(handoff.turns.single().result != null)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), selected)
    }

    private class FakeRepository : VerificationRepository {
        var textRequests = 0
        var imageRequests = 0
        private val result = VerificationResult(
            narrative = "Jangan transfer sebelum mengonfirmasi.",
            riskLevel = RiskLevel.HIGH,
            reasons = emptyList(),
            recommendedActions = emptyList(),
        )

        override suspend fun submitText(input: TextVerificationInput): AppResult<VerificationResult> {
            textRequests += 1
            return AppResult.Success(result)
        }

        override suspend fun submitImage(input: ImageVerificationInput): AppResult<VerificationResult> {
            imageRequests += 1
            return AppResult.Success(result)
        }

        override suspend fun listHistory(): AppResult<List<VerificationHistoryItem>> =
            AppResult.Success(emptyList())

        override suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail> =
            AppResult.Failure("Tidak digunakan")

        override suspend fun listConversations(): AppResult<List<VerificationConversationSummary>> =
            AppResult.Success(emptyList())

        override suspend fun getConversationDetail(
            conversationId: String,
        ): AppResult<VerificationConversationDetail> = AppResult.Failure("Tidak digunakan")
    }
}
