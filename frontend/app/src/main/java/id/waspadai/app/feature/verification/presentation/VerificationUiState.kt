package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult

data class VerificationUiState(
    val draft: String = "",
    val conversation: List<VerificationConversationItem> = previewConversation(),
    val phase: VerificationPhase = VerificationPhase.Idle,
    val isRemoteEnabled: Boolean = false
) {
    companion object {
        fun initial(isRemoteEnabled: Boolean): VerificationUiState {
            return VerificationUiState(
                conversation = if (isRemoteEnabled) emptyList() else previewConversation(),
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
    data class UserMessage(val text: String, val hasAttachment: Boolean = false) : VerificationConversationItem

    data class Analysis(val result: VerificationResult, val isSample: Boolean = false) : VerificationConversationItem
}

private fun previewConversation(): List<VerificationConversationItem> = listOf(
    VerificationConversationItem.UserMessage(
        text = "Aku baru dapat chat seperti ini di WhatsApp.",
        hasAttachment = true
    ),
    VerificationConversationItem.Analysis(
        isSample = true,
        result = VerificationResult(
            narrative = "Contoh tampilan: pesan yang meminta pemasangan APK dari chat perlu dicurigai sebelum sumber dan identitas pengirim diverifikasi.",
            riskLevel = RiskLevel.HIGH,
            reasons = listOf(
                "File APK dapat berasal dari sumber yang belum terverifikasi.",
                "Aplikasi mencurigakan dapat meminta akses SMS dan membaca kode OTP."
            ),
            recommendedActions = listOf(
                "Jangan membuka aplikasi atau memberikan izin tambahan.",
                "Periksa pengirim melalui kanal resmi."
            )
        )
    )
)
