package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.verification.data.dto.ErrorEnvelopeDto
import id.waspadai.app.feature.verification.data.dto.HistoryPageDto
import id.waspadai.app.feature.verification.data.dto.TextVerificationRequestDto
import id.waspadai.app.feature.verification.data.dto.VerificationEnvelopeDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.util.UUID

class VerificationRemoteDataSource(
    private val client: HttpClient,
    private val config: WaspadAiApiConfig,
    private val tokenProvider: AccessTokenProvider,
) {
    suspend fun submitText(text: String): VerificationEnvelopeDto {
        val idempotencyKey = UUID.randomUUID().toString()
        val accessToken = requireAccessToken()
        var response = postText(text, accessToken, idempotencyKey)
        if (response.status == HttpStatusCode.Unauthorized) {
            val refreshedToken = tokenProvider.refreshAccessToken()
            if (!refreshedToken.isNullOrBlank()) {
                response = postText(text, refreshedToken, idempotencyKey)
            }
        }
        if (!response.status.isSuccess()) {
            throw VerificationApiException(response.status, response.safeError())
        }
        return response.body()
    }

    private suspend fun postText(
        text: String,
        accessToken: String,
        idempotencyKey: String,
    ): HttpResponse = client.post(config.textVerificationUrl) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append("Idempotency-Key", idempotencyKey)
            }
            accept(ContentType.Application.Json)
            setBody(TextVerificationRequestDto(text = text))
        }

    private suspend fun HttpResponse.safeError(): ProductApiError? {
        val rawBody = runCatching { bodyAsText() }.getOrNull() ?: return null
        return runCatching {
            json.decodeFromString<ErrorEnvelopeDto>(rawBody).error?.let { error ->
                ProductApiError(
                    code = error.code,
                    message = error.message,
                    retryable = error.retryable,
                    retryAfterSeconds = error.retryAfterSeconds,
                )
            }
        }.getOrNull()
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    suspend fun listHistory(): HistoryPageDto {
        val accessToken = requireAccessToken()
        val response = client.get(config.historyUrl) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw VerificationApiException(response.status, response.safeError())
        }
        return response.body()
    }

    suspend fun getHistoryDetail(caseId: String): VerificationEnvelopeDto {
        val accessToken = requireAccessToken()
        val response = client.get(config.historyDetailUrl(caseId)) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw VerificationApiException(response.status, response.safeError())
        }
        return response.body()
    }

    private suspend fun requireAccessToken(): String = tokenProvider.currentAccessToken()
        ?.takeIf(String::isNotBlank)
        ?: throw MissingAccessTokenException()
}

interface AccessTokenProvider {
    suspend fun currentAccessToken(): String?

    suspend fun refreshAccessToken(): String? = currentAccessToken()
}

class StaticAccessTokenProvider(private val token: String) : AccessTokenProvider {
    override suspend fun currentAccessToken(): String? = token.takeIf(String::isNotBlank)
}

data class ProductApiError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int?,
)

class MissingAccessTokenException : RuntimeException()

class VerificationApiException(
    val status: HttpStatusCode,
    val error: ProductApiError? = null,
) : RuntimeException()
