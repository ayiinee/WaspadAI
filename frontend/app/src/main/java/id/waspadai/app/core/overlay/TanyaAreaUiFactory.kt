package id.waspadai.app.core.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import id.waspadai.app.R
import id.waspadai.app.core.model.VerificationResult

/** Exact shared View toolkit for Floating Verify and the system assistant. */
class TanyaAreaUiFactory(private val context: Context) {
    fun panelShell(
        title: String,
        fillBody: Boolean = false,
        onHeaderCreated: (View) -> Unit = {},
        buildHeaderActions: LinearLayout.() -> Unit = {},
        buildBody: (LinearLayout) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.WHITE, 20, TanyaAreaStyle.BORDER_BLUE, 1)
        clipToOutline = true
        elevation = dp(12).toFloat()
        val header = FrameLayout(context).apply {
            setBackgroundColor(TanyaAreaStyle.BRAND_BLUE)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.community_header_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = .58f
                contentDescription = null
            }, FrameLayout.LayoutParams(-1, -1))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                addView(label(title, 17f, Color.WHITE, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                buildHeaderActions()
            }, FrameLayout.LayoutParams(-1, -1))
        }
        addView(header, LinearLayout.LayoutParams(-1, dp(58)))
        onHeaderCreated(header)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        addView(body, if (fillBody) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        buildBody(body)
    }

    fun cropControls(
        selector: TanyaAreaCropSelectionView,
        onCancel: () -> Unit,
        onContinue: (android.graphics.Rect) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Color.WHITE, 18, TanyaAreaStyle.BORDER_BLUE, 1)
        elevation = dp(8).toFloat()
        addView(label("Pilih area yang ingin ditanyakan", 17f, TanyaAreaStyle.DEEP_BLUE, bold = true))
        addView(label("Geser bingkai atau tarik titik sudutnya.", 13f, TanyaAreaStyle.MUTED).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(actionButton("Batal", secondary = true, onClick = onCancel).apply {
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }, balancedButtonParams(endMargin = 8))
            addView(actionButton("Kirim area") { onContinue(selector.selectedRect()) }.apply {
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }, balancedButtonParams(startMargin = 8))
        })
    }

    fun previewImage(bytes: ByteArray, imageHeight: Int): ImageView = ImageView(context).apply {
        setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(TanyaAreaStyle.SOFT_BLUE, 12)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, imageHeight)
        contentDescription = "Pratinjau area layar yang dipilih"
    }

    fun windowControlButton(
        control: TanyaAreaWindowControl,
        description: String,
        onClick: () -> Unit,
    ): View = TanyaAreaWindowControlView(context, control).apply {
        contentDescription = description
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(42))
        setOnClickListener { onClick() }
    }

    fun actionButton(text: String, secondary: Boolean = false, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        setTextColor(if (secondary) TanyaAreaStyle.BRAND_BLUE else Color.WHITE)
        background = rounded(
            if (secondary) Color.WHITE else TanyaAreaStyle.BRAND_BLUE,
            12,
            TanyaAreaStyle.BRAND_BLUE,
            1,
        )
        backgroundTintList = null
        stateListAnimator = null
        elevation = 0f
        setPadding(dp(10), 0, dp(10), 0)
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(40)
        minimumHeight = dp(40)
        setOnClickListener { onClick() }
    }

    fun chatBubble(isUser: Boolean, text: String): TextView = label(
        text = if (isUser) "Kamu\n$text" else "Tanya Area\n$text",
        size = 13f,
        color = if (isUser) Color.WHITE else TanyaAreaStyle.DEEP_BLUE,
    ).apply {
        setPadding(dp(11), dp(9), dp(11), dp(9))
        background = rounded(if (isUser) TanyaAreaStyle.BRAND_BLUE else TanyaAreaStyle.SOFT_BLUE, 12)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(7)
            if (isUser) leftMargin = dp(34) else rightMargin = dp(18)
        }
    }

    fun chatComposer(enabled: Boolean, onSubmit: (String) -> Unit): View {
        val input = EditText(context).apply {
            hint = "Tanyakan lebih lanjut…"
            textSize = 15f
            setTextColor(TanyaAreaStyle.DEEP_BLUE)
            setHintTextColor(TanyaAreaStyle.MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
            setPadding(dp(10), 0, dp(6), 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val send = ImageButton(context).apply {
            setImageResource(R.drawable.ic_tanya_area_send)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = oval(if (enabled) TanyaAreaStyle.BRAND_BLUE else 0xFF9FC8DD.toInt())
            backgroundTintList = null
            isEnabled = enabled
            contentDescription = "Kirim pertanyaan lanjutan"
            setOnClickListener {
                val value = input.text.toString()
                input.setText("")
                onSubmit(value)
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && send.isEnabled) {
                send.performClick()
                true
            } else false
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(Color.WHITE, 29, 0xFFC8DAE8.toInt(), 2)
            addView(input, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(send, LinearLayout.LayoutParams(dp(46), dp(46)))
        }
    }

    fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            if (strokeColor != null && strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun balancedButtonParams(startMargin: Int = 0, endMargin: Int = 0) =
        LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(startMargin)
            marginEnd = dp(endMargin)
        }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

enum class TanyaAreaWindowControl { Minimize, Maximize, Close }

private class TanyaAreaWindowControlView(
    context: Context,
    private val control: TanyaAreaWindowControl,
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
            TanyaAreaWindowControl.Minimize -> canvas.drawLine(cx - half, cy + dp(4f), cx + half, cy + dp(4f), paint)
            TanyaAreaWindowControl.Maximize -> canvas.drawRoundRect(
                cx - half, cy - half, cx + half, cy + half, dp(1.5f), dp(1.5f), paint,
            )
            TanyaAreaWindowControl.Close -> {
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, paint)
                canvas.drawLine(cx + half, cy - half, cx - half, cy + half, paint)
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

fun VerificationResult.toTanyaAreaChatAnswer(): String = buildString {
    append(narrative.ifBlank { "Analisis selesai." })
    append("\n\nTingkat risiko: ").append(riskLevel.label)
    if (reasons.isNotEmpty()) {
        append("\n\nYang perlu diperhatikan:\n")
        append(reasons.joinToString("\n") { "• $it" })
    }
    if (recommendedActions.isNotEmpty()) {
        append("\n\nLangkah aman:\n")
        append(recommendedActions.joinToString("\n") { "• $it" })
    }
}
