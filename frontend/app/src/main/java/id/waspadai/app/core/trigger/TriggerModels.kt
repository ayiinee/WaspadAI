package id.waspadai.app.core.trigger

import java.util.UUID

enum class TriggerSource {
    ASSISTANT,
    SHARE_SHEET,
    QUICK_SETTINGS,
    FLOATING_OVERLAY,
    IN_APP,
}

data class VerificationPageContext(
    val title: String? = null,
    val before: String? = null,
    val after: String? = null,
)

sealed interface CapturedContext {
    val source: TriggerSource

    data class Image(
        val imageBytes: ByteArray,
        val contentType: String,
        val fileName: String,
        override val source: TriggerSource,
    ) : CapturedContext

    data class Text(
        val text: String,
        val pageContext: VerificationPageContext? = null,
        override val source: TriggerSource,
    ) : CapturedContext
}

data class PendingVerificationTrigger(
    val contexts: List<CapturedContext>,
    val id: String = UUID.randomUUID().toString(),
)

class PendingTriggerStore {
    @Volatile
    private var pending: PendingVerificationTrigger? = null

    fun put(trigger: PendingVerificationTrigger) {
        pending?.wipeImages()
        pending = trigger
    }

    fun take(): PendingVerificationTrigger? = pending.also { pending = null }

    fun clear() {
        pending?.wipeImages()
        pending = null
    }

    private fun PendingVerificationTrigger.wipeImages() {
        contexts.filterIsInstance<CapturedContext.Image>().forEach { it.imageBytes.fill(0) }
    }
}
