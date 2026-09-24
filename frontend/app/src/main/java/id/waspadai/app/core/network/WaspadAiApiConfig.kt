package id.waspadai.app.core.network

data class WaspadAiApiConfig(private val baseUrl: String) {
    private val normalizedBaseUrl: String = baseUrl.trimEnd('/')

    val textVerificationUrl: String = "$normalizedBaseUrl/api/v1/verifications/text"
    val imageVerificationUrl: String = "$normalizedBaseUrl/api/v1/verifications/image"
    val historyUrl: String = "$normalizedBaseUrl/api/v1/history"
    val conversationsUrl: String = "$normalizedBaseUrl/api/v1/conversations"

    fun historyDetailUrl(caseId: String): String = "$historyUrl/$caseId"

    fun conversationDetailUrl(conversationId: String): String = "$conversationsUrl/$conversationId"

    fun communityPreviewUrl(caseId: String): String = "$historyUrl/$caseId/community-preview"

    fun communityPublishUrl(caseId: String): String = "$historyUrl/$caseId/community"
}
