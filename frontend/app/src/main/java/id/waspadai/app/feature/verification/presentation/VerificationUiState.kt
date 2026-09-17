package id.waspadai.app.feature.verification.presentation

import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem

data class VerificationUiState(
    val draft: String = "",
    val conversation: List<VerificationConversationItem> = emptyList(),
    val history: List<VerificationHistoryItem> = emptyList(),
    val isHistoryVisible: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val phase: VerificationPhase = VerificationPhase.Idle,
    val isRemoteEnabled: Boolean = false
) {
    companion object {
        fun initial(isRemoteEnabled: Boolean): VerificationUiState {
            return VerificationUiState(
                conversation = if (isRemoteEnabled) emptyList() else previewConversation(),
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
    data class UserMessage(val text: String, val hasAttachment: Boolean = false) : VerificationConversationItem

    data class Analysis(val result: VerificationResult, val isSample: Boolean = false) : VerificationConversationItem
}
