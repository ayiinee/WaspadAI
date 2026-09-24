package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult

class LoadVerificationConversationsUseCase(
    private val repository: VerificationRepository,
) {
    suspend operator fun invoke(): AppResult<List<VerificationConversationSummary>> =
        repository.listConversations()
}
