package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult

class LoadVerificationConversationsUseCase(
    private val repository: VerificationRepository,
) {
    suspend operator fun invoke(cursor: String? = null): AppResult<VerificationConversationPage> =
        repository.listConversationPage(cursor)

    suspend fun rename(id: String, title: String) = repository.renameConversation(id, title)

    suspend fun delete(id: String) = repository.deleteConversation(id)

    suspend fun attachment(conversationId: String, caseId: String) =
        repository.loadConversationAttachment(conversationId, caseId)
}
