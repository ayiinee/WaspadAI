package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.capture.OverlayChatTurn
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.core.trigger.VerificationPageContext
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.community.domain.CommunityPostStatus
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunitySnapshot
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.feature.community.domain.CommunityVoteCounts
import id.waspadai.app.feature.community.domain.CommunityVoteUpdate
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.data.StaticAccessTokenProvider
import id.waspadai.app.feature.verification.domain.ImageVerificationInput
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationsUseCase
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.verification.domain.TextVerificationInput
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.VerificationConversationDetail
import id.waspadai.app.feature.verification.domain.VerificationConversationPage
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.domain.VerificationConversationTurn
import id.waspadai.app.feature.verification.domain.VerificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `assistant image handoff restores attachment result and source`() {
        val viewModel = viewModel(FakeRepository())
        val result = VerificationResult(
            narrative = "Area ini berisiko.",
            riskLevel = RiskLevel.HIGH,
            reasons = emptyList(),
            recommendedActions = emptyList(),
        )

        viewModel.onAction(
            VerificationAction.OverlayConversationReady(
                imageBytes = byteArrayOf(1, 2, 3),
                contentType = "image/png",
                fileName = "waspadai-assistant.png",
                turns = listOf(OverlayChatTurn(false, result.narrative, result)),
                source = TriggerSource.ASSISTANT,
            )
        )

        val message = viewModel.state.value.conversation.first() as VerificationConversationItem.UserMessage
        assertEquals(TriggerSource.ASSISTANT, message.attachmentGroup.single().source)
        assertTrue(viewModel.state.value.conversation.last() is VerificationConversationItem.Analysis)
        assertTrue(viewModel.state.value.phase is VerificationPhase.Success)
    }

    @Test
    fun `assistant text handoff restores context result and page metadata`() {
        val viewModel = viewModel(FakeRepository())
        val pageContext = VerificationPageContext(title = "Pesan masuk", before = "Pengirim", after = "Tautan")
        val result = VerificationResult(
            narrative = "Pesan meminta OTP.",
            riskLevel = RiskLevel.HIGH,
            reasons = emptyList(),
            recommendedActions = emptyList(),
        )

        viewModel.onAction(
            VerificationAction.TextConversationReady(
                text = "Kirimkan kode OTP sekarang",
                sourceUrl = "https://example.test/message",
                pageContext = pageContext,
                turns = listOf(OverlayChatTurn(false, result.narrative, result)),
                source = TriggerSource.ASSISTANT,
            )
        )

        val message = viewModel.state.value.conversation.first() as VerificationConversationItem.UserMessage
        assertEquals("Kirimkan kode OTP sekarang", message.text)
        assertEquals(TriggerSource.ASSISTANT, viewModel.state.value.draftSource)
        assertEquals(pageContext, viewModel.state.value.draftPageContext)
        assertEquals("https://example.test/message", viewModel.state.value.draftSourceUrl)
        assertTrue(viewModel.state.value.phase is VerificationPhase.Success)
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
    fun `multiple picker result is appended atomically including duplicate files`() {
        val viewModel = viewModel(FakeRepository())
        val duplicate = VerificationAction.ImageSelected(
            imageBytes = byteArrayOf(1, 2, 3),
            contentType = "image/png",
            fileName = "bukti-sama.png",
        )

        viewModel.onAction(
            VerificationAction.AttachmentsSelected(listOf(duplicate, duplicate, duplicate))
        )

        assertEquals(3, viewModel.state.value.pendingAttachments.size)
        assertEquals(
            listOf("bukti-sama.png", "bukti-sama.png", "bukti-sama.png"),
            viewModel.state.value.pendingAttachments.map { it.fileName },
        )
    }

    @Test
    fun `multiple picker result only fills remaining attachment slots`() {
        val viewModel = viewModel(FakeRepository())
        val selections = (0 until 7).map { index ->
            VerificationAction.ImageSelected(
                imageBytes = byteArrayOf(index.toByte()),
                contentType = "image/png",
                fileName = "bukti-$index.png",
            )
        }

        viewModel.onAction(VerificationAction.AttachmentsSelected(selections))

        assertEquals(5, viewModel.state.value.pendingAttachments.size)
        assertTrue(viewModel.state.value.phase is VerificationPhase.Failure)
    }

    @Test
    fun `overlay switch stays off until confirmation and system consent succeed`() {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.RequestOverlayMode)

        assertTrue(!viewModel.state.value.isOverlayModeEnabled)
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
    fun `new conversation clears active chat and keeps saved rooms`() = runTest {
        val viewModel = viewModel(FakeRepository())

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.NewConversation)

        assertTrue(viewModel.state.value.conversation.isEmpty())
        assertTrue(viewModel.state.value.draft.isEmpty())
        assertEquals(1, viewModel.state.value.history.size)
        assertTrue(viewModel.state.value.isHistoryVisible)
        assertEquals(1, viewModel.state.value.composerFocusRequest)
    }

    @Test
    fun `noneligible result stays in session without creating an active conversation`() = runTest {
        val viewModel = viewModel(FakeRepository(resultConversationId = null))

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan ini sekarang"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.conversation.size)
        assertNull(viewModel.state.value.activeConversationId)
    }

    @Test
    fun `history pagination appends the next page`() = runTest {
        val viewModel = viewModel(FakeRepository(hasSecondPage = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.LoadMoreHistory)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("conversation-1", "conversation-2"), viewModel.state.value.history.map { it.conversationId })
        assertNull(viewModel.state.value.historyNextCursor)
    }

    @Test
    fun `rename updates drawer and active header`() = runTest {
        val viewModel = viewModel(FakeRepository())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.OpenConversation("conversation-1"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.StartRenameConversation("conversation-1"))
        viewModel.onAction(VerificationAction.RenameDraftChanged("  Judul   baru  "))
        viewModel.onAction(VerificationAction.ConfirmRenameConversation)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Judul baru", viewModel.state.value.history.single().title)
        assertEquals("Judul baru", viewModel.state.value.activeConversationTitle)
    }

    @Test
    fun `failed rename keeps previous title and inline edit for retry`() = runTest {
        val viewModel = viewModel(FakeRepository(mutationFails = true))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.StartRenameConversation("conversation-1"))
        viewModel.onAction(VerificationAction.RenameDraftChanged("Judul gagal"))
        viewModel.onAction(VerificationAction.ConfirmRenameConversation)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Pesan OTP", viewModel.state.value.history.single().title)
        assertEquals("conversation-1", viewModel.state.value.editingConversationId)
        assertEquals(VerificationAction.ConfirmRenameConversation, viewModel.state.value.uiMessageRetryAction)
    }

    @Test
    fun `delete active conversation returns to focused empty chat`() = runTest {
        val viewModel = viewModel(FakeRepository())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.OpenConversation("conversation-1"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.RequestDeleteConversation("conversation-1"))
        viewModel.onAction(VerificationAction.ConfirmDeleteConversation)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.history.isEmpty())
        assertTrue(viewModel.state.value.conversation.isEmpty())
        assertNull(viewModel.state.value.activeConversationId)
        assertEquals(1, viewModel.state.value.composerFocusRequest)
    }

    @Test
    fun `delete inactive conversation leaves current chat unchanged`() = runTest {
        val viewModel = viewModel(FakeRepository(hasSecondPage = true))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.LoadMoreHistory)
        viewModel.onAction(VerificationAction.OpenConversation("conversation-1"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.RequestDeleteConversation("conversation-2"))
        viewModel.onAction(VerificationAction.ConfirmDeleteConversation)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("conversation-1", viewModel.state.value.activeConversationId)
        assertEquals(2, viewModel.state.value.conversation.size)
        assertEquals(listOf("conversation-1"), viewModel.state.value.history.map { it.conversationId })
    }

    @Test
    fun `failed delete rolls back without clearing active chat`() = runTest {
        val viewModel = viewModel(FakeRepository(mutationFails = true))
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.OpenConversation("conversation-1"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(VerificationAction.RequestDeleteConversation("conversation-1"))
        viewModel.onAction(VerificationAction.ConfirmDeleteConversation)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("conversation-1", viewModel.state.value.activeConversationId)
        assertEquals(2, viewModel.state.value.conversation.size)
        assertEquals(1, viewModel.state.value.history.size)
        assertNull(viewModel.state.value.uiMessageRetryAction)
    }

    @Test
    fun `failed room load keeps the currently visible conversation`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        repository.detailFails = true

        viewModel.onAction(VerificationAction.OpenConversation("conversation-missing"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.conversation.size)
        assertEquals("conversation-1", viewModel.state.value.activeConversationId)
        assertEquals(
            VerificationAction.OpenConversation("conversation-missing"),
            viewModel.state.value.uiMessageRetryAction,
        )
    }

    @Test
    fun `follow up is submitted to the active conversation`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)

        viewModel.onAction(VerificationAction.PrepareNewConversation)
        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP pertama"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.InputChanged("Apa sumber untuk kesimpulan itu?"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(null, "conversation-1"), repository.submittedConversationIds)
        assertEquals("conversation-1", viewModel.state.value.activeConversationId)
        assertEquals(4, viewModel.state.value.conversation.size)
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
        viewModel.onAction(VerificationAction.CommunityCaptionChanged("Caption pengguna"))
        viewModel.onAction(VerificationAction.PublishCommunity)
        dispatcher.scheduler.advanceUntilIdle()

        val phase = viewModel.state.value.communityShare.phase
        assertTrue(phase is CommunitySharePhase.Published)
        assertEquals("community-1", (phase as CommunitySharePhase.Published).post.caseId)
        assertTrue(communityRepository.publishedWithRagConsent)
        assertEquals("Caption pengguna", communityRepository.publishedCaption)
    }

    @Test
    fun `community preview is not requested for a known risk result`() = runTest {
        val communityRepository = FakeCommunityRepository()
        val viewModel = viewModel(FakeRepository(RiskLevel.HIGH), communityRepository)

        viewModel.onAction(VerificationAction.InputChanged("Tolong cek pesan OTP ini"))
        viewModel.onAction(VerificationAction.SubmitText)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VerificationAction.RequestCommunityPreview)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, communityRepository.previewRequests)
        assertTrue(viewModel.state.value.communityShare.phase is CommunitySharePhase.Idle)
    }

    private fun viewModel(
        repository: VerificationRepository,
        communityRepository: CommunityRepository = FakeCommunityRepository(),
    ): VerificationViewModel =
        VerificationViewModel(
            submitTextVerification = SubmitTextVerificationUseCase(repository),
            submitImageVerification = SubmitImageVerificationUseCase(repository),
            loadHistory = LoadVerificationConversationsUseCase(repository),
            loadHistoryDetail = LoadVerificationConversationDetailUseCase(repository),
            requestCommunityPreview = RequestCommunityPreviewUseCase(communityRepository),
            publishCommunityCase = PublishCommunityCaseUseCase(communityRepository),
            communityBaseUrl = "https://api.example.test",
            accessTokenProvider = StaticAccessTokenProvider("test-token"),
            isRemoteEnabled = true
        )

    private class FakeRepository(
        riskLevel: RiskLevel = RiskLevel.UNKNOWN,
        private val resultConversationId: String? = "conversation-1",
        private val hasSecondPage: Boolean = false,
        private val mutationFails: Boolean = false,
    ) : VerificationRepository {
        var lastImageQuestion: String? = null
        val submittedConversationIds = mutableListOf<String?>()
        var detailFails: Boolean = false

        private val result = VerificationResult(
            narrative = "Jangan bagikan kode OTP.",
            headline = "Pesan OTP",
            riskLevel = riskLevel,
            reasons = listOf("Meminta kode OTP."),
            recommendedActions = listOf("Jangan kirim OTP."),
            caseId = "case-1",
            conversationId = resultConversationId,
            communityEligible = true,
            communityState = "PRIVATE",
        )

        override suspend fun submitText(input: TextVerificationInput): AppResult<VerificationResult> {
            submittedConversationIds += input.conversationId
            return AppResult.Success(
                result.copy(conversationId = input.conversationId ?: result.conversationId)
            )
        }

        override suspend fun submitImage(input: ImageVerificationInput): AppResult<VerificationResult> {
            lastImageQuestion = input.question
            return AppResult.Success(
                result.copy(conversationId = input.conversationId ?: result.conversationId)
            )
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

        override suspend fun listConversations(): AppResult<List<VerificationConversationSummary>> =
            AppResult.Success(listOf(summary("conversation-1", "Pesan OTP")))

        override suspend fun listConversationPage(
            cursor: String?,
        ): AppResult<VerificationConversationPage> = when {
            cursor == "next" -> AppResult.Success(
                VerificationConversationPage(listOf(summary("conversation-2", "Pesan kedua")), null)
            )
            else -> AppResult.Success(
                VerificationConversationPage(
                    listOf(summary("conversation-1", "Pesan OTP")),
                    if (hasSecondPage) "next" else null,
                )
            )
        }

        override suspend fun renameConversation(
            conversationId: String,
            title: String,
        ): AppResult<VerificationConversationSummary> = if (mutationFails) {
            AppResult.Failure("Rename gagal")
        } else {
            AppResult.Success(summary(conversationId, title))
        }

        override suspend fun deleteConversation(conversationId: String): AppResult<Unit> =
            if (mutationFails) AppResult.Failure("Delete gagal") else AppResult.Success(Unit)

        override suspend fun getConversationDetail(
            conversationId: String,
        ): AppResult<VerificationConversationDetail> = if (detailFails) {
            AppResult.Failure("Percakapan tidak ditemukan")
        } else AppResult.Success(
            VerificationConversationDetail(
                conversationId = conversationId,
                title = "Pesan OTP",
                createdAt = "2026-09-17T10:00:00Z",
                updatedAt = "2026-09-17T10:00:00Z",
                turns = listOf(
                    VerificationConversationTurn(
                        caseId = "case-1",
                        inputType = "TEXT",
                        inputText = "Pesan meminta OTP",
                        createdAt = "2026-09-17T10:00:00Z",
                        result = result,
                    )
                ),
            )
        )

        private fun summary(id: String, title: String) = VerificationConversationSummary(
            conversationId = id,
            title = title,
            latestMessagePreview = "Jangan bagikan kode OTP.",
            latestMessageRole = "ASSISTANT",
            lastVerdict = "UNVERIFIED",
            createdAt = "2026-09-17T10:00:00Z",
            updatedAt = "2026-09-17T10:00:00Z",
        )

    }

    private class FakeCommunityRepository : CommunityRepository {
        var publishedWithRagConsent: Boolean = false
        var publishedCaption: String? = null
        var previewRequests: Int = 0

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
        ): AppResult<CommunityPreview> {
            previewRequests += 1
            return AppResult.Success(
                CommunityPreview(
                    previewId = "preview-1",
                    expiresAt = "2026-09-18T12:00:00Z",
                    redactedText = "Pesan aman untuk preview.",
                    redactedImageUrl = null,
                    redactions = emptyList(),
                )
            )
        }

        override suspend fun publishCase(
            baseUrl: String,
            accessToken: String,
            caseId: String,
            previewId: String,
            ragReuseConsent: Boolean,
            caption: String,
        ): AppResult<CommunityFeedPost> =
            AppResult.Success(
                CommunityFeedPost(
                    caseId = "community-1",
                    historyCaseId = caseId,
                    creatorName = "Anda",
                    isOwner = true,
                    title = "Kasus",
                    redactedText = "Aman",
                    status = CommunityPostStatus.PublishedUnverified,
                    publishedAt = "2026-09-22T00:00:00Z",
                    counts = CommunityVoteCounts(0, 0, 0),
                    userVote = null,
                ).also {
                    publishedWithRagConsent = ragReuseConsent
                    publishedCaption = caption
                }
            )
    }
}
