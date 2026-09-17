package id.waspadai.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WaspadAiApiConfigTest {
    @Test
    fun `builds product api verification and history urls`() {
        val config = WaspadAiApiConfig("https://api.example.test/")

        assertEquals("https://api.example.test/api/v1/verifications/text", config.textVerificationUrl)
        assertEquals("https://api.example.test/api/v1/history", config.historyUrl)
        assertEquals(
            "https://api.example.test/api/v1/history/case-123",
            config.historyDetailUrl("case-123")
        )
    }
}
