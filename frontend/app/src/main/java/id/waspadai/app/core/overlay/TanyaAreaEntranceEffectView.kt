package id.waspadai.app.core.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.sin

/** Shared entrance effect for Floating Verify and the system assistant. */
class TanyaAreaEntranceEffectView(
    context: Context,
    private val onFinished: (TanyaAreaEntranceEffectView) -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(
        Color.rgb(0, 92, 158),
        Color.rgb(37, 150, 190),
        Color.rgb(245, 158, 11),
        Color.rgb(255, 196, 64),
    )
    private var progress = 0f
    private var suppressFinish = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_050L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!suppressFinish) onFinished(this@TanyaAreaEntranceEffectView)
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        if (animator.isRunning) {
            suppressFinish = true
            animator.cancel()
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val fade = (1f - progress).coerceIn(0f, 1f)
        paint.color = Color.rgb(0, 92, 158)
        paint.alpha = (58 * fade).toInt()
        canvas.drawOval(RectF(-width * .15f, height - dp(74f), width * 1.15f, height + dp(26f)), paint)
        repeat(26) { index ->
            val delay = (index % 7) * .035f
            val local = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@repeat
            val seed = ((index * 47) % 101) / 101f
            val startX = width * (.06f + seed * .88f)
            val sway = sin((local * 2.4f + index) * Math.PI).toFloat() * dp(15f + index % 4)
            val rise = dp(48f + (index % 6) * 13f) * local
            paint.color = colors[index % colors.size]
            paint.alpha = (sin(local * Math.PI).toFloat().coerceAtLeast(0f) * 230).toInt()
            canvas.drawCircle(startX + sway, height - dp(13f + index % 4) - rise, dp(2.2f + index % 3), paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
