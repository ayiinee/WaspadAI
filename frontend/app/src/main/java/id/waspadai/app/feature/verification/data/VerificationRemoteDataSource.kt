package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.verification.data.dto.TextVerificationRequestDto
import id.waspadai.app.feature.verification.data.dto.VerificationEnvelopeDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.util.UUID

class VerificationRemoteDataSource(
    private val client: HttpClient,
    private val config: WaspadAiApiConfig,
    private val tokenProvider: () -> String? = { null },
) {
    suspend fun submitText(text: String): VerificationEnvelopeDto {
        val accessToken = tokenProvider()
        val idempotencyKey = UUID.randomUUID().toString()
        val response = client.post(config.textVerificationUrl) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                if (accessToken != null) {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                append("Idempotency-Key", idempotencyKey)
            }
            accept(ContentType.Application.Json)
            setBody(TextVerificationRequestDto(text = text))
        }
        if (!response.status.isSuccess()) {
            throw VerificationApiException(response.status)
        }
        return response.body()
    }
}

class VerificationApiException(val status: HttpStatusCode) : RuntimeException()
