package id.waspadai.app.core.network

data class WaspadAiApiConfig(private val baseUrl: String) {
    private val normalizedBaseUrl: String = baseUrl.trimEnd('/')

    val textVerificationUrl: String = "$normalizedBaseUrl/api/v1/verifications/text"
    val historyUrl: String = "$normalizedBaseUrl/api/v1/history"

    fun historyDetailUrl(caseId: String): String = "$historyUrl/$caseId"
}
