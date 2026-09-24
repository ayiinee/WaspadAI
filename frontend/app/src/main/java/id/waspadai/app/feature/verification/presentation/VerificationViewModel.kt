package id.waspadai.app.feature.verification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationsUseCase
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerificationViewModel(
    private val submitTextVerification: SubmitTextVerificationUseCase,
    private val submitImageVerification: SubmitImageVerificationUseCase,
    private val loadHistory: LoadVerificationConversationsUseCase,
    private val loadHistoryDetail: LoadVerificationConversationDetailUseCase,
    private val requestCommunityPreview: RequestCommunityPreviewUseCase,
    private val publishCommunityCase: PublishCommunityCaseUseCase,
    private val communityRepository: CommunityRepository? = null,
    private val communityBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
    isRemoteEnabled: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(VerificationUiState.initial(isRemoteEnabled))
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    init {
        refreshHistory()
    }

    fun onAction(action: VerificationAction) {
        when (action) {
            is VerificationAction.InputChanged -> updateInput(action.value)
            is VerificationAction.TextContextSelected -> selectTextContext(action)
            VerificationAction.SubmitText -> submitText()
            VerificationAction.RequestImageCapture -> Unit
            is VerificationAction.ImageSelected -> showImagePreview(action)
            is VerificationAction.AttachmentsSelected -> addAttachments(action.attachments)
            VerificationAction.SubmitPendingImage -> submitPendingImage()
            VerificationAction.DismissImagePreview -> dismissImagePreview()
            is VerificationAction.RemovePendingAttachment -> removePendingAttachment(action.index)
            is VerificationAction.ImageSelectionFailed -> showImageSelectionFailure(action.message)
            VerificationAction.RequestOverlayMode -> requestOverlayMode()
            VerificationAction.AcceptOverlayPrivacy -> acceptOverlayPrivacy()
            VerificationAction.DismissOverlayPrivacy -> dismissOverlayPrivacy()
            is VerificationAction.OverlayPermissionResult -> setOverlayPermissionResult(action.granted)
            is VerificationAction.OverlayModeConsentResult -> setOverlayModeFromConsent(action.granted)
            is VerificationAction.OverlayCaptureReady -> showImagePreview(
                VerificationAction.ImageSelected(
                    imageBytes = action.imageBytes,
                    contentType = action.contentType,
                    fileName = action.fileName,
                    source = TriggerSource.FLOATING_OVERLAY,
                )
            )
            is VerificationAction.OverlayConversationReady -> importOverlayConversation(action)
            is VerificationAction.TextConversationReady -> importTextConversation(action)
            is VerificationAction.OverlayPermissionExpired -> _state.update { current ->
                current.copy(
                    isOverlayModeEnabled = false,
                    isOverlayPrivacyDialogVisible = true,
                    phase = VerificationPhase.Failure(action.message),
                )
            }
            VerificationAction.OverlayStopped -> stopOverlayMode()
            VerificationAction.DismissFailure -> dismissFailure()
            VerificationAction.ToggleHistory -> toggleHistory()
            VerificationAction.RefreshHistory -> refreshHistory()
            VerificationAction.NewConversation -> newConversation()
            VerificationAction.PrepareNewConversation -> prepareNewConversation()
            is VerificationAction.OpenHistory -> openConversation(action.caseId)
            is VerificationAction.OpenConversation -> openConversation(action.conversationId)
            VerificationAction.RequestCommunityPreview -> requestCommunityPreview()
            is VerificationAction.CommunityRagConsentChanged -> _state.update {
                it.copy(communityShare = it.communityShare.copy(ragReuseConsent = action.granted))
            }
            is VerificationAction.CommunityCaptionChanged -> _state.update {
                it.copy(communityShare = it.communityShare.copy(caption = action.caption.take(5000)))
            }
            VerificationAction.PublishCommunity -> publishCommunity()
            VerificationAction.DismissCommunityShare -> dismissCommunityShare()
        }
    }

    private fun updateInput(value: String) {
        _state.update { current -> current.copy(draft = value, phase = VerificationPhase.Idle) }
    }

    private fun selectTextContext(action: VerificationAction.TextContextSelected) {
        _state.update { current ->
            current.copy(
                draft = action.text.take(25_000),
                draftSource = action.source,
                draftPageContext = action.pageContext,
                draftSourceUrl = action.sourceUrl,
                phase = VerificationPhase.Idle,
            )
        }
    }

    private fun importOverlayConversation(action: VerificationAction.OverlayConversationReady) {
        val attachment = ImageVerificationPreview(
            imageBytes = action.imageBytes,
            contentType = action.contentType,
            fileName = action.fileName,
            source = action.source,
        )
        val imported = buildList<VerificationConversationItem> {
            add(
                VerificationConversationItem.UserMessage(
                    text = "Area layar dikirim melalui Tanya Area.",
                    hasAttachment = true,
                    attachmentName = action.fileName,
                    attachmentBytes = action.imageBytes,
                    attachmentContentType = action.contentType,
                    attachmentGroup = listOf(attachment),
                )
            )
            action.turns.forEach { turn ->
                if (turn.isUser) {
                    add(VerificationConversationItem.UserMessage(turn.text))
                } else {
                    turn.result?.let { add(VerificationConversationItem.Analysis(it)) }
                }
            }
        }
        val latestResult = action.turns.lastOrNull { !it.isUser }?.result
        _state.update { current ->
            current.copy(
                conversation = current.conversation + imported,
                pendingAttachments = emptyList(),
                draft = "",
                phase = latestResult?.let(VerificationPhase::Success) ?: VerificationPhase.Idle,
            )
        }
        refreshHistory()
    }

    private fun importTextConversation(action: VerificationAction.TextConversationReady) {
        val imported = buildList<VerificationConversationItem> {
            add(VerificationConversationItem.UserMessage(action.text))
            action.turns.forEach { turn ->
                if (turn.isUser) {
                    add(VerificationConversationItem.UserMessage(turn.text))
                } else {
                    turn.result?.let { add(VerificationConversationItem.Analysis(it)) }
                }
            }
        }
        val latestResult = action.turns.lastOrNull { !it.isUser }?.result
        _state.update { current ->
            current.copy(
                conversation = current.conversation + imported,
                pendingAttachments = emptyList(),
                draft = "",
                draftSource = action.source,
                draftPageContext = action.pageContext,
                draftSourceUrl = action.sourceUrl,
                phase = latestResult?.let(VerificationPhase::Success) ?: VerificationPhase.Idle,
            )
        }
    }

    private fun submitText() {
        val text = state.value.draft.trim()
        val conversationId = state.value.activeConversationId
        if (text.length < MINIMUM_TEXT_LENGTH) {
            _state.update { current ->
                current.copy(phase = VerificationPhase.Failure("Masukkan minimal 10 karakter untuk diperiksa."))
            }
            return
        }
        _state.update { current -> current.copy(phase = VerificationPhase.Validating) }
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    conversation = current.conversation + VerificationConversationItem.UserMessage(text),
                    phase = VerificationPhase.Submitting
                )
            }
            when (
                val result = submitTextVerification(
                    text = text,
                    sourceUrl = state.value.draftSourceUrl,
                    pageContext = state.value.draftPageContext,
                    source = state.value.draftSource,
                    conversationId = conversationId,
                )
            ) {
                is AppResult.Success -> _state.update { current ->
                    communityRepository?.invalidateCommunityCache()
                    current.copy(
                        draft = "",
                        draftSource = TriggerSource.IN_APP,
                        draftPageContext = null,
                        draftSourceUrl = null,
                        conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                        phase = VerificationPhase.Success(result.value),
                        activeConversationId = result.value.conversationId ?: current.activeConversationId,
                        activeConversationTitle = current.activeConversationTitle
                            .takeUnless { it == "Percakapan baru" }
                            ?: result.value.headline.ifBlank { "Percakapan baru" },
                    )
                }.also { refreshHistory() }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    private fun submitImage(
        action: VerificationAction.ImageSelected,
        forceSource: TriggerSource? = null,
    ) {
        val question = state.value.draft.trim().takeIf(String::isNotBlank)
        val source = forceSource ?: action.source
        val conversationId = state.value.activeConversationId
        val userMessage = question ?: "Gambar dikirim untuk diperiksa."
        _state.update { current -> current.copy(phase = VerificationPhase.Validating) }
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    conversation = current.conversation + VerificationConversationItem.UserMessage(
                        text = userMessage,
                        hasAttachment = true,
                        attachmentName = action.fileName,
                        attachmentBytes = action.imageBytes,
                        attachmentContentType = action.contentType,
                        attachmentGroup = listOf(
                            ImageVerificationPreview(
                                imageBytes = action.imageBytes,
                                contentType = action.contentType,
                                fileName = action.fileName,
                                source = source,
                            )
                        ),
                    ),
                    phase = VerificationPhase.Submitting
                )
            }
            when (
                val result = submitImageVerification(
                    imageBytes = action.imageBytes,
                    contentType = action.contentType,
                    fileName = action.fileName,
                    question = question,
                    source = source,
                    conversationId = conversationId,
                )
            ) {
                is AppResult.Success -> _state.update { current ->
                    communityRepository?.invalidateCommunityCache()
                    current.copy(
                        draft = "",
                        draftSource = TriggerSource.IN_APP,
                        draftPageContext = null,
                        draftSourceUrl = null,
                        conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                        phase = VerificationPhase.Success(result.value),
                        activeConversationId = result.value.conversationId ?: current.activeConversationId,
                        activeConversationTitle = current.activeConversationTitle
                            .takeUnless { it == "Percakapan baru" }
                            ?: result.value.headline.ifBlank { "Percakapan baru" },
                    )
                }.also { refreshHistory() }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    private fun showImageSelectionFailure(message: String) {
        _state.update { current -> current.copy(phase = VerificationPhase.Failure(message)) }
    }

    private fun requestOverlayMode() {
        if (state.value.isOverlayModeEnabled) {
            stopOverlayMode()
        } else {
            _state.update { current ->
                current.copy(
                    isOverlayModeEnabled = true,
                    isOverlayPrivacyDialogVisible = true,
                    phase = VerificationPhase.Idle,
                )
            }
        }
    }

    private fun acceptOverlayPrivacy() {
        _state.update { current ->
            current.copy(isOverlayPrivacyDialogVisible = false, phase = VerificationPhase.Idle)
        }
    }

    private fun dismissOverlayPrivacy() {
        _state.update { current ->
            current.copy(isOverlayPrivacyDialogVisible = false, isOverlayModeEnabled = false)
        }
    }

    private fun setOverlayPermissionResult(granted: Boolean) {
        if (granted) {
            _state.update { current -> current.copy(phase = VerificationPhase.Idle) }
        } else {
            _state.update { current ->
                current.copy(
                    isOverlayModeEnabled = false,
                    phase = VerificationPhase.Failure("Izin tampil di atas aplikasi lain belum aktif. Aktifkan izin overlay lalu coba lagi.")
                )
            }
        }
    }

    private fun setOverlayModeFromConsent(granted: Boolean) {
        _state.update { current ->
            current.copy(
                isOverlayModeEnabled = granted,
                phase = if (granted) {
                    VerificationPhase.Idle
                } else {
                    VerificationPhase.Failure("Izin tangkapan layar dibatalkan. Mode overlay belum aktif.")
                },
            )
        }
    }

    private fun showImagePreview(action: VerificationAction.ImageSelected) {
        addAttachments(listOf(action))
    }

    private fun addAttachments(selections: List<VerificationAction.ImageSelected>) {
        if (selections.isEmpty()) return
        _state.update { current ->
            val availableSlots = MAXIMUM_PENDING_ATTACHMENTS - current.pendingAttachments.size
            if (availableSlots <= 0) {
                return@update current.copy(
                    phase = VerificationPhase.Failure("Maksimal 5 lampiran dapat ditambahkan dalam satu pesan."),
                )
            }
            val accepted = selections.take(availableSlots).map { selection ->
                ImageVerificationPreview(
                    imageBytes = selection.imageBytes,
                    contentType = selection.contentType,
                    fileName = selection.fileName,
                    source = selection.source,
                )
            }
            current.copy(
                isOverlayModeEnabled = false,
                pendingAttachments = current.pendingAttachments + accepted,
                phase = if (accepted.size < selections.size) {
                    VerificationPhase.Failure(
                        "${accepted.size} lampiran ditambahkan. Maksimal 5 lampiran dalam satu pesan."
                    )
                } else {
                    VerificationPhase.Idle
                },
            )
        }
    }

    private fun stopOverlayMode() {
        _state.update { current ->
            current.copy(isOverlayModeEnabled = false, phase = VerificationPhase.Idle)
        }
    }

    private fun submitPendingImage() {
        val attachments = state.value.pendingAttachments
        if (attachments.isEmpty()) return
        val question = state.value.draft.trim().takeIf(String::isNotBlank)
        _state.update { current ->
            current.copy(pendingAttachments = emptyList(), phase = VerificationPhase.Validating)
        }
        viewModelScope.launch {
            val userMessage = question ?: "Lampiran dikirim untuk diperiksa."
            _state.update { current ->
                current.copy(
                    conversation = current.conversation + VerificationConversationItem.UserMessage(
                        text = userMessage,
                        hasAttachment = true,
                        attachmentName = attachments.first().fileName,
                        attachmentBytes = attachments.first().imageBytes,
                        attachmentContentType = attachments.first().contentType,
                        attachmentGroup = attachments,
                    ),
                    phase = VerificationPhase.Submitting,
                )
            }
            attachments.forEachIndexed { index, attachment ->
                when (val result = submitImageVerification(
                    imageBytes = attachment.imageBytes,
                    contentType = attachment.contentType,
                    fileName = attachment.fileName,
                    question = question,
                    source = attachment.source,
                    conversationId = state.value.activeConversationId,
                )) {
                    is AppResult.Success -> _state.update { current ->
                        current.copy(
                            conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                            phase = if (index == attachments.lastIndex) {
                                VerificationPhase.Success(result.value)
                            } else {
                                VerificationPhase.Submitting
                            },
                            activeConversationId = result.value.conversationId
                                ?: current.activeConversationId,
                            activeConversationTitle = current.activeConversationTitle
                                .takeUnless { it == "Percakapan baru" }
                                ?: result.value.headline.ifBlank { "Percakapan baru" },
                        )
                    }
                    is AppResult.Failure -> {
                        _state.update { current -> current.copy(phase = VerificationPhase.Failure(result.message)) }
                        return@launch
                    }
                }
            }
            _state.update { current ->
                current.copy(
                    draft = "",
                    draftSource = TriggerSource.IN_APP,
                    draftPageContext = null,
                    draftSourceUrl = null,
                )
            }
            refreshHistory()
        }
    }

    private fun dismissImagePreview() {
        _state.update { current ->
            current.copy(pendingAttachments = emptyList(), phase = VerificationPhase.Idle)
        }
    }

    private fun removePendingAttachment(index: Int) {
        _state.update { current ->
            current.copy(
                pendingAttachments = current.pendingAttachments.filterIndexed { itemIndex, _ ->
                    itemIndex != index
                },
                phase = VerificationPhase.Idle,
            )
        }
    }

    private fun dismissFailure() {
        _state.update { current -> current.copy(phase = VerificationPhase.Idle) }
    }

    private fun toggleHistory() {
        val shouldLoad = !state.value.isHistoryVisible && state.value.history.isEmpty()
        _state.update { current -> current.copy(isHistoryVisible = !current.isHistoryVisible) }
        if (shouldLoad) {
            refreshHistory()
        }
    }

    private fun refreshHistory() {
        if (state.value.isHistoryLoading) return
        _state.update { current -> current.copy(isHistoryLoading = true, phase = VerificationPhase.Idle) }
        viewModelScope.launch {
            when (val result = loadHistory()) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(history = result.value, isHistoryLoading = false)
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(
                        isHistoryLoading = false,
                        phase = VerificationPhase.Failure(result.message)
                    )
                }
            }
        }
    }

    private fun newConversation() {
        _state.update { current ->
            current.copy(
                draft = "",
                conversation = emptyList(),
                pendingAttachments = emptyList(),
                phase = VerificationPhase.Idle,
                communityShare = CommunityShareState(),
                isHistoryVisible = true,
                activeConversationId = null,
                activeConversationTitle = "Percakapan baru",
                isConversationLoading = false,
            )
        }
    }

    private fun prepareNewConversation() {
        _state.update { current ->
            current.copy(
                conversation = emptyList(),
                phase = VerificationPhase.Idle,
                communityShare = CommunityShareState(),
                activeConversationId = null,
                activeConversationTitle = "Percakapan baru",
                isConversationLoading = false,
            )
        }
    }

    private fun openConversation(conversationId: String) {
        _state.update { current ->
            current.copy(
                conversation = emptyList(),
                draft = "",
                pendingAttachments = emptyList(),
                activeConversationId = conversationId,
                activeConversationTitle = current.history
                    .firstOrNull { it.conversationId == conversationId }
                    ?.title
                    ?: "Percakapan",
                isConversationLoading = true,
                phase = VerificationPhase.Idle,
            )
        }
        viewModelScope.launch {
            when (val result = loadHistoryDetail(conversationId)) {
                is AppResult.Success -> _state.update { current ->
                    val restored = buildList<VerificationConversationItem> {
                        result.value.turns.forEach { turn ->
                            add(
                                VerificationConversationItem.UserMessage(
                                    text = turn.inputText,
                                    hasAttachment = turn.inputType == "IMAGE",
                                    attachmentName = if (turn.inputType == "IMAGE") {
                                        "Lampiran gambar"
                                    } else {
                                        null
                                    },
                                )
                            )
                            add(VerificationConversationItem.Analysis(turn.result))
                        }
                    }
                    current.copy(
                        conversation = restored,
                        draft = "",
                        activeConversationId = result.value.conversationId,
                        activeConversationTitle = result.value.title,
                        isConversationLoading = false,
                        phase = VerificationPhase.Idle,
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(
                        isConversationLoading = false,
                        phase = VerificationPhase.Failure(result.message),
                    )
                }
            }
        }
    }

    private fun requestCommunityPreview() {
        val result = currentResult() ?: return
        val caseId = result.caseId ?: return
        if (
            !result.communityEligible ||
            result.communityState != "PRIVATE" ||
            result.riskLevel != id.waspadai.app.core.model.RiskLevel.UNKNOWN
        ) return
        _state.update {
            it.copy(
                communityShare = it.communityShare.copy(
                    phase = CommunitySharePhase.RequestingPreview,
                    ragReuseConsent = false,
                    caption = "",
                )
            )
        }
        viewModelScope.launch {
            val token = accessTokenProvider.currentAccessToken()
            if (token.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        communityShare = it.communityShare.copy(
                            phase = CommunitySharePhase.Failure(
                                "Sesi Supabase belum tersedia. Login ulang lalu coba lagi."
                            )
                        )
                    )
                }
                return@launch
            }
            when (
                val result = requestCommunityPreview(
                    baseUrl = communityBaseUrl,
                    accessToken = token,
                    caseId = caseId,
                )
            ) {
                is AppResult.Success -> _state.update {
                    // Preview membuat state komunitas terkait kasus berubah di backend.
                    communityRepository?.invalidateCommunityCache()
                    it.copy(
                        communityShare = it.communityShare.copy(
                            phase = CommunitySharePhase.PreviewReady(result.value)
                        )
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        communityShare = it.communityShare.copy(
                            phase = CommunitySharePhase.Failure(result.message)
                        )
                    )
                }
            }
        }
    }

    private fun publishCommunity() {
        val result = currentResult() ?: return
        val caseId = result.caseId ?: return
        val preview = (state.value.communityShare.phase as? CommunitySharePhase.PreviewReady)?.preview
            ?: return
        val caption = state.value.communityShare.caption.trim()
        if (caption.isEmpty()) {
            _state.update {
                it.copy(
                    communityShare = it.communityShare.copy(
                        phase = CommunitySharePhase.Failure("Caption wajib diisi sebelum publikasi.")
                    )
                )
            }
            return
        }
        _state.update {
            it.copy(communityShare = it.communityShare.copy(phase = CommunitySharePhase.Publishing))
        }
        viewModelScope.launch {
            val token = accessTokenProvider.currentAccessToken()
            if (token.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        communityShare = it.communityShare.copy(
                            phase = CommunitySharePhase.Failure(
                                "Sesi Supabase belum tersedia. Login ulang lalu coba lagi."
                            )
                        )
                    )
                }
                return@launch
            }
            when (
                val published = publishCommunityCase(
                    baseUrl = communityBaseUrl,
                    accessToken = token,
                    caseId = caseId,
                    previewId = preview.previewId,
                    ragReuseConsent = state.value.communityShare.ragReuseConsent,
                    caption = caption,
                )
            ) {
                is AppResult.Success -> _state.update { current ->
                    val updatedResult = result.copy(
                        communityState = "PUBLISHED_UNVERIFIED",
                        communityEligible = false,
                    )
                    current.copy(
                        conversation = current.conversation.map { item ->
                            if (item is VerificationConversationItem.Analysis && item.result.caseId == caseId) {
                                item.copy(result = updatedResult)
                            } else {
                                item
                            }
                        },
                        communityShare = current.communityShare.copy(
                            phase = CommunitySharePhase.Published(published.value)
                        )
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        communityShare = it.communityShare.copy(
                            phase = CommunitySharePhase.Failure(published.message)
                        )
                    )
                }
            }
        }
    }

    private fun dismissCommunityShare() {
        _state.update {
            it.copy(communityShare = CommunityShareState())
        }
    }

    private fun currentResult(): id.waspadai.app.core.model.VerificationResult? =
        state.value.conversation.asReversed()
            .filterIsInstance<VerificationConversationItem.Analysis>()
            .firstOrNull()
            ?.result

    class Factory(
        private val submitTextVerification: SubmitTextVerificationUseCase,
        private val submitImageVerification: SubmitImageVerificationUseCase,
        private val loadHistory: LoadVerificationConversationsUseCase,
        private val loadHistoryDetail: LoadVerificationConversationDetailUseCase,
        private val requestCommunityPreview: RequestCommunityPreviewUseCase,
        private val publishCommunityCase: PublishCommunityCaseUseCase,
        private val communityRepository: CommunityRepository? = null,
        private val communityBaseUrl: String,
        private val accessTokenProvider: AccessTokenProvider,
        private val isRemoteEnabled: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(VerificationViewModel::class.java))
            return VerificationViewModel(
                submitTextVerification,
                submitImageVerification,
                loadHistory,
                loadHistoryDetail,
                requestCommunityPreview,
                publishCommunityCase,
                communityRepository,
                communityBaseUrl,
                accessTokenProvider,
                isRemoteEnabled
            ) as T
        }
    }

    private companion object {
        const val MINIMUM_TEXT_LENGTH = 10
        const val MAXIMUM_PENDING_ATTACHMENTS = 5
    }
}
