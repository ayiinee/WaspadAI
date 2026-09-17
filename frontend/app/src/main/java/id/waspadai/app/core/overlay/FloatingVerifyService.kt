package id.waspadai.app.core.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FloatingVerifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.rgb(0, 118, 191))
            }
        }
        val title = TextView(this).apply {
            text = "WaspadAI"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        val verify = bubbleButton("Verify").apply {
            setOnClickListener { captureOneFrame() }
        }
        val stop = bubbleButton("Stop").apply {
            setOnClickListener {
                CaptureResultBus.publish(CaptureEvent.Stopped)
                stopSelf()
            }
        }
        actions.addView(verify)
        actions.addView(stop)
        container.addView(title)
        container.addView(actions)
        return container
    }

    private fun bubbleButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 12f
        minHeight = 0
        minWidth = 0
        setPadding(16, 4, 16, 4)
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    bubbleView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun captureOneFrame() {
        if (isCapturing) return
        val data = projectionData ?: return
        isCapturing = true
        removeBubble()
        scope.launch {
            runCatching {
                MediaProjectionController(this@FloatingVerifyService).capturePng(projectionResultCode, data)
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

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("WaspadAI overlay aktif")
        .setContentText("Tekan Verify pada bubble untuk mengambil satu screenshot.")
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
