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
import kotlinx.coroutines.Job
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
    private var conversationLoadJob: Job? = null
    private var verificationJob: Job? = null

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
            VerificationAction.LoadMoreHistory -> loadMoreHistory()
            VerificationAction.OpenDrawer -> _state.update { it.copy(isDrawerOpen = true) }
            VerificationAction.CloseDrawer -> _state.update { it.copy(isDrawerOpen = false) }
            VerificationAction.NewConversation -> newConversation()
            VerificationAction.PrepareNewConversation -> prepareNewConversation()
            is VerificationAction.OpenHistory -> openConversation(action.caseId)
            is VerificationAction.OpenConversation -> openConversation(action.conversationId)
            is VerificationAction.StartRenameConversation -> startRename(action.conversationId)
            is VerificationAction.RenameDraftChanged -> _state.update { it.copy(renameDraft = action.value.take(80)) }
            VerificationAction.ConfirmRenameConversation -> confirmRename()
            VerificationAction.CancelRenameConversation -> _state.update {
                it.copy(editingConversationId = null, renameDraft = "")
            }
            is VerificationAction.RequestDeleteConversation -> _state.update {
                it.copy(pendingDeleteConversationId = action.conversationId)
            }
            VerificationAction.ConfirmDeleteConversation -> confirmDelete()
            VerificationAction.CancelDeleteConversation -> _state.update {
                it.copy(pendingDeleteConversationId = null)
            }
            VerificationAction.DismissUiMessage -> _state.update {
                it.copy(uiMessage = null, uiMessageRetryAction = null)
            }
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
        verificationJob = viewModelScope.launch {
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
                        activeConversationTitle = if (
                            conversationId == null && result.value.conversationId != null
                        ) {
                            conversationTitle(text)
                        } else {
                            current.activeConversationTitle
                        },
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
        verificationJob = viewModelScope.launch {
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
                        activeConversationTitle = if (
                            conversationId == null && result.value.conversationId != null
                        ) {
                            conversationTitle(userMessage)
                        } else {
                            current.activeConversationTitle
                        },
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
        verificationJob = viewModelScope.launch {
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
                    val activeTitle = result.value.items
                        .firstOrNull { it.conversationId == current.activeConversationId }
                        ?.title
                    current.copy(
                        history = result.value.items,
                        historyNextCursor = result.value.nextCursor,
                        isHistoryLoading = false,
                        activeConversationTitle = activeTitle ?: current.activeConversationTitle,
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(
                        isHistoryLoading = false,
                        uiMessage = result.message,
                        uiMessageRetryAction = VerificationAction.RefreshHistory,
                    )
                }
            }
        }
    }

    private fun loadMoreHistory() {
        val cursor = state.value.historyNextCursor ?: return
        if (state.value.isHistoryLoadingMore) return
        _state.update { it.copy(isHistoryLoadingMore = true) }
        viewModelScope.launch {
            when (val result = loadHistory(cursor)) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        history = (current.history + result.value.items).distinctBy { it.conversationId },
                        historyNextCursor = result.value.nextCursor,
                        isHistoryLoadingMore = false,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isHistoryLoadingMore = false,
                        uiMessage = result.message,
                        uiMessageRetryAction = VerificationAction.LoadMoreHistory,
                    )
                }
            }
        }
    }

    private fun newConversation() {
        conversationLoadJob?.cancel()
        verificationJob?.cancel()
        _state.update { current ->
            current.copy(
                draft = "",
                draftSource = TriggerSource.IN_APP,
                draftPageContext = null,
                draftSourceUrl = null,
                conversation = emptyList(),
                pendingAttachments = emptyList(),
                phase = VerificationPhase.Idle,
                communityShare = CommunityShareState(),
                isHistoryVisible = true,
                isDrawerOpen = false,
                activeConversationId = null,
                activeConversationTitle = "Percakapan baru",
                isConversationLoading = false,
                editingConversationId = null,
                renameDraft = "",
                pendingDeleteConversationId = null,
                uiMessage = null,
                uiMessageRetryAction = null,
                composerFocusRequest = current.composerFocusRequest + 1,
            )
        }
    }

    private fun prepareNewConversation() {
        conversationLoadJob?.cancel()
        verificationJob?.cancel()
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
        conversationLoadJob?.cancel()
        verificationJob?.cancel()
        _state.update { current ->
            current.copy(
                isConversationLoading = true,
                phase = VerificationPhase.Idle,
                isDrawerOpen = false,
                uiMessage = null,
                uiMessageRetryAction = null,
            )
        }
        conversationLoadJob = viewModelScope.launch {
            when (val result = loadHistoryDetail(conversationId)) {
                is AppResult.Success -> {
                    var attachmentFailed = false
                    val restored = buildList<VerificationConversationItem> {
                        result.value.turns.forEach { turn ->
                            val attachmentBytes = if (turn.attachment?.available == true) {
                                when (val attachment = loadHistory.attachment(conversationId, turn.caseId)) {
                                    is AppResult.Success -> attachment.value
                                    is AppResult.Failure -> {
                                        attachmentFailed = true
                                        null
                                    }
                                }
                            } else null
                            add(
                                VerificationConversationItem.UserMessage(
                                    text = turn.inputText,
                                    hasAttachment = turn.inputType == "IMAGE",
                                    attachmentName = if (turn.inputType == "IMAGE") {
                                        "Lampiran gambar"
                                    } else {
                                        null
                                    },
                                    attachmentBytes = attachmentBytes,
                                    attachmentContentType = turn.attachment?.contentType,
                                )
                            )
                            add(VerificationConversationItem.Analysis(turn.result))
                        }
                    }
                    _state.update { current ->
                        current.copy(
                            conversation = restored,
                            draft = "",
                            pendingAttachments = emptyList(),
                            activeConversationId = result.value.conversationId,
                            activeConversationTitle = result.value.title,
                            isConversationLoading = false,
                            phase = VerificationPhase.Idle,
                            uiMessage = if (attachmentFailed) {
                                "Sebagian preview lampiran belum dapat dimuat."
                            } else {
                                null
                            },
                            uiMessageRetryAction = if (attachmentFailed) {
                                VerificationAction.OpenConversation(conversationId)
                            } else {
                                null
                            },
                        )
                    }
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(
                        isConversationLoading = false,
                        uiMessage = result.message,
                        uiMessageRetryAction = VerificationAction.OpenConversation(conversationId),
                    )
                }
            }
        }
    }

    private fun startRename(conversationId: String) {
        val title = state.value.history.firstOrNull { it.conversationId == conversationId }?.title ?: return
        _state.update { it.copy(editingConversationId = conversationId, renameDraft = title) }
    }

    private fun confirmRename() {
        val id = state.value.editingConversationId ?: return
        val title = state.value.renameDraft.trim().replace(Regex("\\s+"), " ")
        if (title.isBlank()) {
            _state.update { it.copy(uiMessage = "Judul percakapan tidak boleh kosong.") }
            return
        }
        _state.update { it.copy(isConversationMutationRunning = true) }
        viewModelScope.launch {
            when (val result = loadHistory.rename(id, title)) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        history = current.history.map { if (it.conversationId == id) result.value else it },
                        activeConversationTitle = if (current.activeConversationId == id) result.value.title else current.activeConversationTitle,
                        editingConversationId = null,
                        renameDraft = "",
                        isConversationMutationRunning = false,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isConversationMutationRunning = false,
                        uiMessage = result.message,
                        uiMessageRetryAction = VerificationAction.ConfirmRenameConversation,
                    )
                }
            }
        }
    }

    private fun confirmDelete() {
        val id = state.value.pendingDeleteConversationId ?: return
        if (state.value.activeConversationId == id) {
            verificationJob?.cancel()
            conversationLoadJob?.cancel()
        }
        _state.update { it.copy(isConversationMutationRunning = true) }
        viewModelScope.launch {
            when (val result = loadHistory.delete(id)) {
                is AppResult.Success -> _state.update { current ->
                    val deletingActive = current.activeConversationId == id
                    current.copy(
                        history = current.history.filterNot { it.conversationId == id },
                        pendingDeleteConversationId = null,
                        isConversationMutationRunning = false,
                        activeConversationId = if (deletingActive) null else current.activeConversationId,
                        activeConversationTitle = if (deletingActive) "Percakapan baru" else current.activeConversationTitle,
                        conversation = if (deletingActive) emptyList() else current.conversation,
                        draft = if (deletingActive) "" else current.draft,
                        draftSource = if (deletingActive) TriggerSource.IN_APP else current.draftSource,
                        draftPageContext = if (deletingActive) null else current.draftPageContext,
                        draftSourceUrl = if (deletingActive) null else current.draftSourceUrl,
                        pendingAttachments = if (deletingActive) emptyList() else current.pendingAttachments,
                        phase = if (deletingActive) VerificationPhase.Idle else current.phase,
                        communityShare = if (deletingActive) CommunityShareState() else current.communityShare,
                        composerFocusRequest = if (deletingActive) current.composerFocusRequest + 1 else current.composerFocusRequest,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(
                        isConversationMutationRunning = false,
                        uiMessage = result.message,
                        uiMessageRetryAction = VerificationAction.ConfirmDeleteConversation,
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

        fun conversationTitle(input: String): String = input
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(7)
            .joinToString(" ")
            .take(80)
            .ifBlank { "Pemeriksaan gambar" }
    }
}
