package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.community.domain.CommunityPreview
import id.waspadai.app.feature.community.domain.CommunityFeedPost
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem

data class VerificationUiState(
    val draft: String = "",
    val conversation: List<VerificationConversationItem> = emptyList(),
    val history: List<VerificationHistoryItem> = emptyList(),
    val isHistoryVisible: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val phase: VerificationPhase = VerificationPhase.Idle,
    val isOverlayModeEnabled: Boolean = false,
    val isOverlayPrivacyDialogVisible: Boolean = false,
    val pendingAttachments: List<ImageVerificationPreview> = emptyList(),
    val isRemoteEnabled: Boolean = false,
    val communityShare: CommunityShareState = CommunityShareState(),
) {
    val pendingImagePreview: ImageVerificationPreview?
        get() = pendingAttachments.firstOrNull()

    companion object {
        fun initial(isRemoteEnabled: Boolean): VerificationUiState {
            return VerificationUiState(
                conversation = emptyList(),
                isRemoteEnabled = isRemoteEnabled,
            )
        }
    }
}

sealed interface VerificationPhase {
    data object Idle : VerificationPhase

    data object Validating : VerificationPhase

    data object Submitting : VerificationPhase

    data class Success(val result: VerificationResult) : VerificationPhase

    data class Failure(val message: String) : VerificationPhase
}

sealed interface VerificationConversationItem {
    data class UserMessage(
        val text: String,
        val hasAttachment: Boolean = false,
        val attachmentName: String? = null,
        val attachmentBytes: ByteArray? = null,
        val attachmentContentType: String? = null,
        val attachmentGroup: List<ImageVerificationPreview> = emptyList(),
    ) : VerificationConversationItem

    data class Analysis(val result: VerificationResult, val isSample: Boolean = false) : VerificationConversationItem
}

data class CommunityShareState(
    val phase: CommunitySharePhase = CommunitySharePhase.Idle,
    val ragReuseConsent: Boolean = false,
)

sealed interface CommunitySharePhase {
    data object Idle : CommunitySharePhase
    data object RequestingPreview : CommunitySharePhase
    data class PreviewReady(val preview: CommunityPreview) : CommunitySharePhase
    data object Publishing : CommunitySharePhase
    data class Published(val post: CommunityFeedPost) : CommunitySharePhase
    data class Failure(val message: String) : CommunitySharePhase
}

data class ImageVerificationPreview(
    val imageBytes: ByteArray,
    val contentType: String,
    val fileName: String,
    val overlayModeEnabled: Boolean = false,
)
