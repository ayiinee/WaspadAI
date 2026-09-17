package id.waspadai.app.core.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MediaProjectionController(private val context: Context) {
    suspend fun capturePng(resultCode: Int, data: Intent): ByteArray = withContext(Dispatchers.Main.immediate) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, data)
            ?: throw CaptureException("Izin tangkapan layar tidak valid. Aktifkan overlay lagi.")
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() = Unit
        }
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
        try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val virtualDisplay = projection.createVirtualDisplay(
                        "WaspadAIOverlayCapture",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface,
                        null,
                        null,
                    ) ?: run {
                        continuation.resumeWithException(
                            CaptureException("Tangkapan layar belum dapat dimulai. Coba lagi.")
                        )
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation {
                        virtualDisplay.release()
                    }
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val bitmap = image.toBitmap(width, height)
                            if (!continuation.isActive) {
                                bitmap.recycle()
                                return@setOnImageAvailableListener
                            }
                            if (bitmap.isBlankFrame()) {
                                bitmap.recycle()
                                continuation.resumeWithException(
                                    CaptureException("Layar tidak dapat ditangkap. Coba aplikasi lain atau gunakan upload gambar manual.")
                                )
                            } else {
                                continuation.resume(bitmap.toPngBytes())
                            }
                        } catch (error: Throwable) {
                            continuation.resumeWithException(error)
                        } finally {
                            image.close()
                            reader.setOnImageAvailableListener(null, null)
                            virtualDisplay.release()
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            }
        } finally {
            imageReader.close()
            projection.unregisterCallback(callback)
            projection.stop()
        }
    }

    private fun android.media.Image.toBitmap(width: Int, height: Int): Bitmap {
        val plane = planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
            if (it !== paddedBitmap) paddedBitmap.recycle()
        }
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        recycle()
        return output.toByteArray()
    }

    private fun Bitmap.isBlankFrame(): Boolean {
        val stepX = (width / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (height / SAMPLE_GRID).coerceAtLeast(1)
        var sampled = 0
        var dark = 0
        var transparent = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                val alpha = pixel ushr 24 and 0xFF
                val red = pixel ushr 16 and 0xFF
                val green = pixel ushr 8 and 0xFF
                val blue = pixel and 0xFF
                sampled += 1
                if (alpha == 0) transparent += 1
                if (red < BLANK_THRESHOLD && green < BLANK_THRESHOLD && blue < BLANK_THRESHOLD) dark += 1
                x += stepX
            }
            y += stepY
        }
        return sampled > 0 && (transparent == sampled || dark == sampled)
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 5_000L
        const val SAMPLE_GRID = 12
        const val BLANK_THRESHOLD = 6
    }
}

class CaptureException(message: String) : RuntimeException(message)
