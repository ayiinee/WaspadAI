package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult

class LoadVerificationConversationDetailUseCase(
    private val repository: VerificationRepository,
) {
    suspend operator fun invoke(
        conversationId: String,
    ): AppResult<VerificationConversationDetail> = repository.getConversationDetail(conversationId)
}
