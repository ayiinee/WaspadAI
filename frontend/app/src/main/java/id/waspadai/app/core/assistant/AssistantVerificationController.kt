package id.waspadai.app.core.assistant

import android.graphics.Bitmap
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.capture.OverlayChatTurn
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.core.trigger.VerificationPageContext
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantConversationTurn(
    val question: String?,
    val result: VerificationResult,
)

sealed interface AssistantSessionPhase {
    data object WaitingForContext : AssistantSessionPhase
    data class PreviewImage(val bitmap: Bitmap) : AssistantSessionPhase
    data class ReviewImage(val imageBytes: ByteArray) : AssistantSessionPhase
    data class PreviewText(val text: String, val pageContext: VerificationPageContext?) : AssistantSessionPhase
    data object Submitting : AssistantSessionPhase
    data class Result(val value: VerificationResult) : AssistantSessionPhase
    data class Failure(val message: String) : AssistantSessionPhase
    data object Closed : AssistantSessionPhase
}

data class AssistantSessionState(
    val phase: AssistantSessionPhase = AssistantSessionPhase.WaitingForContext,
    val conversation: List<AssistantConversationTurn> = emptyList(),
    val selectedImagePreview: ByteArray? = null,
)

sealed interface AssistantSessionHandoff {
    val turns: List<OverlayChatTurn>

    data class Image(
        val imageBytes: ByteArray,
        override val turns: List<OverlayChatTurn>,
    ) : AssistantSessionHandoff

    data class Text(
        val text: String,
        val sourceUrl: String?,
        val pageContext: VerificationPageContext?,
        override val turns: List<OverlayChatTurn>,
    ) : AssistantSessionHandoff
}

class AssistantVerificationController(
    repository: id.waspadai.app.feature.verification.domain.VerificationRepository,
    private val scope: CoroutineScope,
) {
    private val submitImage = SubmitImageVerificationUseCase(repository)
    private val submitText = SubmitTextVerificationUseCase(repository)
    private val mutableState = MutableStateFlow(AssistantSessionState())
    val state: StateFlow<AssistantSessionState> = mutableState.asStateFlow()

    private var screenshot: Bitmap? = null
    private var selectedImageBytes: ByteArray? = null
    private var selectedText: String? = null
    private var pageContext: VerificationPageContext? = null
    private var sourceUrl: String? = null
    private var lastSubmittedQuestion: String? = null

    fun offerScreenshot(bitmap: Bitmap?) {
        if (bitmap == null || mutableState.value.phase is AssistantSessionPhase.Closed) return
        screenshot?.takeIf { it !== bitmap }?.recycle()
        screenshot = bitmap
        mutableState.update { it.copy(phase = AssistantSessionPhase.PreviewImage(bitmap)) }
    }

    fun offerText(context: ExtractedAssistContext?) {
        context ?: return
        selectedText = context.text
        pageContext = context.pageContext
        sourceUrl = context.sourceUrl
        if (screenshot == null && mutableState.value.phase !is AssistantSessionPhase.Closed) {
            mutableState.update {
                it.copy(phase = AssistantSessionPhase.PreviewText(context.text, context.pageContext))
            }
        }
    }

    fun updateText(value: String) {
        selectedText = value.take(MAX_TEXT_LENGTH)
        mutableState.update { current ->
            current.copy(phase = AssistantSessionPhase.PreviewText(selectedText.orEmpty(), pageContext))
        }
    }

    fun selectImageArea(imageBytes: ByteArray) {
        if (imageBytes.isEmpty()) {
            mutableState.update { it.copy(phase = AssistantSessionPhase.Failure("Area gambar kosong. Pilih area lain.")) }
            return
        }
        selectedImageBytes?.fill(0)
        selectedImageBytes = imageBytes
        mutableState.update {
            it.copy(
                phase = AssistantSessionPhase.ReviewImage(imageBytes),
                selectedImagePreview = imageBytes,
            )
        }
    }

    fun reselectImageArea() {
        selectedImageBytes?.fill(0)
        selectedImageBytes = null
        mutableState.update { current ->
            val bitmap = screenshot
            if (bitmap != null) {
                current.copy(
                    phase = AssistantSessionPhase.PreviewImage(bitmap),
                    selectedImagePreview = null,
                )
            } else {
                current.copy(
                    phase = AssistantSessionPhase.Failure("Screenshot sudah tidak tersedia. Panggil assistant kembali."),
                    selectedImagePreview = null,
                )
            }
        }
    }

    fun confirmImage() = submit(question = null)

    fun confirmText() = submit(question = null)

    fun askFollowUp(question: String) {
        val clean = question.trim().take(MAX_QUESTION_LENGTH)
        if (clean.isNotEmpty()) submit(clean)
    }

    fun retryLastSubmission() = submit(lastSubmittedQuestion)

    fun snapshotForApp(): AssistantSessionHandoff? {
        val turns = mutableState.value.conversation.flatMap { turn ->
            buildList {
                turn.question?.let { add(OverlayChatTurn(isUser = true, text = it)) }
                add(
                    OverlayChatTurn(
                        isUser = false,
                        text = turn.result.narrative,
                        result = turn.result,
                    )
                )
            }
        }
        selectedImageBytes?.let { bytes ->
            return AssistantSessionHandoff.Image(bytes.copyOf(), turns)
        }
        val text = selectedText?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() }?.let {
            AssistantSessionHandoff.Text(
                text = it,
                sourceUrl = sourceUrl,
                pageContext = pageContext,
                turns = turns,
            )
        }
    }

    private fun submit(question: String?) {
        if (mutableState.value.phase is AssistantSessionPhase.Submitting) return
        val imageBytes = selectedImageBytes
        val text = selectedText?.trim()
        if (imageBytes == null && text.isNullOrBlank()) {
            mutableState.update { it.copy(phase = AssistantSessionPhase.Failure("Konteks layar belum tersedia.")) }
            return
        }
        lastSubmittedQuestion = question
        mutableState.update { it.copy(phase = AssistantSessionPhase.Submitting) }
        scope.launch {
            val result = if (imageBytes != null) {
                submitImage(
                    imageBytes = imageBytes,
                    contentType = "image/png",
                    fileName = "waspadai-assistant.png",
                    question = question,
                    source = TriggerSource.ASSISTANT,
                )
            } else {
                submitText(
                    text = text.orEmpty(),
                    question = question,
                    sourceUrl = sourceUrl,
                    pageContext = pageContext,
                    source = TriggerSource.ASSISTANT,
                )
            }
            when (result) {
                is AppResult.Success -> mutableState.update {
                    it.copy(
                        phase = AssistantSessionPhase.Result(result.value),
                        conversation = it.conversation + AssistantConversationTurn(question, result.value),
                    )
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(phase = AssistantSessionPhase.Failure(result.message))
                }
            }
        }
    }

    fun clear() {
        selectedImageBytes?.fill(0)
        selectedImageBytes = null
        selectedText = null
        pageContext = null
        sourceUrl = null
        lastSubmittedQuestion = null
        screenshot?.recycle()
        screenshot = null
        mutableState.value = AssistantSessionState(AssistantSessionPhase.Closed)
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 25_000
        const val MAX_QUESTION_LENGTH = 500
    }
}
