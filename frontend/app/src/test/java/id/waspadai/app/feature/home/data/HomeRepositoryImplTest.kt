package id.waspadai.app.feature.home.data

import id.waspadai.app.core.common.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRepositoryImplTest {
    @Test
    fun `loads authenticated home and resolves relative image url`() = runTest {
        var authorization: String? = null
        val client = HttpClient(MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = HOME_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val result = HomeRepositoryImpl(client).loadHome("https://api.test/", "token")

        assertTrue(result is AppResult.Success)
        val dashboard = (result as AppResult.Success).value
        assertEquals("Bearer token", authorization)
        assertEquals("Putu Alvin", dashboard.displayName)
        assertEquals(
            "https://api.test/api/v1/community/post/media/image",
            dashboard.recentCases.single().imageUrl,
        )
        assertEquals(
            "https://api.test/api/v1/learning/media/cover.png",
            dashboard.learningRecommendations.single().imageUrl,
        )
        client.close()
    }
}

private const val HOME_RESPONSE = """
{
  "profile": {"display_name": "Putu Alvin"},
  "recent_cases": [{
    "case_id": "3a2fd727-87c0-4bb6-9ac6-1ab50fd02430",
    "community_id": "0eb7025d-4627-4cc3-862c-6c0ed709b4e8",
    "creator_name": "Alya Prameswari",
    "title": "Kasus",
    "summary": "Ringkasan",
    "verdict": "HOAX",
    "risk_level": "HIGH",
    "requires_human_review": false,
    "created_at": "2026-09-24T00:00:00Z",
    "image_url": "/api/v1/community/post/media/image"
  }],
  "learning_recommendations": [{
    "module_id": "9b919771-ae25-4d35-a7ce-b253eec3239d",
    "title": "Kenali Link Palsu",
    "summary": "Ringkasan materi",
    "image_url": "/api/v1/learning/media/cover.png",
    "progress_percent": 50.0
  }]
}
"""
