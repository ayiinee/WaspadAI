package id.waspadai.app.feature.verification.presentation

sealed interface VerificationAction {
    data class InputChanged(val value: String) : VerificationAction

    data object SubmitText : VerificationAction

    data object RequestImageCapture : VerificationAction

    data class SubmitImage(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : VerificationAction

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

    data object OverlayStopped : VerificationAction

    data object SubmitOverlayCapture : VerificationAction

    data object DismissOverlayCapturePreview : VerificationAction

    data object DismissFailure : VerificationAction

    data object ToggleHistory : VerificationAction

    data object RefreshHistory : VerificationAction

    data class OpenHistory(val caseId: String) : VerificationAction
}
