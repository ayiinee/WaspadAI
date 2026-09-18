package id.waspadai.app.feature.community.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.network.WaspadAiApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityRepositoryImplTest {
    @Test
    fun `request preview sends authenticated case endpoint`() = runTest {
        val repository = repositoryWith(MockEngine { request ->
            assertEquals(
                "https://api.example.test/api/v1/history/case-1/community-preview",
                request.url.toString(),
            )
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            respond(
                """
                {
                  "preview_id": "preview-1",
                  "expires_at": "2026-09-18T12:00:00Z",
                  "redacted_text": "Pesan aman.",
                  "redacted_image_url": null,
                  "redactions": [],
                  "confirmation_required": true
                }
                """.trimIndent(),
                headers = jsonHeaders(),
            )
        })

        val result = repository.requestPreview(
            baseUrl = "https://api.example.test/",
            accessToken = "token",
            caseId = "case-1",
        )

        assertTrue(result is AppResult.Success)
        assertEquals("preview-1", (result as AppResult.Success).value.previewId)
    }

    @Test
    fun `publish sends both publication and rag consent`() = runTest {
        val repository = repositoryWith(MockEngine { request ->
            assertEquals(
                "https://api.example.test/api/v1/history/case-1/community",
                request.url.toString(),
            )
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            respond(
                """
                {
                  "case_id": "case-1",
                  "community_state": "PUBLISHED_UNVERIFIED",
                  "revision": 2
                }
                """.trimIndent(),
                headers = jsonHeaders(),
            )
        })

        val result = repository.publishCase(
            baseUrl = "https://api.example.test/",
            accessToken = "token",
            caseId = "case-1",
            previewId = "preview-1",
            ragReuseConsent = true,
        )

        assertTrue(result is AppResult.Success)
        assertEquals(
            "PUBLISHED_UNVERIFIED",
            (result as AppResult.Success).value.communityState,
        )
    }

    private fun repositoryWith(engine: MockEngine): CommunityRepositoryImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }
        return CommunityRepositoryImpl(client)
    }

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
