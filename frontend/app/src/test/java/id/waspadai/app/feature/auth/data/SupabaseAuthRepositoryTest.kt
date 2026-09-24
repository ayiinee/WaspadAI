package id.waspadai.app.feature.auth.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthRepositoryTest {
    @Test
    fun `restores persisted session after process recreation and clears it on logout`() = runTest {
        val store = FakeSessionStore(StoredAuthSession("restored-access", "restored-refresh"))
        val repository = SupabaseAuthRepository(
            client = httpClient(MockEngine { error("Network must not be called") }),
            supabaseUrl = "",
            publishableKey = "",
            sessionStore = store,
        )

        assertTrue(repository.hasSession())
        assertEquals("restored-access", repository.currentAccessToken())

        repository.signOut()

        assertFalse(repository.hasSession())
        assertNull(repository.currentAccessToken())
        assertEquals(1, store.clearCount)
    }

    @Test
    fun `password recovery sends email verifies code and updates password with recovery session`() = runTest {
        var requestIndex = 0
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            when (requestIndex++) {
                0 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("https://project.supabase.co/auth/v1/recover", request.url.toString())
                    assertEquals("user@example.com", Json.parseToJsonElement(body).jsonObject["email"]?.jsonPrimitive?.content)
                    respond("{}", headers = jsonHeaders())
                }
                1 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("https://project.supabase.co/auth/v1/verify", request.url.toString())
                    val payload = Json.parseToJsonElement(body).jsonObject
                    assertEquals("user@example.com", payload["email"]?.jsonPrimitive?.content)
                    assertEquals("123456", payload["token"]?.jsonPrimitive?.content)
                    assertEquals("recovery", payload["type"]?.jsonPrimitive?.content)
                    respond(
                        """{"access_token":"recovery-access","refresh_token":"recovery-refresh"}""",
                        headers = jsonHeaders(),
                    )
                }
                else -> {
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("https://project.supabase.co/auth/v1/user", request.url.toString())
                    assertEquals("Bearer recovery-access", request.headers[HttpHeaders.Authorization])
                    assertEquals("new-password", Json.parseToJsonElement(body).jsonObject["password"]?.jsonPrimitive?.content)
                    respond("{}", headers = jsonHeaders())
                }
            }
        }
        val repository = SupabaseAuthRepository(
            client = httpClient(engine),
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "publishable-key",
        )

        repository.requestPasswordReset(" user@example.com ")
        repository.verifyPasswordResetCode("user@example.com", "123456")
        repository.updatePassword("new-password")

        assertEquals(3, requestIndex)
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private class FakeSessionStore(initial: StoredAuthSession?) : AuthSessionStore {
        private var value = initial
        var clearCount = 0

        override fun load(): StoredAuthSession? = value

        override fun save(session: StoredAuthSession) {
            value = session
        }

        override fun clear() {
            value = null
            clearCount += 1
        }
    }
}
