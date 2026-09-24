package id.waspadai.app.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingTriggerParserTest {
    @Test
    fun `detects supported image signatures`() {
        assertEquals(
            "image/png",
            detectSupportedImageType(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)),
        )
        assertEquals("image/jpeg", detectSupportedImageType(byteArrayOf(-1, -40, -1, 0)))
        assertEquals(
            "image/webp",
            detectSupportedImageType("RIFF0000WEBP".encodeToByteArray()),
        )
    }

    @Test
    fun `rejects bytes with a spoofed or unknown image signature`() {
        assertNull(detectSupportedImageType("not-an-image".encodeToByteArray()))
    }
}
