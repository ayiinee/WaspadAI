package id.waspadai.app.core.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CaptureResultBus {
    private val _events = MutableSharedFlow<CaptureEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val events = _events.asSharedFlow()

    fun publish(event: CaptureEvent) {
        _events.tryEmit(event)
    }
}

sealed interface CaptureEvent {
    data class Success(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : CaptureEvent

    data class Failure(val message: String) : CaptureEvent

    data object Stopped : CaptureEvent
}
