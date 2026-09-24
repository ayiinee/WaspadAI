package id.waspadai.app.core.capture

import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.trigger.TriggerSource
import id.waspadai.app.core.trigger.VerificationPageContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object CaptureResultBus {
    private val eventChannel = Channel<CaptureEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun publish(event: CaptureEvent) {
        eventChannel.trySend(event)
    }
}

sealed interface CaptureEvent {
    data class Success(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : CaptureEvent

    data class Failure(val message: String) : CaptureEvent

    data class PermissionExpired(val message: String) : CaptureEvent

    data class Conversation(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
        val turns: List<OverlayChatTurn>,
        val source: TriggerSource = TriggerSource.FLOATING_OVERLAY,
    ) : CaptureEvent

    data class TextConversation(
        val text: String,
        val sourceUrl: String?,
        val pageContext: VerificationPageContext?,
        val turns: List<OverlayChatTurn>,
        val source: TriggerSource = TriggerSource.ASSISTANT,
    ) : CaptureEvent

    data object Stopped : CaptureEvent
}

data class OverlayChatTurn(
    val isUser: Boolean,
    val text: String,
    val result: VerificationResult? = null,
)
