package id.waspadai.app.core.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

/** In-memory crop editor used by the assistant preview. */
class BitmapCropView(context: Context) : View(context) {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val imageBounds = RectF()
    private val selection = RectF()
    private var bitmap: Bitmap? = null
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(value: Bitmap) {
        bitmap = value
        requestLayout()
        invalidate()
    }

    fun croppedPng(): ByteArray? {
        val source = bitmap ?: return null
        if (imageBounds.width() <= 0f || imageBounds.height() <= 0f) return null
        val pixels = mapCropToPixels(
            selectionLeft = selection.left,
            selectionTop = selection.top,
            selectionRight = selection.right,
            selectionBottom = selection.bottom,
            imageLeft = imageBounds.left,
            imageTop = imageBounds.top,
            renderedWidth = imageBounds.width(),
            renderedHeight = imageBounds.height(),
            bitmapWidth = source.width,
            bitmapHeight = source.height,
        )
        val cropped = Bitmap.createBitmap(
            source,
            pixels.left,
            pixels.top,
            pixels.right - pixels.left,
            pixels.bottom - pixels.top,
        )
        return ByteArrayOutputStream().use { output ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
            if (cropped !== source) cropped.recycle()
            output.toByteArray()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        val scale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val renderedWidth = source.width * scale
        val renderedHeight = source.height * scale
        imageBounds.set(
            (width - renderedWidth) / 2f,
            (height - renderedHeight) / 2f,
            (width + renderedWidth) / 2f,
            (height + renderedHeight) / 2f,
        )
        if (selection.isEmpty) {
            val inset = min(imageBounds.width(), imageBounds.height()) * 0.06f
            selection.set(imageBounds.left + inset, imageBounds.top + inset, imageBounds.right - inset, imageBounds.bottom - inset)
        }
        canvas.drawBitmap(source, null, imageBounds, imagePaint)
        canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, selection.top, shadePaint)
        canvas.drawRect(imageBounds.left, selection.bottom, imageBounds.right, imageBounds.bottom, shadePaint)
        canvas.drawRect(imageBounds.left, selection.top, selection.left, selection.bottom, shadePaint)
        canvas.drawRect(selection.right, selection.top, imageBounds.right, selection.bottom, shadePaint)
        canvas.drawRect(selection, borderPaint)
        drawHandle(canvas, selection.left, selection.top)
        drawHandle(canvas, selection.right, selection.top)
        drawHandle(canvas, selection.left, selection.bottom)
        drawHandle(canvas, selection.right, selection.bottom)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = dragModeFor(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (dragMode) {
                    DragMode.MOVE -> selection.offset(dx, dy)
                    DragMode.TOP_LEFT -> { selection.left += dx; selection.top += dy }
                    DragMode.TOP_RIGHT -> { selection.right += dx; selection.top += dy }
                    DragMode.BOTTOM_LEFT -> { selection.left += dx; selection.bottom += dy }
                    DragMode.BOTTOM_RIGHT -> { selection.right += dx; selection.bottom += dy }
                    DragMode.NONE -> return false
                }
                normalizeSelection()
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dragModeFor(x: Float, y: Float): DragMode {
        val tolerance = dp(30f)
        return when {
            near(x, y, selection.left, selection.top, tolerance) -> DragMode.TOP_LEFT
            near(x, y, selection.right, selection.top, tolerance) -> DragMode.TOP_RIGHT
            near(x, y, selection.left, selection.bottom, tolerance) -> DragMode.BOTTOM_LEFT
            near(x, y, selection.right, selection.bottom, tolerance) -> DragMode.BOTTOM_RIGHT
            selection.contains(x, y) -> DragMode.MOVE
            else -> DragMode.NONE
        }
    }

    private fun normalizeSelection() {
        val minSize = dp(64f)
        if (selection.width() < minSize) selection.right = selection.left + minSize
        if (selection.height() < minSize) selection.bottom = selection.top + minSize
        if (selection.left < imageBounds.left) selection.offset(imageBounds.left - selection.left, 0f)
        if (selection.top < imageBounds.top) selection.offset(0f, imageBounds.top - selection.top)
        if (selection.right > imageBounds.right) selection.offset(imageBounds.right - selection.right, 0f)
        if (selection.bottom > imageBounds.bottom) selection.offset(0f, imageBounds.bottom - selection.bottom)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) = canvas.drawCircle(x, y, dp(7f), handlePaint)
    private fun near(x: Float, y: Float, targetX: Float, targetY: Float, tolerance: Float) =
        abs(x - targetX) <= tolerance && abs(y - targetY) <= tolerance
    private fun dp(value: Float) = value * resources.displayMetrics.density

    private enum class DragMode { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
}

internal data class PixelCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun mapCropToPixels(
    selectionLeft: Float,
    selectionTop: Float,
    selectionRight: Float,
    selectionBottom: Float,
    imageLeft: Float,
    imageTop: Float,
    renderedWidth: Float,
    renderedHeight: Float,
    bitmapWidth: Int,
    bitmapHeight: Int,
): PixelCrop {
    require(renderedWidth > 0f && renderedHeight > 0f)
    require(bitmapWidth > 0 && bitmapHeight > 0)
    val left = ((selectionLeft - imageLeft) / renderedWidth * bitmapWidth)
        .toInt().coerceIn(0, bitmapWidth - 1)
    val top = ((selectionTop - imageTop) / renderedHeight * bitmapHeight)
        .toInt().coerceIn(0, bitmapHeight - 1)
    val right = ((selectionRight - imageLeft) / renderedWidth * bitmapWidth)
        .toInt().coerceIn(left + 1, bitmapWidth)
    val bottom = ((selectionBottom - imageTop) / renderedHeight * bitmapHeight)
        .toInt().coerceIn(top + 1, bitmapHeight)
    return PixelCrop(left, top, right, bottom)
}
