package id.waspadai.app.feature.verification.presentation

sealed interface VerificationAction {
    data class InputChanged(val value: String) : VerificationAction

    data object SubmitText : VerificationAction

    data object RequestImageCapture : VerificationAction

    data object DismissFailure : VerificationAction

    data object ToggleHistory : VerificationAction

    data object RefreshHistory : VerificationAction

    data class OpenHistory(val caseId: String) : VerificationAction
}
