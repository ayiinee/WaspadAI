package id.waspadai.app.core.assistant

import android.app.assist.AssistStructure
import android.app.assist.AssistContent
import id.waspadai.app.core.trigger.VerificationPageContext

data class ExtractedAssistContext(
    val text: String,
    val pageContext: VerificationPageContext?,
    val sourceUrl: String? = null,
)

object AssistantContextExtractor {
    fun extract(structure: AssistStructure?, content: AssistContent? = null): ExtractedAssistContext? {
        structure ?: return null
        val fragments = buildList {
            repeat(structure.windowNodeCount) { windowIndex ->
                collect(structure.getWindowNodeAt(windowIndex).rootViewNode, this)
            }
        }
        val text = normalizeVisibleText(fragments)
        if (text.length < MIN_TEXT_LENGTH) return null
        val title = structure.activityComponent?.packageName
            ?.takeIf(String::isNotBlank)
        return ExtractedAssistContext(
            text = text,
            pageContext = title?.let { VerificationPageContext(title = it.take(300)) },
            sourceUrl = content?.webUri?.toString()?.takeIf(::isPublicHttpUrl),
        )
    }

    fun normalizeVisibleText(fragments: List<String>): String = fragments
        .asSequence()
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
        .take(MAX_TEXT_LENGTH)
        .trim()

    private fun collect(node: AssistStructure.ViewNode, output: MutableList<String>) {
        node.text?.toString()?.let(output::add)
        node.contentDescription?.toString()?.let(output::add)
        repeat(node.childCount) { collect(node.getChildAt(it), output) }
    }

    private const val MIN_TEXT_LENGTH = 10
    private const val MAX_TEXT_LENGTH = 25_000
}

private fun isPublicHttpUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
