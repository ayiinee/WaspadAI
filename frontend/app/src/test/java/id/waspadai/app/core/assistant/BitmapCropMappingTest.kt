package id.waspadai.app.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapCropMappingTest {
    @Test
    fun `maps rendered crop coordinates to source bitmap pixels`() {
        val crop = mapCropToPixels(
            selectionLeft = 50f,
            selectionTop = 100f,
            selectionRight = 450f,
            selectionBottom = 900f,
            imageLeft = 0f,
            imageTop = 0f,
            renderedWidth = 500f,
            renderedHeight = 1_000f,
            bitmapWidth = 1_000,
            bitmapHeight = 2_000,
        )

        assertEquals(PixelCrop(100, 200, 900, 1_800), crop)
    }

    @Test
    fun `clamps crop coordinates to bitmap bounds`() {
        val crop = mapCropToPixels(
            selectionLeft = -20f,
            selectionTop = -10f,
            selectionRight = 220f,
            selectionBottom = 210f,
            imageLeft = 0f,
            imageTop = 0f,
            renderedWidth = 200f,
            renderedHeight = 200f,
            bitmapWidth = 400,
            bitmapHeight = 400,
        )

        assertEquals(PixelCrop(0, 0, 400, 400), crop)
    }
}
