package id.waspadai.app.core.trigger

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.waspadai.app.MainActivity
import id.waspadai.app.WaspadAIApplication
import kotlinx.coroutines.launch

class QuickCaptureActivity : ComponentActivity() {
    private val captureConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                this,
                QuickCaptureService.intent(this, result.resultCode, data),
            )
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            QuickCaptureResultBus.results.collect { result ->
                when (result) {
                    is QuickCaptureResult.Success -> {
                        (application as WaspadAIApplication).pendingTriggerStore.put(result.trigger)
                        startActivity(
                            Intent(this@QuickCaptureActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(MainActivity.EXTRA_CONSUME_PENDING_TRIGGER, true)
                            }
                        )
                    }
                    is QuickCaptureResult.Failure ->
                        Toast.makeText(this@QuickCaptureActivity, result.message, Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
        if (savedInstanceState == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            captureConsent.launch(manager.createScreenCaptureIntent())
        }
    }
}
