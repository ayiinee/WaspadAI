package id.waspadai.app.core.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import id.waspadai.app.MainActivity
import id.waspadai.app.R
import id.waspadai.app.core.capture.CaptureEvent
import id.waspadai.app.core.capture.CaptureResultBus
import id.waspadai.app.core.capture.MediaProjectionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatingVerifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var cropView: View? = null
    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null
    private var isCapturing = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CaptureResultBus.publish(CaptureEvent.Stopped)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            CaptureResultBus.publish(CaptureEvent.Failure("Izin tampil di atas aplikasi lain belum aktif."))
            stopSelf()
            return START_NOT_STICKY
        }
        projectionResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0).orZero()
        projectionData = intent?.getParcelableExtraCompat(EXTRA_DATA)
        if (projectionResultCode == 0 || projectionData == null) {
            CaptureResultBus.publish(CaptureEvent.Failure("Izin tangkapan layar belum tersedia. Aktifkan overlay lagi."))
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubble()
        removeCropSelector()
        scope.cancel()
        projectionData = null
        super.onDestroy()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val view = buildBubbleView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 36
            y = 220
        }
        makeDraggable(view, params)
        bubbleView = view
        windowManager.addView(view, params)
    }

    private fun buildBubbleView(): View {
        return TextView(this).apply {
            text = "W"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 118, 191))
                setStroke(dp(2), Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            width = dp(64)
            height = dp(64)
            minWidth = dp(64)
            minHeight = dp(64)
            elevation = dp(8).toFloat()
            contentDescription = "Buka seleksi area WaspadAI"
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    moved = moved || kotlin.math.abs(deltaX) > dp(6) || kotlin.math.abs(deltaY) > dp(6)
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()
                    bubbleView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showCropSelector()
                    true
                }
                else -> true
            }
        }
    }

    private fun showCropSelector() {
        if (cropView != null || isCapturing) return
        removeBubble()
        val selector = CropSelectionView(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                selector,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )
            addView(buildCropControls(selector), cropControlsLayoutParams())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        cropView = root
        windowManager.addView(root, params)
    }

    private fun buildCropControls(selector: CropSelectionView): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.WHITE)
            }
            addView(TextView(this@FloatingVerifyService).apply {
                text = "Pilih area yang ingin diperiksa"
                setTextColor(Color.rgb(21, 58, 82))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@FloatingVerifyService).apply {
                text = "Geser kotak biru, lalu kirim area."
                setTextColor(Color.rgb(85, 115, 131))
                textSize = 12f
                setPadding(0, dp(3), 0, dp(8))
            })
            addView(LinearLayout(this@FloatingVerifyService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(Button(this@FloatingVerifyService).apply {
                    text = "Batal"
                    setOnClickListener {
                        removeCropSelector()
                        showBubble()
                    }
                })
                addView(Button(this@FloatingVerifyService).apply {
                    text = "Kirim area"
                    setOnClickListener { captureOneFrame(selector.selectedRect()) }
                })
            })
        }

    private fun cropControlsLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = dp(18)
            rightMargin = dp(18)
            bottomMargin = dp(28)
        }

    private fun captureOneFrame(cropRect: Rect) {
        if (isCapturing) return
        val data = projectionData ?: return
        isCapturing = true
        removeCropSelector()
        scope.launch {
            delay(180)
            runCatching {
                MediaProjectionController(this@FloatingVerifyService).capturePng(
                    projectionResultCode,
                    data,
                    cropRect,
                )
            }.onSuccess { bytes ->
                CaptureResultBus.publish(
                    CaptureEvent.Success(
                        imageBytes = bytes,
                        contentType = "image/png",
                        fileName = "overlay-capture-${timestamp()}.png",
                    )
                )
                openApp()
            }.onFailure { error ->
                CaptureResultBus.publish(
                    CaptureEvent.Failure(
                        error.message ?: "Tangkapan layar belum berhasil. Coba lagi atau gunakan upload gambar manual."
                    )
                )
                openApp()
            }
            stopSelf()
        }
    }

    private fun removeBubble() {
        bubbleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        bubbleView = null
    }

    private fun removeCropSelector() {
        cropView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        cropView = null
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("WaspadAI overlay aktif")
        .setContentText("Tekan ikon W untuk memilih area layar yang akan diperiksa.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WaspadAI overlay",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun Int?.orZero(): Int = this ?: 0

    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    companion object {
        private const val CHANNEL_ID = "waspadai_overlay_capture"
        private const val NOTIFICATION_ID = 401
        private const val ACTION_STOP = "id.waspadai.app.overlay.STOP"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, FloatingVerifyService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingVerifyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

private class CropSelectionView(context: Context) : View(context) {
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(118, 0, 0, 0)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 118, 191)
        style = Paint.Style.STROKE
        strokeWidth = dp(3).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(42, 0, 118, 191)
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val selection = RectF()
    private var mode = DragMode.None
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        if (selection.isEmpty) {
            val horizontalInset = width * 0.12f
            val top = height * 0.22f
            selection.set(horizontalInset, top, width - horizontalInset, top + height * 0.32f)
        }
    }

    override fun onDraw(canvas: Canvas) {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                mode = dragModeFor(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (mode) {
                    DragMode.Move -> selection.offset(dx, dy)
                    DragMode.TopLeft -> {
                        selection.left += dx
                        selection.top += dy
                    }
                    DragMode.TopRight -> {
                        selection.right += dx
                        selection.top += dy
                    }
                    DragMode.BottomLeft -> {
                        selection.left += dx
                        selection.bottom += dy
                    }
                    DragMode.BottomRight -> {
                        selection.right += dx
                        selection.bottom += dy
                    }
                    DragMode.None -> Unit
                }
                normalizeSelection()
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = DragMode.None
                return true
            }
        }
        return true
    }

    fun selectedRect(): Rect {
        normalizeSelection()
        return Rect(
            selection.left.toInt(),
            selection.top.toInt(),
            selection.right.toInt(),
            selection.bottom.toInt(),
        )
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

    private fun distanceTo(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class DragMode {
        None,
        Move,
        TopLeft,
        TopRight,
        BottomLeft,
        BottomRight,
    }
}
