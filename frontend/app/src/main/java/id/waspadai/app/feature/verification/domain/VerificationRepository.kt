package id.waspadai.app.feature.verification.domain

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.core.trigger.VerificationPageContext

data class TextVerificationInput(
    val text: String,
    val question: String? = null,
    val sourceUrl: String? = null,
    val senderContext: String = "UNKNOWN",
    val pageContext: VerificationPageContext? = null,
    val source: TriggerSource = TriggerSource.IN_APP,
    val conversationId: String? = null,
)

data class ImageVerificationInput(
    val imageBytes: ByteArray,
    val contentType: String,
    val fileName: String,
    val question: String? = null,
    val source: TriggerSource = TriggerSource.IN_APP,
    val conversationId: String? = null,
)

interface VerificationRepository {
    suspend fun submitText(input: TextVerificationInput): AppResult<VerificationResult>

    suspend fun submitImage(input: ImageVerificationInput): AppResult<VerificationResult>

    suspend fun listHistory(): AppResult<List<VerificationHistoryItem>>

    suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail>

    suspend fun listConversations(): AppResult<List<VerificationConversationSummary>>

    suspend fun listConversationPage(cursor: String? = null): AppResult<VerificationConversationPage> =
        when (val result = listConversations()) {
            is AppResult.Success -> AppResult.Success(VerificationConversationPage(result.value, null))
            is AppResult.Failure -> result
        }

    suspend fun renameConversation(conversationId: String, title: String): AppResult<VerificationConversationSummary> =
        AppResult.Failure("Rename percakapan belum tersedia.")

    suspend fun deleteConversation(conversationId: String): AppResult<Unit> =
        AppResult.Failure("Hapus percakapan belum tersedia.")

    suspend fun loadConversationAttachment(conversationId: String, caseId: String): AppResult<ByteArray> =
        AppResult.Failure("Lampiran belum tersedia.")

    suspend fun getConversationDetail(
        conversationId: String,
    ): AppResult<VerificationConversationDetail>
}
