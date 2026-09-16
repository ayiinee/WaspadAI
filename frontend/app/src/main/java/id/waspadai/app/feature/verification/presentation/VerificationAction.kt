package id.waspadai.app.feature.verification.presentation

sealed interface VerificationAction {
    data class InputChanged(val value: String) : VerificationAction

    data object SubmitText : VerificationAction

    data object RequestImageCapture : VerificationAction

    data object DismissFailure : VerificationAction
}
