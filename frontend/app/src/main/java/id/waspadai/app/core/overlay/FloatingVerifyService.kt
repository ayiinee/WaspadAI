package id.waspadai.app.core.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import id.waspadai.app.MainActivity
import id.waspadai.app.R
import id.waspadai.app.WaspadAIApplication
import id.waspadai.app.core.capture.CaptureEvent
import id.waspadai.app.core.capture.CaptureResultBus
import id.waspadai.app.core.capture.MediaProjectionController
import id.waspadai.app.core.capture.OverlayChatTurn
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Overlay Tanya Area. Gambar hanya disimpan di memori selama service aktif. */
class FloatingVerifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ui by lazy { TanyaAreaUiFactory(this) }
    private lateinit var windowManager: WindowManager
    private lateinit var submitImage: SubmitImageVerificationUseCase

    private var bubbleView: View? = null
    private var trashView: View? = null
    private var trashCircleView: View? = null
    private var trashHighlighted = false
    private var cropView: View? = null
    private var panelView: View? = null
    private var transitionView: View? = null
    private var projectionResultCode = 0
    private var projectionData: Intent? = null
    private var isCapturing = false
    private var isProcessing = false
    private var isPanelMinimized = false

    private var fullCaptureBytes: ByteArray? = null
    private var selectedImageBytes: ByteArray? = null
    private var selectedFileName: String? = null
    private var lastQuestion: String? = null
    private var errorMessage: String? = null
    private var feedback: Feedback? = null
    private val conversation = mutableListOf<ChatEntry>()
    private var panelX = 0
    private var panelY = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        submitImage = SubmitImageVerificationUseCase(
            (application as WaspadAIApplication).verificationRepository
        )
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            CaptureResultBus.publish(CaptureEvent.Stopped)
            clearSession()
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
            CaptureResultBus.publish(CaptureEvent.Failure("Izin tangkapan layar belum tersedia. Aktifkan Tanya Area lagi."))
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubble()
        removeTrashTarget()
        removeCropSelector()
        removePanel()
        removeTransitionEffect()
        clearSession()
        scope.cancel()
        projectionData = null
        super.onDestroy()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        removePanel()
        val view = ImageView(this).apply {
            setImageResource(R.drawable.waspadai_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.WHITE, 40, Color.WHITE, 2)
            clipToOutline = true
            setElevation(dp(8).toFloat())
            setContentDescription("Buka Tanya Area")
        }
        val params = overlayParams(dp(62), dp(62), focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(220)
        }
        makeBubbleDraggable(view, params)
        bubbleView = view
        windowManager.addView(view, params)
    }

    private fun makeBubbleDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var overTrash = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    overTrash = false
                    showTrashTarget()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchX
                    val deltaY = event.rawY - touchY
                    moved = moved || abs(deltaX) > dp(6) || abs(deltaY) > dp(6)
                    val (screenWidth, screenHeight) = screenSize()
                    val naturalX = (initialX + deltaX.toInt()).coerceIn(0, screenWidth - dp(BUBBLE_SIZE_DP))
                    val naturalY = (initialY + deltaY.toInt()).coerceIn(0, screenHeight - dp(BUBBLE_SIZE_DP))
                    val bubbleCenterX = naturalX + dp(BUBBLE_SIZE_DP) / 2f
                    val bubbleCenterY = naturalY + dp(BUBBLE_SIZE_DP) / 2f
                    val (targetX, targetY) = trashCenterOnScreen()
                    val distance = distanceBetween(bubbleCenterX, bubbleCenterY, targetX, targetY)
                    val magnetRadius = dp(TRASH_MAGNET_RADIUS_DP).toFloat()
                    val magnetStrength = if (distance < magnetRadius) {
                        val proximity = 1f - distance / magnetRadius
                        (.16f + proximity * .84f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    params.x = lerp(naturalX.toFloat(), targetX - dp(BUBBLE_SIZE_DP) / 2f, magnetStrength)
                        .toInt()
                        .coerceIn(0, screenWidth - dp(BUBBLE_SIZE_DP))
                    params.y = lerp(naturalY.toFloat(), targetY - dp(BUBBLE_SIZE_DP) / 2f, magnetStrength)
                        .toInt()
                        .coerceIn(0, screenHeight - dp(BUBBLE_SIZE_DP))
                    bubbleView?.let { windowManager.updateViewLayout(it, params) }
                    overTrash = distance <= dp(TRASH_DROP_RADIUS_DP)
                    setTrashHighlighted(overTrash)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val shouldClose = moved && overTrash
                    when {
                        shouldClose -> animateBubbleDismiss(view)
                        !moved && selectedImageBytes != null -> {
                            removeTrashTarget()
                            isPanelMinimized = false
                            showChatPanel()
                        }
                        !moved -> {
                            removeTrashTarget()
                            showCropSelector()
                        }
                        else -> removeTrashTarget()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeTrashTarget()
                    true
                }
                else -> true
            }
        }
    }

    private fun showTrashTarget() {
        if (trashView != null) return
        val target = TextView(this).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 35f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(ERROR_RED, 48, Color.WHITE, 2)
            contentDescription = "Geser ke sini untuk menutup Tanya Area"
        }
        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                target,
                FrameLayout.LayoutParams(dp(TRASH_CIRCLE_SIZE_DP), dp(TRASH_CIRCLE_SIZE_DP), Gravity.CENTER),
            )
        }
        val params = overlayParams(dp(TRASH_WINDOW_SIZE_DP), dp(TRASH_WINDOW_SIZE_DP), focusable = false).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TRASH_BOTTOM_MARGIN_DP)
        }
        trashHighlighted = false
        trashCircleView = target
        trashView = root
        windowManager.addView(root, params)
    }

    private fun setTrashHighlighted(highlighted: Boolean) {
        if (trashHighlighted == highlighted) return
        trashHighlighted = highlighted
        if (highlighted) trashCircleView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        trashCircleView?.animate()
            ?.scaleX(if (highlighted) 1.2f else 1f)
            ?.scaleY(if (highlighted) 1.2f else 1f)
            ?.alpha(if (highlighted) 1f else .78f)
            ?.setDuration(140L)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }

    private fun trashCenterOnScreen(): Pair<Float, Float> {
        trashCircleView?.takeIf { it.width > 0 && it.height > 0 }?.let { circle ->
            val location = IntArray(2)
            circle.getLocationOnScreen(location)
            return location[0] + circle.width / 2f to location[1] + circle.height / 2f
        }
        val (width, height) = screenSize()
        return width / 2f to height - dp(TRASH_BOTTOM_MARGIN_DP) - dp(TRASH_WINDOW_SIZE_DP) / 2f
    }

    private fun animateBubbleDismiss(view: View) {
        val (effectX, effectY) = trashCenterOnScreen()
        setTrashHighlighted(true)
        view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(210L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                removeBubble()
                removeTrashTarget()
                showDismissEffect(effectX, effectY)
            }
            .start()
    }

    private fun showDismissEffect(originX: Float, originY: Float) {
        removeTransitionEffect()
        val effect = TanyaAreaDismissEffectView(this, originX, originY) { completedView ->
            if (transitionView === completedView) {
                removeTransitionEffect()
                closeOverlay()
            }
        }
        val params = overlayParams(-1, -1, focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        transitionView = effect
        windowManager.addView(effect, params)
    }

    private fun showCropSelector() {
        if (cropView != null || isCapturing || isProcessing) return
        removeBubble()
        removePanel()
        // Saat memilih ulang, gunakan frame yang sama agar tidak mengambil layar diam-diam lagi.
        val selector = TanyaAreaCropSelectionView(this, fullCaptureBytes)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(selector, FrameLayout.LayoutParams(-1, -1))
            addView(buildCropControls(selector), cropControlsLayoutParams())
        }
        val params = overlayParams(-1, -1, focusable = true).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        cropView = root
        windowManager.addView(root, params)
    }

    private fun buildCropControls(selector: TanyaAreaCropSelectionView): View =
        ui.cropControls(
            selector = selector,
            onCancel = {
                removeCropSelector()
                showBubble()
            },
            onContinue = ::preparePreview,
        )

    private fun preparePreview(cropRect: Rect) {
        if (isCapturing) return
        if (cropRect.width() < dp(MIN_SELECTION_DP) || cropRect.height() < dp(MIN_SELECTION_DP)) {
            Toast.makeText(this, "Area terlalu kecil. Perbesar bingkai sebelum melanjutkan.", Toast.LENGTH_LONG).show()
            return
        }
        isCapturing = true
        removeCropSelector()
        showStatusPanel("Menyiapkan pratinjau…", "Gambar belum dikirim untuk dianalisis.")
        scope.launch {
            try {
                val fullBytes = fullCaptureBytes ?: run {
                    // Android 14+ mengharuskan resultData MediaProjection dipakai tepat satu kali.
                    val data = projectionData?.also { projectionData = null }
                        ?: error("Izin berbagi layar sudah berakhir.")
                    // Jangan ikut menangkap panel loading milik Tanya Area.
                    delay(250)
                    removePanel()
                    delay(CAPTURE_START_DELAY_MS)
                    MediaProjectionController(this@FloatingVerifyService).capturePng(
                        projectionResultCode,
                        data,
                        cropRect = null,
                    ).also { fullCaptureBytes = it }
                }
                val cropped = withContext(Dispatchers.Default) { cropPng(fullBytes, cropRect) }
                if (cropped.isEmpty()) error("Area terpilih kosong. Pilih area lain.")
                selectedImageBytes = cropped
                selectedFileName = "tanya-area-${timestamp()}.png"
                errorMessage = null
                showPreviewPanel()
            } catch (error: Throwable) {
                val requiresFreshPermission = fullCaptureBytes == null
                errorMessage = if (requiresFreshPermission) {
                    "Izin berbagi layar sudah berakhir atau telah digunakan. Aktifkan ulang Tanyain untuk mengambil area baru."
                } else {
                    error.message ?: "Pratinjau belum berhasil dibuat. Coba pilih ulang area."
                }
                showFailurePanel(errorMessage.orEmpty(), requiresFreshPermission)
            } finally {
                isCapturing = false
            }
        }
    }

    private fun showPreviewPanel() {
        val bytes = selectedImageBytes ?: return
        val content = panelShell("Pratinjau Tanya Area") { body ->
            body.addView(previewImage(bytes, dp(190)))
            body.addView(label("Pastikan area sudah tepat. Gambar baru dikirim setelah kamu menekan tombol di bawah.", 12f, MUTED).apply {
                setPadding(0, dp(10), 0, dp(8))
            })
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    actionButton("Atur ulang", secondary = true) {
                        removePanel()
                        showCropSelector()
                    },
                    balancedButtonParams(endMargin = 8),
                )
                addView(
                    actionButton("Kirim area") { analyze(null, appendQuestion = false) },
                    balancedButtonParams(startMargin = 8),
                )
            })
        }
        showPanel(content, height = WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun analyze(question: String?, appendQuestion: Boolean) {
        val bytes = selectedImageBytes ?: return
        if (isProcessing) return
        val cleanQuestion = question?.trim()?.takeIf { it.isNotEmpty() }
        if (question != null && cleanQuestion == null) {
            Toast.makeText(this, "Tulis pertanyaan terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        if (appendQuestion && cleanQuestion != null) conversation += ChatEntry(true, cleanQuestion)
        lastQuestion = cleanQuestion
        feedback = null
        errorMessage = null
        isProcessing = true
        isPanelMinimized = false
        showChatPanel(animateEntrance = true)
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    submitImage(
                        imageBytes = bytes,
                        contentType = "image/png",
                        fileName = selectedFileName ?: "tanya-area.png",
                        question = cleanQuestion,
                        source = TriggerSource.FLOATING_OVERLAY,
                    )
                }
            }.getOrElse { AppResult.Failure(it.message ?: "Analisis gagal dijalankan.") }
            when (result) {
                is AppResult.Success -> {
                    conversation += ChatEntry(
                        isUser = false,
                        text = result.value.toTanyaAreaChatAnswer(),
                        result = result.value,
                    )
                    errorMessage = null
                    fullCaptureBytes = null
                }
                is AppResult.Failure -> errorMessage = result.message
            }
            isProcessing = false
            if (isPanelMinimized) showBubble() else showChatPanel()
        }
    }

    private fun showChatPanel(animateEntrance: Boolean = false) {
        val bytes = selectedImageBytes ?: return
        isPanelMinimized = false
        removeBubble()
        if (animateEntrance) showChatEntranceEffect()
        val content = panelShell("WaspadAI", showWindowControls = true) { body ->
            body.addView(previewImage(bytes, dp(88)))
            body.addView(label("Percakapan", 12f, MUTED, bold = true).apply {
                setPadding(0, dp(10), 0, dp(5))
            })
            val transcript = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                conversation.forEach { entry -> addView(chatBubble(entry)) }
                if (isProcessing) {
                    addView(LinearLayout(this@FloatingVerifyService).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        background = rounded(SOFT_BLUE, 12)
                        addView(
                            TanyaAreaLoadingView(this@FloatingVerifyService),
                            LinearLayout.LayoutParams(dp(28), dp(28)),
                        )
                        addView(label("Menganalisis area…", 13f, DEEP_BLUE).apply {
                            setPadding(dp(10), 0, 0, 0)
                        })
                    })
                }
                errorMessage?.let { message ->
                    addView(label(message, 13f, ERROR_RED).apply {
                        setPadding(dp(10), dp(9), dp(10), dp(9))
                        background = rounded(ERROR_SOFT, 12)
                    })
                    addView(actionButton("Coba lagi", secondary = true) {
                        analyze(lastQuestion, appendQuestion = false)
                    })
                }
            }
            body.addView(ScrollView(this).apply {
                isFillViewport = true
                addView(transcript)
            }, LinearLayout.LayoutParams(-1, 0, 1f))

            if (conversation.any { !it.isUser }) {
                body.addView(label("Apakah jawaban ini membantu?", 12f, MUTED).apply {
                    setPadding(0, dp(9), 0, dp(5))
                })
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        feedbackButton("Membantu", Feedback.Helpful),
                        balancedButtonParams(endMargin = 8),
                    )
                    addView(
                        feedbackButton("Kurang tepat", Feedback.Inaccurate),
                        balancedButtonParams(startMargin = 8),
                    )
                })
            }
            body.addView(buildChatComposer(), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })
        }
        showPanel(content, height = min(dp(610), screenSize().second - dp(86)))
    }

    private fun feedbackButton(text: String, value: Feedback): Button =
        actionButton(text, secondary = false) {
            feedback = value
            Toast.makeText(this, "Terima kasih atas masukannya.", Toast.LENGTH_SHORT).show()
            showChatPanel()
        }.apply {
            textSize = 11f
            background = rounded(
                if (feedback == value) SELECTED_BLUE else BRAND_BLUE,
                12,
            )
            setTextColor(Color.WHITE)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
        }

    private fun buildChatComposer(): View =
        ui.chatComposer(enabled = !isProcessing) { analyze(it, appendQuestion = true) }

    private fun balancedButtonParams(
        startMargin: Int = 0,
        endMargin: Int = 0,
    ) = ui.balancedButtonParams(startMargin, endMargin)

    private fun chatBubble(entry: ChatEntry): TextView = ui.chatBubble(entry.isUser, entry.text)

    private fun showStatusPanel(title: String, subtitle: String) {
        val content = panelShell("Tanya Area") { body ->
            body.gravity = Gravity.CENTER
            body.addView(
                TanyaAreaLoadingView(this),
                LinearLayout.LayoutParams(dp(46), dp(46)),
            )
            body.addView(label(title, 15f, DEEP_BLUE, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(4))
            })
            body.addView(label(subtitle, 12f, MUTED).apply { gravity = Gravity.CENTER })
        }
        showPanel(content, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showFailurePanel(message: String, requiresFreshPermission: Boolean = false) {
        val content = panelShell("Tanya Area") { body ->
            body.addView(label(message, 13f, ERROR_RED).apply {
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(ERROR_SOFT, 12)
            })
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
                addView(
                    actionButton("Tutup", secondary = true) { closeOverlay() },
                    balancedButtonParams(endMargin = 8),
                )
                addView(
                    actionButton(if (requiresFreshPermission) "Aktifkan ulang" else "Pilih ulang") {
                        if (requiresFreshPermission) {
                            requestFreshScreenPermission(message)
                        } else {
                            removePanel()
                            showCropSelector()
                        }
                    },
                    balancedButtonParams(startMargin = 8),
                )
            })
        }
        showPanel(content, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun panelShell(
        title: String,
        showWindowControls: Boolean = false,
        buildBody: (LinearLayout) -> Unit,
    ): View = ui.panelShell(
        title = title,
        fillBody = showWindowControls,
        onHeaderCreated = ::makePanelDraggable,
        buildHeaderActions = {
            if (showWindowControls) {
                addView(windowControlButton(TanyaAreaWindowControl.Minimize, "Kecilkan panel") {
                    isPanelMinimized = true
                    removePanel()
                    showBubble()
                })
                addView(windowControlButton(TanyaAreaWindowControl.Maximize, "Buka dalam aplikasi") { openFullApp() })
            }
            addView(windowControlButton(TanyaAreaWindowControl.Close, "Tutup Tanya Area") { closeOverlay() })
        },
        buildBody = buildBody,
    )

    private fun showPanel(view: View, height: Int) {
        removePanel()
        removeBubble()
        val width = min(dp(370), screenSize().first - dp(24))
        if (panelX == 0) panelX = (screenSize().first - width) / 2
        if (panelY == 0) panelY = dp(72)
        val params = overlayParams(width, height, focusable = true).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelX
            y = panelY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        panelView = view
        view.alpha = 0f
        view.scaleX = .94f
        view.scaleY = .94f
        view.translationY = dp(18).toFloat()
        windowManager.addView(view, params)
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun makePanelDraggable(handle: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            val params = panelView?.layoutParams as? WindowManager.LayoutParams
                ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (screenWidth, screenHeight) = screenSize()
                    params.x = (startX + (event.rawX - touchX).toInt()).coerceIn(0, screenWidth - params.width)
                    params.y = (startY + (event.rawY - touchY).toInt()).coerceIn(0, screenHeight - dp(80))
                    panelX = params.x
                    panelY = params.y
                    panelView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun openFullApp() {
        selectedImageBytes?.let { bytes ->
            CaptureResultBus.publish(
                CaptureEvent.Conversation(
                    imageBytes = bytes.copyOf(),
                    contentType = "image/png",
                    fileName = selectedFileName ?: "tanya-area.png",
                    turns = conversation.map { entry ->
                        OverlayChatTurn(entry.isUser, entry.text, entry.result)
                    },
                    source = TriggerSource.FLOATING_OVERLAY,
                )
            )
        }
        removePanel()
        isPanelMinimized = true
        showBubble()
        launchVerificationApp()
    }

    private fun requestFreshScreenPermission(message: String) {
        CaptureResultBus.publish(CaptureEvent.PermissionExpired(message))
        removePanel()
        clearSession()
        launchVerificationApp()
        stopSelf()
    }

    private fun launchVerificationApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_FULL, true)
        })
    }

    private fun previewImage(bytes: ByteArray, imageHeight: Int): ImageView =
        ui.previewImage(bytes, imageHeight)

    private fun actionButton(text: String, secondary: Boolean = false, onClick: () -> Unit): Button =
        ui.actionButton(text, secondary, onClick)

    private fun windowControlButton(
        control: TanyaAreaWindowControl,
        description: String,
        onClick: () -> Unit,
    ): View =
        ui.windowControlButton(control, description, onClick)

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        ui.label(text, size, color, bold)

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0) =
        ui.rounded(color, radiusDp, strokeColor, strokeDp)

    private fun cropPng(bytes: ByteArray, rect: Rect): ByteArray {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Tangkapan layar tidak dapat dibaca.")
        val left = rect.left.coerceIn(0, source.width - 1)
        val top = rect.top.coerceIn(0, source.height - 1)
        val right = rect.right.coerceIn(left + 1, source.width)
        val bottom = rect.bottom.coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        val output = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (cropped !== source) cropped.recycle()
        source.recycle()
        return output.toByteArray()
    }

    private fun clearSession() {
        fullCaptureBytes = null
        selectedImageBytes = null
        selectedFileName = null
        conversation.clear()
        feedback = null
        errorMessage = null
        lastQuestion = null
        isPanelMinimized = false
    }

    private fun showChatEntranceEffect() {
        removeTransitionEffect()
        val effect = TanyaAreaEntranceEffectView(this) { completedView ->
            if (transitionView === completedView) removeTransitionEffect()
        }
        val params = overlayParams(-1, -1, focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        transitionView = effect
        windowManager.addView(effect, params)
    }

    private fun closeOverlay() {
        CaptureResultBus.publish(CaptureEvent.Stopped)
        clearSession()
        stopSelf()
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun removeTrashTarget() {
        trashView?.let { runCatching { windowManager.removeView(it) } }
        trashView = null
        trashCircleView = null
        trashHighlighted = false
    }

    private fun removeCropSelector() {
        cropView?.let { runCatching { windowManager.removeView(it) } }
        cropView = null
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
    }

    private fun removeTransitionEffect() {
        transitionView?.let { runCatching { windowManager.removeView(it) } }
        transitionView = null
    }

    private fun cropControlsLayoutParams() = FrameLayout.LayoutParams(-1, -2).apply {
        gravity = Gravity.BOTTOM
        leftMargin = dp(14)
        rightMargin = dp(14)
        bottomMargin = dp(24)
    }

    private fun overlayParams(width: Int, height: Int, focusable: Boolean) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
        android.graphics.PixelFormat.TRANSLUCENT,
    )

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Tanya Area aktif")
        .setContentText("Ketuk ikon ? untuk memilih area layar. Tidak ada gambar yang dikirim otomatis.")
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tanya Area", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
    private fun distanceBetween(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    private fun Int?.orZero(): Int = this ?: 0

    private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(key)

    private data class ChatEntry(
        val isUser: Boolean,
        val text: String,
        val result: VerificationResult? = null,
    )
    private enum class Feedback { Helpful, Inaccurate }

    companion object {
        const val EXTRA_OPEN_FULL = "id.waspadai.app.extra.OPEN_TANYA_AREA"
        private const val CHANNEL_ID = "waspadai_overlay_capture"
        private const val NOTIFICATION_ID = 401
        private const val CAPTURE_START_DELAY_MS = 350L
        private const val MIN_SELECTION_DP = 80
        private const val BUBBLE_SIZE_DP = 62
        private const val TRASH_CIRCLE_SIZE_DP = 76
        private const val TRASH_WINDOW_SIZE_DP = 116
        private const val TRASH_BOTTOM_MARGIN_DP = 16
        private const val TRASH_MAGNET_RADIUS_DP = 132
        private const val TRASH_DROP_RADIUS_DP = 62
        private const val ACTION_STOP = "id.waspadai.app.overlay.STOP"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"
        private val BRAND_BLUE = TanyaAreaStyle.BRAND_BLUE
        private const val SELECTED_BLUE = 0xFF003F70.toInt()
        private val DEEP_BLUE = TanyaAreaStyle.DEEP_BLUE
        private val BORDER_BLUE = TanyaAreaStyle.BORDER_BLUE
        private val SOFT_BLUE = TanyaAreaStyle.SOFT_BLUE
        private val MUTED = TanyaAreaStyle.MUTED
        private val ERROR_RED = TanyaAreaStyle.ERROR_RED
        private val ERROR_SOFT = TanyaAreaStyle.ERROR_SOFT

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, FloatingVerifyService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FloatingVerifyService::class.java).apply { action = ACTION_STOP })
        }
    }
}

private enum class WindowControl { Minimize, Maximize, Close }

private class WindowControlView(
    context: Context,
    private val control: WindowControl,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val half = dp(6.5f)
        when (control) {
            WindowControl.Minimize -> canvas.drawLine(cx - half, cy + dp(4f), cx + half, cy + dp(4f), paint)
            WindowControl.Maximize -> canvas.drawRoundRect(
                cx - half,
                cy - half,
                cx + half,
                cy + half,
                dp(1.5f),
                dp(1.5f),
                paint,
            )
            WindowControl.Close -> {
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, paint)
                canvas.drawLine(cx + half, cy - half, cx - half, cy + half, paint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class LegacyTanyaAreaEntranceEffectView(
    context: Context,
    private val onFinished: (LegacyTanyaAreaEntranceEffectView) -> Unit,
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
                if (!suppressFinish) onFinished(this@LegacyTanyaAreaEntranceEffectView)
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
        canvas.drawOval(
            RectF(-width * .15f, height - dp(74f), width * 1.15f, height + dp(26f)),
            paint,
        )

        repeat(26) { index ->
            val delay = (index % 7) * .035f
            val local = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@repeat
            val seed = ((index * 47) % 101) / 101f
            val startX = width * (.06f + seed * .88f)
            val sway = sin((local * 2.4f + index) * Math.PI).toFloat() * dp(15f + index % 4)
            val rise = dp(48f + (index % 6) * 13f) * local
            val alpha = (sin(local * Math.PI).toFloat().coerceAtLeast(0f) * 230).toInt()
            paint.color = colors[index % colors.size]
            paint.alpha = alpha
            canvas.drawCircle(
                startX + sway,
                height - dp(13f + index % 4) - rise,
                dp(2.2f + index % 3),
                paint,
            )
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class TanyaAreaDismissEffectView(
    context: Context,
    private val originX: Float,
    private val originY: Float,
    private val onFinished: (TanyaAreaDismissEffectView) -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(165, 34, 25)
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val colors = intArrayOf(
        Color.rgb(165, 34, 25),
        Color.rgb(232, 73, 63),
        Color.rgb(255, 220, 107),
        Color.rgb(0, 92, 158),
    )
    private var progress = 0f
    private var suppressFinish = false
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 560L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!suppressFinish) onFinished(this@TanyaAreaDismissEffectView)
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
        val centerX = originX
        val centerY = originY
        val fade = (1f - progress).coerceIn(0f, 1f)

        ringPaint.alpha = (210 * fade).toInt()
        canvas.drawCircle(centerX, centerY, dp(12f + progress * 52f), ringPaint)

        repeat(18) { index ->
            val angle = (Math.PI * 2.0 * index / 18.0 + (index % 3) * .13).toFloat()
            val travel = dp(24f + (index % 5) * 9f) * progress
            val lift = dp(13f) * progress * progress
            val particleFade = (1f - progress * progress).coerceIn(0f, 1f)
            paint.color = colors[index % colors.size]
            paint.alpha = (245 * particleFade).toInt()
            canvas.drawCircle(
                centerX + cos(angle) * travel,
                centerY + sin(angle) * travel - lift,
                dp(2.4f + index % 3),
                paint,
            )
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class LegacyTanyaAreaLoadingView(context: Context) : View(context) {
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
            canvas.drawCircle(
                centerX + cos(angle) * orbit,
                centerY + sin(angle) * orbit,
                radius,
                paint,
            )
        }
        bluePaint.alpha = 255
        canvas.drawCircle(centerX, centerY, min(width, height) * .09f, bluePaint)
    }
}

private class CropSelectionView(context: Context, backgroundBytes: ByteArray? = null) : View(context) {
    private val backgroundBitmap = backgroundBytes?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(118, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 118, 191)
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
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                null,
            )
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
        backgroundBitmap?.recycle()
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

    private fun distanceTo(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private enum class DragMode { None, Move, TopLeft, TopRight, BottomLeft, BottomRight }
}
