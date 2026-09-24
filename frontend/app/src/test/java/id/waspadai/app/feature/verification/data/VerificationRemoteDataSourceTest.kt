package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.network.WaspadAiApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VerificationRemoteDataSourceTest {
    @Test
    fun `submits text to Product API with bearer token and idempotency key`() = runTest {
        var requestedUrl = ""
        var authorization = ""
        var idempotencyKey: String? = null

        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            authorization = request.headers[HttpHeaders.Authorization].orEmpty()
            idempotencyKey = request.headers["Idempotency-Key"]
            respond(
                content = successEnvelope,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val dataSource = VerificationRemoteDataSource(
            client = httpClient(engine),
            config = WaspadAiApiConfig("https://product.example"),
            tokenProvider = StaticAccessTokenProvider("user-token"),
        )

        dataSource.submitText("Pesan uji remote dengan panjang cukup.")

        assertEquals("https://product.example/api/v1/verifications/text", requestedUrl)
        assertEquals("Bearer user-token", authorization)
        assertNotNull(idempotencyKey)
    }

    @Test
    fun `retries unauthorized response once with refreshed token and same idempotency key`() = runTest {
        val idempotencyKeys = mutableListOf<String?>()
        val authorizations = mutableListOf<String?>()
        val engine = MockEngine { request ->
            idempotencyKeys += request.headers["Idempotency-Key"]
            authorizations += request.headers[HttpHeaders.Authorization]
            if (idempotencyKeys.size == 1) {
                respond(
                    content = """{"error":{"code":"INVALID_ACCESS_TOKEN","message":"expired"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    content = successEnvelope,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val dataSource = VerificationRemoteDataSource(
            client = httpClient(engine),
            config = WaspadAiApiConfig("https://product.example"),
            tokenProvider = RefreshingTokenProvider(),
        )

        dataSource.submitText("Pesan uji retry dengan panjang cukup.")

        assertEquals(listOf("Bearer old-token", "Bearer new-token"), authorizations)
        assertEquals(2, idempotencyKeys.size)
        assertEquals(idempotencyKeys[0], idempotencyKeys[1])
    }

    @Test
    fun `requires an access token before submitting`() = runTest {
        val engine = MockEngine {
            throw AssertionError("Request should not be sent without a token")
        }
        val dataSource = VerificationRemoteDataSource(
            client = httpClient(engine),
            config = WaspadAiApiConfig("https://product.example"),
            tokenProvider = StaticAccessTokenProvider(""),
        )

        val result = runCatching {
            dataSource.submitText("Pesan uji token dengan panjang cukup.")
        }

        assertFalse(result.isSuccess)
        assertEquals(MissingAccessTokenException::class, result.exceptionOrNull()!!::class)
    }

    @Test
    fun `conversation pagination sends cursor query`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("next-page", request.url.parameters["cursor"])
            respond(
                content = """{"items":[],"next_cursor":null}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val dataSource = VerificationRemoteDataSource(
            httpClient(engine),
            WaspadAiApiConfig("https://product.example"),
            StaticAccessTokenProvider("user-token"),
        )

        dataSource.listConversations("next-page")
    }

    @Test
    fun `rename and delete use conversation resource`() = runTest {
        var requestIndex = 0
        val engine = MockEngine { request ->
            requestIndex += 1
            assertEquals(
                "https://product.example/api/v1/conversations/conversation-1",
                request.url.toString(),
            )
            if (requestIndex == 1) {
                assertEquals("PATCH", request.method.value)
                respond(
                    content = """{
                        "conversation_id":"conversation-1",
                        "title":"Judul baru",
                        "latest_message_preview":"Preview",
                        "latest_message_role":"ASSISTANT",
                        "last_verdict":"UNVERIFIED",
                        "created_at":"2026-09-17T10:00:00Z",
                        "updated_at":"2026-09-17T10:05:00Z"
                    }""".trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                assertEquals("DELETE", request.method.value)
                respond(content = "", status = HttpStatusCode.NoContent)
            }
        }
        val dataSource = VerificationRemoteDataSource(
            httpClient(engine),
            WaspadAiApiConfig("https://product.example"),
            StaticAccessTokenProvider("user-token"),
        )

        assertEquals("Judul baru", dataSource.renameConversation("conversation-1", "Judul baru").title)
        dataSource.deleteConversation("conversation-1")
        assertEquals(2, requestIndex)
    }

    @Test
    fun `attachment download returns in-memory bytes`() = runTest {
        val expected = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine { request ->
            assertEquals(
                "https://product.example/api/v1/conversations/conversation-1/attachments/case-1",
                request.url.toString(),
            )
            respond(
                content = expected,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
            )
        }
        val dataSource = VerificationRemoteDataSource(
            httpClient(engine),
            WaspadAiApiConfig("https://product.example"),
            StaticAccessTokenProvider("user-token"),
        )

        assertArrayEquals(expected, dataSource.loadConversationAttachment("conversation-1", "case-1"))
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
    }

    private class RefreshingTokenProvider : AccessTokenProvider {
        override suspend fun currentAccessToken(): String = "old-token"

        override suspend fun refreshAccessToken(): String = "new-token"
    }

    private companion object {
        val successEnvelope = """
            {
              "request_id": "8f20b3a3-7d90-4b0a-a5ee-59b7b0a4e8b8",
              "status": "COMPLETED",
              "execution_mode": "REMOTE",
              "history": {
                "saved": true,
                "case_id": "56f50192-7dd1-4bec-9a52-d838174c9d23",
                "save_reason": "UNVERIFIED",
                "community_eligible": true,
                "community_state": "PRIVATE"
              },
              "result": {
                "risk_level": "MEDIUM",
                "why": ["Bukti belum cukup."],
                "recommended_actions": [{"title": "Periksa sumber resmi."}],
                "presentation": {
                  "narrative": {
                    "text": "Hasil pemeriksaan dari Product API."
                  }
                }
              }
            }
        """.trimIndent()
    }
}
