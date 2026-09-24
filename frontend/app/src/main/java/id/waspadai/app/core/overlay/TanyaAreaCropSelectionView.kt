package id.waspadai.app.core.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** Shared area selector used by both Floating Verify and the system assistant. */
class TanyaAreaCropSelectionView private constructor(
    context: Context,
    private val backgroundBitmap: Bitmap?,
    private val recycleBackgroundOnDetach: Boolean,
) : View(context) {
    constructor(context: Context, backgroundBytes: ByteArray?) : this(
        context,
        backgroundBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
        true,
    )

    constructor(context: Context, backgroundBitmap: Bitmap) : this(context, backgroundBitmap, false)

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(118, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TanyaAreaStyle.BRAND_BLUE
        style = Paint.Style.STROKE
        strokeWidth = dp(3).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 0, 118, 191)
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val selection = RectF()
    private var mode = DragMode.None
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        if (selection.isEmpty) resetSelection()
    }

    override fun onDraw(canvas: Canvas) {
        backgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, dimPaint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, dimPaint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, dimPaint)
        canvas.drawRect(selection, fillPaint)
        canvas.drawRect(selection, borderPaint)
        drawHandle(canvas, selection.left, selection.top)
        drawHandle(canvas, selection.right, selection.top)
        drawHandle(canvas, selection.left, selection.bottom)
        drawHandle(canvas, selection.right, selection.bottom)
    }

    override fun onDetachedFromWindow() {
        if (recycleBackgroundOnDetach) backgroundBitmap?.recycle()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                mode = dragModeFor(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (mode) {
                    DragMode.Move -> selection.offset(dx, dy)
                    DragMode.TopLeft -> { selection.left += dx; selection.top += dy }
                    DragMode.TopRight -> { selection.right += dx; selection.top += dy }
                    DragMode.BottomLeft -> { selection.left += dx; selection.bottom += dy }
                    DragMode.BottomRight -> { selection.right += dx; selection.bottom += dy }
                    DragMode.None -> Unit
                }
                normalizeSelection()
                lastX = event.x
                lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = DragMode.None
        }
        return true
    }

    fun selectedRect(): Rect {
        normalizeSelection()
        return Rect(selection.left.toInt(), selection.top.toInt(), selection.right.toInt(), selection.bottom.toInt())
    }

    fun croppedPng(): ByteArray? {
        val source = backgroundBitmap ?: return null
        if (width <= 0 || height <= 0) return null
        val selected = selectedRect()
        val pixels = mapTanyaAreaCropToPixels(
            selected.left,
            selected.top,
            selected.right,
            selected.bottom,
            width,
            height,
            source.width,
            source.height,
        )
        val cropped = Bitmap.createBitmap(source, pixels.left, pixels.top, pixels.width, pixels.height)
        return ByteArrayOutputStream().use { output ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
            if (cropped !== source) cropped.recycle()
            output.toByteArray()
        }
    }

    fun resetSelection() {
        if (width <= 0 || height <= 0) return
        val inset = width * .12f
        val top = height * .22f
        selection.set(inset, top, width - inset, top + height * .32f)
        invalidate()
    }

    private fun dragModeFor(x: Float, y: Float): DragMode {
        val handle = dp(32).toFloat()
        return when {
            distanceTo(x, y, selection.left, selection.top) <= handle -> DragMode.TopLeft
            distanceTo(x, y, selection.right, selection.top) <= handle -> DragMode.TopRight
            distanceTo(x, y, selection.left, selection.bottom) <= handle -> DragMode.BottomLeft
            distanceTo(x, y, selection.right, selection.bottom) <= handle -> DragMode.BottomRight
            selection.contains(x, y) -> DragMode.Move
            else -> {
                selection.set(x, y, x + dp(160), y + dp(120))
                normalizeSelection()
                DragMode.BottomRight
            }
        }
    }

    private fun normalizeSelection() {
        val minSize = dp(80).toFloat()
        if (selection.width() < minSize) selection.right = selection.left + minSize
        if (selection.height() < minSize) selection.bottom = selection.top + minSize
        if (selection.left < 0f) selection.offset(-selection.left, 0f)
        if (selection.top < 0f) selection.offset(0f, -selection.top)
        if (selection.right > width) selection.offset(width - selection.right, 0f)
        if (selection.bottom > height) selection.offset(0f, height - selection.bottom)
        selection.left = selection.left.coerceIn(0f, (width - minSize).coerceAtLeast(0f))
        selection.top = selection.top.coerceIn(0f, (height - minSize).coerceAtLeast(0f))
        selection.right = selection.right.coerceIn(selection.left + minSize, width.toFloat())
        selection.bottom = selection.bottom.coerceIn(selection.top + minSize, height.toFloat())
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, dp(7).toFloat(), handlePaint)
        canvas.drawCircle(x, y, dp(7).toFloat(), borderPaint)
    }

    private fun distanceTo(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private enum class DragMode { None, Move, TopLeft, TopRight, BottomLeft, BottomRight }
}

internal data class TanyaAreaPixelCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal fun mapTanyaAreaCropToPixels(
    selectionLeft: Int,
    selectionTop: Int,
    selectionRight: Int,
    selectionBottom: Int,
    renderedWidth: Int,
    renderedHeight: Int,
    bitmapWidth: Int,
    bitmapHeight: Int,
): TanyaAreaPixelCrop {
    require(renderedWidth > 0 && renderedHeight > 0 && bitmapWidth > 0 && bitmapHeight > 0)
    val left = (selectionLeft.toFloat() / renderedWidth * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
    val top = (selectionTop.toFloat() / renderedHeight * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
    val right = (selectionRight.toFloat() / renderedWidth * bitmapWidth).toInt().coerceIn(left + 1, bitmapWidth)
    val bottom = (selectionBottom.toFloat() / renderedHeight * bitmapHeight).toInt().coerceIn(top + 1, bitmapHeight)
    return TanyaAreaPixelCrop(left, top, right, bottom)
}

object TanyaAreaStyle {
    const val BRAND_BLUE = 0xFF005C9E.toInt()
    const val DEEP_BLUE = 0xFF153A52.toInt()
    const val BORDER_BLUE = 0xFFB6D7EB.toInt()
    const val SOFT_BLUE = 0xFFF0F7FB.toInt()
    const val MUTED = 0xFF557383.toInt()
    const val ERROR_RED = 0xFFA52219.toInt()
    const val ERROR_SOFT = 0xFFFFEDEB.toInt()
}
