package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.capture.OverlayChatTurn
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.core.trigger.VerificationPageContext

sealed interface VerificationAction {
    data class InputChanged(val value: String) : VerificationAction

    data class TextContextSelected(
        val text: String,
        val source: TriggerSource,
        val pageContext: VerificationPageContext? = null,
        val sourceUrl: String? = null,
    ) : VerificationAction

    data object SubmitText : VerificationAction

    data object RequestImageCapture : VerificationAction

    data class ImageSelected(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
        val source: TriggerSource = TriggerSource.IN_APP,
    ) : VerificationAction

    data class AttachmentsSelected(
        val attachments: List<ImageSelected>,
    ) : VerificationAction

    data object SubmitPendingImage : VerificationAction

    data object DismissImagePreview : VerificationAction

    data class RemovePendingAttachment(val index: Int) : VerificationAction

    data class ImageSelectionFailed(val message: String) : VerificationAction

    data object RequestOverlayMode : VerificationAction

    data object AcceptOverlayPrivacy : VerificationAction

    data object DismissOverlayPrivacy : VerificationAction

    data class OverlayPermissionResult(val granted: Boolean) : VerificationAction

    data class OverlayModeConsentResult(val granted: Boolean) : VerificationAction

    data class OverlayCaptureReady(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : VerificationAction

    data class OverlayConversationReady(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
        val turns: List<OverlayChatTurn>,
        val source: TriggerSource = TriggerSource.FLOATING_OVERLAY,
    ) : VerificationAction

    data class TextConversationReady(
        val text: String,
        val sourceUrl: String?,
        val pageContext: VerificationPageContext?,
        val turns: List<OverlayChatTurn>,
        val source: TriggerSource,
    ) : VerificationAction

    data class OverlayPermissionExpired(val message: String) : VerificationAction

    data object OverlayStopped : VerificationAction

    data object DismissFailure : VerificationAction

    data object ToggleHistory : VerificationAction

    data object RefreshHistory : VerificationAction

    data object LoadMoreHistory : VerificationAction

    data object OpenDrawer : VerificationAction

    data object CloseDrawer : VerificationAction

    data object NewConversation : VerificationAction

    data object PrepareNewConversation : VerificationAction

    data class OpenHistory(val caseId: String) : VerificationAction

    data class OpenConversation(val conversationId: String) : VerificationAction

    data class StartRenameConversation(val conversationId: String) : VerificationAction

    data class RenameDraftChanged(val value: String) : VerificationAction

    data object ConfirmRenameConversation : VerificationAction

    data object CancelRenameConversation : VerificationAction

    data class RequestDeleteConversation(val conversationId: String) : VerificationAction

    data object ConfirmDeleteConversation : VerificationAction

    data object CancelDeleteConversation : VerificationAction

    data object DismissUiMessage : VerificationAction

    data object RequestCommunityPreview : VerificationAction

    data class CommunityRagConsentChanged(val granted: Boolean) : VerificationAction

    data class CommunityCaptionChanged(val caption: String) : VerificationAction

    data object PublishCommunity : VerificationAction

    data object DismissCommunityShare : VerificationAction
}
