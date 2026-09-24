package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.network.WaspadAiApiConfig
import id.waspadai.app.feature.verification.data.mapper.VerificationMapper
import id.waspadai.app.feature.verification.domain.TextVerificationInput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationRepositoryImplTest {
    @Test
    fun `submit text sends bearer and idempotency headers`() = runTest {
        val repository = repositoryWith(MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            assertTrue(request.headers["Idempotency-Key"]?.isNotBlank() == true)
            respond(envelopeJson(inputText = null), headers = jsonHeaders())
        })

        val result = repository.submitText(TextVerificationInput("Tolong cek pesan OTP ini"))

        assertTrue(result is AppResult.Success)
        assertEquals(RiskLevel.HIGH, (result as AppResult.Success).value.riskLevel)
    }

    @Test
    fun `list history parses history page`() = runTest {
        val repository = repositoryWith(MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(
                """
                {
                  "items": [
                    {
                      "case_id": "case-1",
                      "input_type": "TEXT",
                      "headline": "Pesan OTP",
                      "verdict": "UNVERIFIED",
                      "requires_human_review": true,
                      "community_state": "PRIVATE",
                      "created_at": "2026-09-17T10:00:00Z"
                    }
                  ],
                  "next_cursor": null
                }
                """.trimIndent(),
                headers = jsonHeaders()
            )
        })

        val result = repository.listHistory()

        assertTrue(result is AppResult.Success)
        val item = (result as AppResult.Success).value.single()
        assertEquals("case-1", item.caseId)
        assertEquals("Pesan OTP", item.headline)
    }

    @Test
    fun `history detail parses input text and analysis`() = runTest {
        val repository = repositoryWith(MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(envelopeJson(inputText = "Pesan meminta OTP"), headers = jsonHeaders())
        })

        val result = repository.getHistoryDetail("case-1")

        assertTrue(result is AppResult.Success)
        val detail = (result as AppResult.Success).value
        assertEquals("case-1", detail.caseId)
        assertEquals("Pesan meminta OTP", detail.inputText)
        assertEquals(RiskLevel.HIGH, detail.result.riskLevel)
    }

    @Test
    fun `list conversations parses vertical room summaries`() = runTest {
        val repository = repositoryWith(MockEngine {
            respond(
                """
                {
                  "items": [{
                    "conversation_id": "conversation-1",
                    "title": "Pesan OTP",
                    "latest_message_preview": "Jangan bagikan kode OTP.",
                    "latest_message_role": "ASSISTANT",
                    "last_verdict": "UNVERIFIED",
                    "created_at": "2026-09-17T10:00:00Z",
                    "updated_at": "2026-09-17T10:05:00Z"
                  }],
                  "next_cursor": null
                }
                """.trimIndent(),
                headers = jsonHeaders(),
            )
        })

        val result = repository.listConversations()

        assertTrue(result is AppResult.Success)
        val room = (result as AppResult.Success).value.single()
        assertEquals("conversation-1", room.conversationId)
        assertEquals("Jangan bagikan kode OTP.", room.latestMessagePreview)
    }

    @Test
    fun `conversation detail restores all turns in order`() = runTest {
        val repository = repositoryWith(MockEngine {
            respond(
                """
                {
                  "conversation_id": "conversation-1",
                  "title": "Pesan OTP",
                  "created_at": "2026-09-17T10:00:00Z",
                  "updated_at": "2026-09-17T10:05:00Z",
                  "turns": [
                    {
                      "case_id": "case-1",
                      "input_type": "TEXT",
                      "input_text": "Tolong cek pesan OTP ini",
                      "created_at": "2026-09-17T10:00:00Z",
                      "execution_mode": "MOCK",
                      "history": {
                        "saved": true,
                        "case_id": "case-1",
                        "conversation_id": "conversation-1",
                        "save_reason": "ALL_POLICY",
                        "community_eligible": false,
                        "community_state": "PRIVATE"
                      },
                      "result": {
                        "headline": "Pesan OTP",
                        "risk_level": "HIGH",
                        "why": [],
                        "recommended_actions": [],
                        "presentation": {"narrative": {"text": "Jangan bagikan OTP."}}
                      }
                    }
                  ]
                }
                """.trimIndent(),
                headers = jsonHeaders(),
            )
        })

        val result = repository.getConversationDetail("conversation-1")

        assertTrue(result is AppResult.Success)
        val detail = (result as AppResult.Success).value
        assertEquals("Pesan OTP", detail.title)
        assertEquals("Tolong cek pesan OTP ini", detail.turns.single().inputText)
        assertEquals("conversation-1", detail.turns.single().result.conversationId)
    }

    private fun repositoryWith(engine: MockEngine): VerificationRepositoryImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }
        return VerificationRepositoryImpl(
            remoteDataSource = VerificationRemoteDataSource(
                client = client,
                config = WaspadAiApiConfig("https://api.example.test"),
                tokenProvider = StaticAccessTokenProvider("test-token")
            ),
            mapper = VerificationMapper()
        )
    }

    private fun jsonHeaders(): Headers =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun envelopeJson(inputText: String?): String {
        val inputTextField = inputText?.let { ""","input_text":"$it"""" } ?: ""
        return """
            {
              "request_id": "00000000-0000-0000-0000-000000000001",
              "status": "COMPLETED",
              "execution_mode": "MOCK",
              "history": {
                "saved": true,
                "case_id": "case-1",
                "save_reason": "UNVERIFIED",
                "community_eligible": false,
                "community_state": "PRIVATE"
              },
              "result": {
                "risk_level": "HIGH",
                "why": ["Meminta kode OTP."],
                "recommended_actions": [{"title": "Jangan kirim OTP."}],
                "presentation": {"narrative": {"text": "Jangan bagikan kode OTP."}}
              }
              $inputTextField
            }
        """.trimIndent()
    }
}
