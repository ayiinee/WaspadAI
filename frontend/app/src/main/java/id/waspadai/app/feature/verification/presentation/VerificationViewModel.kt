package id.waspadai.app.feature.verification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.data.AccessTokenProvider
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryUseCase
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
    private val loadHistory: LoadVerificationHistoryUseCase,
    private val loadHistoryDetail: LoadVerificationHistoryDetailUseCase,
    private val requestCommunityPreview: RequestCommunityPreviewUseCase,
    private val publishCommunityCase: PublishCommunityCaseUseCase,
    private val communityBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
    isRemoteEnabled: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(VerificationUiState.initial(isRemoteEnabled))
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    fun onAction(action: VerificationAction) {
        when (action) {
            is VerificationAction.InputChanged -> updateInput(action.value)
            VerificationAction.SubmitText -> submitText()
            VerificationAction.RequestImageCapture -> Unit
            is VerificationAction.ImageSelected -> showImagePreview(action)
            VerificationAction.SubmitPendingImage -> submitPendingImage()
            VerificationAction.DismissImagePreview -> dismissImagePreview()
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
                    overlayModeEnabled = true,
                )
            )
            VerificationAction.OverlayStopped -> stopOverlayMode()
            VerificationAction.DismissFailure -> dismissFailure()
            VerificationAction.ToggleHistory -> toggleHistory()
            VerificationAction.RefreshHistory -> refreshHistory()
            is VerificationAction.OpenHistory -> openHistory(action.caseId)
            VerificationAction.RequestCommunityPreview -> requestCommunityPreview()
            is VerificationAction.CommunityRagConsentChanged -> _state.update {
                it.copy(communityShare = it.communityShare.copy(ragReuseConsent = action.granted))
            }
            VerificationAction.PublishCommunity -> publishCommunity()
            VerificationAction.DismissCommunityShare -> dismissCommunityShare()
        }
    }

    private fun updateInput(value: String) {
        _state.update { current -> current.copy(draft = value, phase = VerificationPhase.Idle) }
    }

    private fun submitText() {
        val text = state.value.draft.trim()
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
            when (val result = submitTextVerification(text)) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        draft = "",
                        conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                        phase = VerificationPhase.Success(result.value)
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    private fun submitImage(
        action: VerificationAction.ImageSelected,
        forceOverlayModeEnabled: Boolean? = null,
    ) {
        val question = state.value.draft.trim().takeIf(String::isNotBlank)
        val overlayModeEnabled = forceOverlayModeEnabled ?: state.value.isOverlayModeEnabled
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
                    overlayModeEnabled = overlayModeEnabled,
                )
            ) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        draft = "",
                        conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                        phase = VerificationPhase.Success(result.value)
                    )
                }
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
        _state.update { current ->
            current.copy(
                isOverlayModeEnabled = false,
                pendingImagePreview = ImageVerificationPreview(
                    imageBytes = action.imageBytes,
                    contentType = action.contentType,
                    fileName = action.fileName,
                    overlayModeEnabled = action.overlayModeEnabled,
                ),
                phase = VerificationPhase.Idle,
            )
        }
    }

    private fun stopOverlayMode() {
        _state.update { current ->
            current.copy(isOverlayModeEnabled = false, phase = VerificationPhase.Idle)
        }
    }

    private fun submitPendingImage() {
        val preview = state.value.pendingImagePreview ?: return
        _state.update { current -> current.copy(pendingImagePreview = null) }
        submitImage(
            VerificationAction.ImageSelected(
                imageBytes = preview.imageBytes,
                contentType = preview.contentType,
                fileName = preview.fileName,
                overlayModeEnabled = preview.overlayModeEnabled,
            ),
            forceOverlayModeEnabled = preview.overlayModeEnabled,
        )
    }

    private fun dismissImagePreview() {
        _state.update { current ->
            current.copy(pendingImagePreview = null, phase = VerificationPhase.Idle)
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

    private fun openHistory(caseId: String) {
        _state.update { current -> current.copy(phase = VerificationPhase.Validating) }
        viewModelScope.launch {
            when (val result = loadHistoryDetail(caseId)) {
                is AppResult.Success -> _state.update { current ->
                    val restored = buildList {
                        result.value.inputText?.takeIf(String::isNotBlank)?.let { text ->
                            add(VerificationConversationItem.UserMessage(text))
                        }
                        add(VerificationConversationItem.Analysis(result.value.result))
                    }
                    current.copy(
                        conversation = restored,
                        draft = "",
                        isHistoryVisible = false,
                        phase = VerificationPhase.Idle
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    private fun requestCommunityPreview() {
        val result = currentResult() ?: return
        val caseId = result.caseId ?: return
        if (!result.communityEligible || result.communityState != "PRIVATE") return
        _state.update {
            it.copy(
                communityShare = it.communityShare.copy(
                    phase = CommunitySharePhase.RequestingPreview,
                    ragReuseConsent = false,
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
                )
            ) {
                is AppResult.Success -> _state.update { current ->
                    val updatedResult = result.copy(
                        communityState = published.value.communityState,
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
        private val loadHistory: LoadVerificationHistoryUseCase,
        private val loadHistoryDetail: LoadVerificationHistoryDetailUseCase,
        private val requestCommunityPreview: RequestCommunityPreviewUseCase,
        private val publishCommunityCase: PublishCommunityCaseUseCase,
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
                communityBaseUrl,
                accessTokenProvider,
                isRemoteEnabled
            ) as T
        }
    }

    private companion object {
        const val MINIMUM_TEXT_LENGTH = 10
    }
}
