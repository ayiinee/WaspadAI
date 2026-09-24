package id.waspadai.app.core.assistant

import id.waspadai.app.core.overlay.TanyaAreaPixelCrop
import id.waspadai.app.core.overlay.mapTanyaAreaCropToPixels
import org.junit.Assert.assertEquals
import org.junit.Test

class TanyaAreaCropMappingTest {
    @Test
    fun `maps rendered crop coordinates to source bitmap pixels`() {
        val crop = mapTanyaAreaCropToPixels(
            selectionLeft = 50,
            selectionTop = 100,
            selectionRight = 450,
            selectionBottom = 900,
            renderedWidth = 500,
            renderedHeight = 1_000,
            bitmapWidth = 1_000,
            bitmapHeight = 2_000,
        )

        assertEquals(TanyaAreaPixelCrop(100, 200, 900, 1_800), crop)
    }

    @Test
    fun `clamps crop coordinates to bitmap bounds`() {
        val crop = mapTanyaAreaCropToPixels(
            selectionLeft = -20,
            selectionTop = -10,
            selectionRight = 220,
            selectionBottom = 210,
            renderedWidth = 200,
            renderedHeight = 200,
            bitmapWidth = 400,
            bitmapHeight = 400,
        )

        assertEquals(TanyaAreaPixelCrop(0, 0, 400, 400), crop)
    }
}
