package id.waspadai.app.core.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.annotation.RequiresApi
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import id.waspadai.app.WaspadAIApplication
import id.waspadai.app.ui.theme.WaspadAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class WaspadAIVoiceInteractionService : VoiceInteractionService()

class WaspadAIVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        WaspadAIVoiceSession(applicationContext)
}

class WaspadAIRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onStopListening(listener: Callback) = Unit
    override fun onCancel(listener: Callback) = Unit
}

class WaspadAIVoiceSession(context: android.content.Context) : VoiceInteractionSession(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owners = AssistantViewTreeOwners()
    private val controller = AssistantVerificationController(
        repository = (context.applicationContext as WaspadAIApplication).verificationRepository,
        scope = scope,
    )

    override fun onCreate() {
        super.onCreate()
        setTheme(id.waspadai.app.R.style.Theme_Frontend)
    }

    override fun onCreateContentView(): View {
        val root = FrameLayout(context)
        root.setViewTreeLifecycleOwner(owners)
        root.setViewTreeViewModelStoreOwner(owners)
        root.setViewTreeSavedStateRegistryOwner(owners)
        root.addView(
            ComposeView(context).apply {
                setContent {
                    WaspadAITheme {
                        AssistantPanel(
                            controller = controller,
                            onOpenApp = ::openFullApp,
                            onClose = ::finish,
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        owners.start()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        controller.offerScreenshot(screenshot)
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        controller.offerText(AssistantContextExtractor.extract(structure, content))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onHandleAssist(state: AssistState) {
        if (state.isFocused) {
            controller.offerText(
                AssistantContextExtractor.extract(state.assistStructure, state.assistContent)
            )
        }
    }

    override fun onLockscreenShown() {
        controller.clear()
        hide()
    }

    override fun onHide() {
        owners.stop()
        controller.clear()
        super.onHide()
    }

    override fun onDestroy() {
        controller.clear()
        owners.destroy()
        scope.cancel()
        super.onDestroy()
    }

    private fun openFullApp() {
        context.startActivity(
            Intent(context, id.waspadai.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(id.waspadai.app.core.overlay.FloatingVerifyService.EXTRA_OPEN_FULL, true)
            }
        )
        finish()
    }
}
