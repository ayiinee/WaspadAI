package id.waspadai.app.core.network

data class WaspadAiApiConfig(private val baseUrl: String) {
    val textVerificationUrl: String = "${baseUrl.trimEnd('/')}/api/v1/verify/text"
}
