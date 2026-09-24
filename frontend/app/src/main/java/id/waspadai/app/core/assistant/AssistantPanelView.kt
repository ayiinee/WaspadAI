package id.waspadai.app.core.assistant

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import id.waspadai.app.core.overlay.TanyaAreaCropSelectionView
import id.waspadai.app.core.overlay.TanyaAreaEntranceEffectView
import id.waspadai.app.core.overlay.TanyaAreaLoadingView
import id.waspadai.app.core.overlay.TanyaAreaStyle
import id.waspadai.app.core.overlay.TanyaAreaUiFactory
import id.waspadai.app.core.overlay.TanyaAreaWindowControl
import id.waspadai.app.core.overlay.toTanyaAreaChatAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min

/** System-assistant host using the exact same native View renderer as Floating Verify. */
class AssistantPanelView(
    context: Context,
    private val controller: AssistantVerificationController,
    scope: CoroutineScope,
    private val onOpenApp: () -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    private val ui = TanyaAreaUiFactory(context)
    private var feedback: Feedback? = null
    private var previousPhase: AssistantSessionPhase? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        scope.launch {
            controller.state.collectLatest(::render)
        }
    }

    private fun render(state: AssistantSessionState) {
        val animateEntrance = state.phase is AssistantSessionPhase.Submitting &&
            previousPhase !is AssistantSessionPhase.Submitting
        removeAllViews()
        when (val phase = state.phase) {
            AssistantSessionPhase.WaitingForContext -> showWaiting()
            is AssistantSessionPhase.PreviewImage -> showCropSelector(phase)
            is AssistantSessionPhase.ReviewImage -> showPreview(phase.imageBytes)
            is AssistantSessionPhase.PreviewText -> showTextPreview(phase.text)
            AssistantSessionPhase.Submitting -> showChat(state, processing = true, animateEntrance = animateEntrance)
            is AssistantSessionPhase.Result -> showChat(state, processing = false)
            is AssistantSessionPhase.Failure -> showFailure(state, phase.message)
            AssistantSessionPhase.Closed -> Unit
        }
        previousPhase = state.phase
    }

    private fun showCropSelector(phase: AssistantSessionPhase.PreviewImage) {
        setBackgroundColor(Color.TRANSPARENT)
        val selector = TanyaAreaCropSelectionView(context, phase.bitmap)
        addView(selector, LayoutParams(-1, -1))
        addView(
            ui.cropControls(
                selector = selector,
                onCancel = onClose,
                onContinue = {
                    selector.croppedPng()?.let(controller::selectImageArea)
                },
            ),
            LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = ui.dp(14)
                rightMargin = ui.dp(14)
                bottomMargin = ui.dp(24)
            },
        )
    }

    private fun showPreview(bytes: ByteArray) {
        showFloatingPanel(
            assistantPanelShell(
                title = "Pratinjau Tanya Area",
                buildHeaderActions = { addCloseControl() },
            ) { body ->
                body.addView(ui.previewImage(bytes, ui.dp(190)))
                body.addView(ui.label(
                    "Pastikan area sudah tepat. Gambar baru dikirim setelah kamu menekan tombol di bawah.",
                    12f,
                    TanyaAreaStyle.MUTED,
                ).apply { setPadding(0, ui.dp(10), 0, ui.dp(8)) })
                body.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        ui.actionButton("Atur ulang", secondary = true, onClick = controller::reselectImageArea),
                        ui.balancedButtonParams(endMargin = 8),
                    )
                    addView(
                        ui.actionButton("Kirim area", onClick = controller::confirmImage),
                        ui.balancedButtonParams(startMargin = 8),
                    )
                })
            },
        )
    }

    private fun showChat(
        state: AssistantSessionState,
        processing: Boolean,
        animateEntrance: Boolean = false,
    ) {
        val bytes = state.selectedImagePreview
        if (bytes == null) {
            showFailure(state, "Area layar tidak tersedia.")
            return
        }
        showFloatingPanel(
            assistantPanelShell(
                title = "WaspadAI",
                fillBody = true,
                buildHeaderActions = {
                    addView(ui.windowControlButton(TanyaAreaWindowControl.Minimize, "Kecilkan panel", onClose))
                    addView(ui.windowControlButton(TanyaAreaWindowControl.Maximize, "Buka dalam aplikasi", onOpenApp))
                    addCloseControl()
                },
            ) { body ->
                body.addView(ui.previewImage(bytes, ui.dp(88)))
                body.addView(ui.label("Percakapan", 12f, TanyaAreaStyle.MUTED, bold = true).apply {
                    setPadding(0, ui.dp(10), 0, ui.dp(5))
                })
                val transcript = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    state.conversation.forEach { turn ->
                        turn.question?.let { addView(ui.chatBubble(true, it)) }
                        addView(ui.chatBubble(false, turn.result.toTanyaAreaChatAnswer()))
                    }
                    if (processing) addView(loadingRow())
                }
                body.addView(ScrollView(context).apply {
                    isFillViewport = true
                    addView(transcript)
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                if (state.conversation.isNotEmpty()) addFeedbackControls(body)
                body.addView(ui.chatComposer(enabled = !processing, onSubmit = controller::askFollowUp).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(12) }
                })
            },
            height = min(ui.dp(610), resources.displayMetrics.heightPixels - ui.dp(86)),
        )
        if (animateEntrance) {
            val effect = TanyaAreaEntranceEffectView(context) { removeView(it) }
            addView(effect, LayoutParams(-1, -1))
        }
    }

    private fun loadingRow(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        background = ui.rounded(TanyaAreaStyle.SOFT_BLUE, 12)
        addView(TanyaAreaLoadingView(context), LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)))
        addView(ui.label("Menganalisis area…", 13f, TanyaAreaStyle.DEEP_BLUE).apply {
            setPadding(ui.dp(10), 0, 0, 0)
        })
    }

    private fun addFeedbackControls(body: LinearLayout) {
        body.addView(ui.label("Apakah jawaban ini membantu?", 12f, TanyaAreaStyle.MUTED).apply {
            setPadding(0, ui.dp(9), 0, ui.dp(5))
        })
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(feedbackButton("Membantu", Feedback.Helpful), ui.balancedButtonParams(endMargin = 8))
            addView(feedbackButton("Kurang tepat", Feedback.Inaccurate), ui.balancedButtonParams(startMargin = 8))
        })
    }

    private fun feedbackButton(text: String, value: Feedback) = ui.actionButton(text) {
        feedback = value
        Toast.makeText(context, "Terima kasih atas masukannya.", Toast.LENGTH_SHORT).show()
        render(controller.state.value)
    }.apply {
        textSize = 11f
        background = ui.rounded(
            if (feedback == value) 0xFF003F70.toInt() else TanyaAreaStyle.BRAND_BLUE,
            12,
        )
        setTextColor(Color.WHITE)
        minWidth = 0
        minimumWidth = 0
        setPadding(ui.dp(10), 0, ui.dp(10), 0)
    }

    private fun showFailure(state: AssistantSessionState, message: String) {
        showFloatingPanel(
            assistantPanelShell("Tanya Area", buildHeaderActions = { addCloseControl() }) { body ->
                body.addView(ui.label(message, 13f, TanyaAreaStyle.ERROR_RED).apply {
                    setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
                    background = ui.rounded(TanyaAreaStyle.ERROR_SOFT, 12)
                })
                body.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, ui.dp(8), 0, 0)
                    addView(ui.actionButton("Tutup", secondary = true, onClick = onClose), ui.balancedButtonParams(endMargin = 8))
                    val canRetry = state.selectedImagePreview != null
                    addView(
                        ui.actionButton(if (canRetry) "Coba lagi" else "Buka aplikasi") {
                            if (canRetry) controller.retryLastSubmission() else onOpenApp()
                        },
                        ui.balancedButtonParams(startMargin = 8),
                    )
                })
            },
        )
    }

    private fun showTextPreview(text: String) {
        showFloatingPanel(
            assistantPanelShell(
                title = "Tanya Area",
                fillBody = true,
                buildHeaderActions = { addCloseControl() },
            ) { body ->
                body.addView(ui.label("Teks yang terbaca dari layar", 15f, TanyaAreaStyle.DEEP_BLUE, bold = true))
                val input = EditText(context).apply {
                    setText(text)
                    textSize = 14f
                    setTextColor(TanyaAreaStyle.DEEP_BLUE)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 5
                    background = ui.rounded(Color.WHITE, 12, TanyaAreaStyle.BORDER_BLUE, 1)
                    setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
                }
                body.addView(input, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = ui.dp(10) })
                body.addView(ui.actionButton("Periksa sekarang") {
                    controller.updateText(input.text.toString())
                    controller.confirmText()
                }, LinearLayout.LayoutParams(-1, ui.dp(44)).apply { topMargin = ui.dp(10) })
            },
            height = min(ui.dp(610), resources.displayMetrics.heightPixels - ui.dp(86)),
        )
    }

    private fun showWaiting() {
        showFloatingPanel(
            assistantPanelShell("Tanya Area", buildHeaderActions = { addCloseControl() }) { body ->
                body.gravity = Gravity.CENTER
                body.addView(TanyaAreaLoadingView(context), LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)))
                body.addView(ui.label("Menunggu konteks layar…", 15f, TanyaAreaStyle.DEEP_BLUE, bold = true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, ui.dp(12), 0, ui.dp(4))
                })
                body.addView(ui.label("Tidak ada data yang dikirim otomatis.", 12f, TanyaAreaStyle.MUTED).apply {
                    gravity = Gravity.CENTER
                })
            },
        )
    }

    private fun LinearLayout.addCloseControl() {
        addView(ui.windowControlButton(TanyaAreaWindowControl.Close, "Tutup Tanya Area", onClose))
    }

    private fun assistantPanelShell(
        title: String,
        fillBody: Boolean = false,
        buildHeaderActions: LinearLayout.() -> Unit,
        buildBody: (LinearLayout) -> Unit,
    ): View = ui.panelShell(
        title = title,
        fillBody = fillBody,
        onHeaderCreated = ::makePanelDraggable,
        buildHeaderActions = buildHeaderActions,
        buildBody = buildBody,
    )

    private fun makePanelDraggable(handle: View) {
        var startX = 0f
        var startY = 0f
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            val panel = handle.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = panel.translationX
                    startY = panel.translationY
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val limitX = ((width - panel.width) / 2f).coerceAtLeast(0f)
                    val maxDown = (height - panel.bottom).toFloat().coerceAtLeast(0f)
                    panel.translationX = (startX + event.rawX - touchX).coerceIn(-limitX, limitX)
                    panel.translationY = (startY + event.rawY - touchY).coerceIn(-panel.top.toFloat(), maxDown)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun showFloatingPanel(view: View, height: Int = LayoutParams.WRAP_CONTENT) {
        setBackgroundColor(Color.TRANSPARENT)
        val width = min(ui.dp(370), resources.displayMetrics.widthPixels - ui.dp(24))
        addView(view, LayoutParams(width, height, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = ui.dp(72)
        })
        view.alpha = 0f
        view.scaleX = .94f
        view.scaleY = .94f
        view.translationY = ui.dp(18).toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private enum class Feedback { Helpful, Inaccurate }
}
