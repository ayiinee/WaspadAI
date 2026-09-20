package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityState
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.data.StaticAccessTokenProvider
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
    fun `image waits for custom message before submitting`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(
            VerificationAction.ImageSelected(
                imageBytes = byteArrayOf(1, 2, 3),
                contentType = "image/png",
                fileName = "screenshot.png",
            )
        )

        assertTrue(viewModel.state.value.pendingImagePreview != null)
        assertEquals(0, viewModel.state.value.conversation.size)

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek klaim pada gambar ini"))
        viewModel.onAction(VerificationAction.SubmitPendingImage)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Tolong cek klaim pada gambar ini", repository.lastImageQuestion)
        assertEquals(null, viewModel.state.value.pendingImagePreview)
        assertEquals(2, viewModel.state.value.conversation.size)
        val userMessage = viewModel.state.value.conversation.first() as VerificationConversationItem.UserMessage
        assertTrue(userMessage.attachmentBytes?.contentEquals(byteArrayOf(1, 2, 3)) == true)
        assertEquals("image/png", userMessage.attachmentContentType)
    }

    @Test
    fun `draft accepts up to five attachments and keeps their order`() {
        val viewModel = viewModel(FakeRepository())

        repeat(5) { index ->
            viewModel.onAction(
                VerificationAction.ImageSelected(
                    imageBytes = byteArrayOf(index.toByte()),
                    contentType = "image/png",
                    fileName = "bukti-$index.png",
                )
            )
        }

        assertEquals(5, viewModel.state.value.pendingAttachments.size)
        assertEquals("bukti-0.png", viewModel.state.value.pendingAttachments.first().fileName)
        assertEquals("bukti-4.png", viewModel.state.value.pendingAttachments.last().fileName)
    }

    @Test
    fun `overlay switch is active while confirmation is visible and resets when cancelled`() {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.RequestOverlayMode)

        assertTrue(viewModel.state.value.isOverlayModeEnabled)
        assertTrue(viewModel.state.value.isOverlayPrivacyDialogVisible)

        viewModel.onAction(VerificationAction.DismissOverlayPrivacy)

        assertTrue(!viewModel.state.value.isOverlayModeEnabled)
        assertTrue(!viewModel.state.value.isOverlayPrivacyDialogVisible)
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

    @Test
    fun `community sharing requests preview and publishes after consent`() = runTest {
        val communityRepository = FakeCommunityRepository()
        val viewModel = viewModel(FakeRepository(), communityRepository)

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.RequestCommunityPreview)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.communityShare.phase is CommunitySharePhase.PreviewReady)
        viewModel.onAction(VerificationAction.CommunityRagConsentChanged(true))
        viewModel.onAction(VerificationAction.PublishCommunity)
        dispatcher.scheduler.advanceUntilIdle()

        val phase = viewModel.state.value.communityShare.phase
        assertTrue(phase is CommunitySharePhase.Published)
        assertEquals("PUBLISHED_UNVERIFIED", (phase as CommunitySharePhase.Published).state.communityState)
        assertTrue(communityRepository.publishedWithRagConsent)
    }

    private fun viewModel(
        repository: VerificationRepository,
        communityRepository: CommunityRepository = FakeCommunityRepository(),
    ): VerificationViewModel =
        VerificationViewModel(
            submitTextVerification = SubmitTextVerificationUseCase(repository),
            submitImageVerification = SubmitImageVerificationUseCase(repository),
            loadHistory = LoadVerificationHistoryUseCase(repository),
            loadHistoryDetail = LoadVerificationHistoryDetailUseCase(repository),
            requestCommunityPreview = RequestCommunityPreviewUseCase(communityRepository),
            publishCommunityCase = PublishCommunityCaseUseCase(communityRepository),
            communityBaseUrl = "https://api.example.test",
            accessTokenProvider = StaticAccessTokenProvider("test-token"),
            isRemoteEnabled = true
        )

    private class FakeRepository : VerificationRepository {
        var lastImageQuestion: String? = null

        private val result = VerificationResult(
            narrative = "Jangan bagikan kode OTP.",
            riskLevel = RiskLevel.HIGH,
            reasons = listOf("Meminta kode OTP."),
            recommendedActions = listOf("Jangan kirim OTP."),
            caseId = "case-1",
            communityEligible = true,
            communityState = "PRIVATE",
        )

        override suspend fun submitText(text: String): AppResult<VerificationResult> =
            AppResult.Success(result)

        override suspend fun submitImage(
            imageBytes: ByteArray,
            contentType: String,
            fileName: String,
            question: String?,
            overlayModeEnabled: Boolean,
        ): AppResult<VerificationResult> {
            lastImageQuestion = question
            return AppResult.Success(result)
        }

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

    private class FakeCommunityRepository : CommunityRepository {
        var publishedWithRagConsent: Boolean = false

        override suspend fun loadCommunity(
            baseUrl: String,
            accessToken: String,
            forceRefresh: Boolean,
        ): AppResult<CommunitySnapshot> = AppResult.Failure("not used")

        override suspend fun castVote(
            baseUrl: String,
            accessToken: String,
            caseId: String,
            vote: CommunityVote,
        ): AppResult<CommunityVoteUpdate> = AppResult.Failure("not used")

        override suspend fun removeVote(
            baseUrl: String,
            accessToken: String,
            caseId: String,
        ): AppResult<CommunityVoteUpdate> = AppResult.Failure("not used")

        override suspend fun requestPreview(
            baseUrl: String,
            accessToken: String,
            caseId: String,
        ): AppResult<CommunityPreview> =
            AppResult.Success(
                CommunityPreview(
                    previewId = "preview-1",
                    expiresAt = "2026-09-18T12:00:00Z",
                    redactedText = "Pesan aman untuk preview.",
                    redactedImageUrl = null,
                    redactions = emptyList(),
                )
            )

        override suspend fun publishCase(
            baseUrl: String,
            accessToken: String,
            caseId: String,
            previewId: String,
            ragReuseConsent: Boolean,
        ): AppResult<CommunityState> =
            AppResult.Success(
                CommunityState(
                    caseId = caseId,
                    communityState = "PUBLISHED_UNVERIFIED",
                    revision = 2,
                ).also {
                    publishedWithRagConsent = ragReuseConsent
                }
            )
    }
}
