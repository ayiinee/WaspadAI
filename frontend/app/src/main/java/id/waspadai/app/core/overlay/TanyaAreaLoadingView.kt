package id.waspadai.app.core.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Shared loading animation for Floating Verify and the system assistant. */
class TanyaAreaLoadingView(context: Context) : View(context) {
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 92, 158) }
    private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 158, 11) }
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_150L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val orbit = min(width, height) * .29f
        repeat(6) { index ->
            val step = index / 6f
            val angle = ((phase + step) * Math.PI * 2).toFloat()
            val pulse = ((phase + step) % 1f)
            val radius = min(width, height) * (.055f + .035f * (1f - abs(.5f - pulse) * 2f))
            val paint = if (index % 3 == 0) yellowPaint else bluePaint
            paint.alpha = (125 + 130 * (1f - pulse)).toInt()
            canvas.drawCircle(centerX + cos(angle) * orbit, centerY + sin(angle) * orbit, radius, paint)
        }
        bluePaint.alpha = 255
        canvas.drawCircle(centerX, centerY, min(width, height) * .09f, bluePaint)
    }
}
