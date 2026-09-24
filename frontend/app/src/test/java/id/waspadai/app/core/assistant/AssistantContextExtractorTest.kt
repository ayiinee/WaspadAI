package id.waspadai.app.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantContextExtractorTest {
    @Test
    fun `normalizes whitespace and removes duplicate visible text`() {
        val result = AssistantContextExtractor.normalizeVisibleText(
            listOf("  Transfer   sekarang ", "Transfer sekarang", "Nomor rekening 123")
        )

        assertEquals("Transfer sekarang\nNomor rekening 123", result)
    }

    @Test
    fun `caps extracted text to session limit`() {
        val result = AssistantContextExtractor.normalizeVisibleText(listOf("a".repeat(30_000)))

        assertEquals(25_000, result.length)
    }
}
