package id.waspadai.app.core.trigger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.waspadai.app.MainActivity
import id.waspadai.app.R
import id.waspadai.app.core.capture.MediaProjectionController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultCode == 0 || data == null) {
            QuickCaptureResultBus.publish(QuickCaptureResult.Failure("Izin tangkapan layar tidak tersedia."))
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                delay(CAPTURE_DELAY_MS)
                val bytes = MediaProjectionController(this@QuickCaptureService).capturePng(resultCode, data)
                QuickCaptureResultBus.publish(
                    QuickCaptureResult.Success(
                        PendingVerificationTrigger(
                            listOf(
                                CapturedContext.Image(
                                    imageBytes = bytes,
                                    contentType = "image/png",
                                    fileName = "quick-capture-${timestamp()}.png",
                                    source = TriggerSource.QUICK_SETTINGS,
                                )
                            )
                        )
                    )
                )
            } catch (error: Throwable) {
                QuickCaptureResultBus.publish(
                    QuickCaptureResult.Failure(error.message ?: "Tangkapan layar belum berhasil.")
                )
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("WaspadAI sedang menangkap layar")
        .setContentText("Gambar belum dikirim dan akan dibuka sebagai preview.")
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

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tangkapan cepat", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        private const val CHANNEL_ID = "waspadai_quick_capture"
        private const val NOTIFICATION_ID = 4202
        private const val CAPTURE_DELAY_MS = 450L
        private const val EXTRA_RESULT_CODE = "quick_result_code"
        private const val EXTRA_DATA = "quick_result_data"

        fun intent(context: Context, resultCode: Int, data: Intent) =
            Intent(context, QuickCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
    }
}
