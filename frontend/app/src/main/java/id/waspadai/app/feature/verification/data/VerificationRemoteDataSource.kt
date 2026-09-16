package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.verification.data.dto.TextVerificationRequestDto
import id.waspadai.app.feature.verification.data.dto.VerificationResponseDto
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

class VerificationRemoteDataSource(
    private val client: HttpClient,
    private val config: WaspadAiApiConfig
) {
    suspend fun submitText(text: String): VerificationResponseDto {
        val response = client.post(config.textVerificationUrl) {
            headers { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) }
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
