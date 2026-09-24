package id.waspadai.app.core.trigger

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface QuickCaptureResult {
    data class Success(val trigger: PendingVerificationTrigger) : QuickCaptureResult
    data class Failure(val message: String) : QuickCaptureResult
}

object QuickCaptureResultBus {
    private val channel = Channel<QuickCaptureResult>(Channel.BUFFERED)
    val results = channel.receiveAsFlow()

    fun publish(result: QuickCaptureResult) {
        channel.trySend(result)
    }
}
