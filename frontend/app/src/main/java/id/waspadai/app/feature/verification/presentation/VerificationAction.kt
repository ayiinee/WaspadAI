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

    data object ToggleOverlayMode : VerificationAction

    data object DismissFailure : VerificationAction

    data object ToggleHistory : VerificationAction

    data object RefreshHistory : VerificationAction

    data class OpenHistory(val caseId: String) : VerificationAction
}
