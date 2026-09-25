package id.waspadai.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ActionFeedbackKind { Success, Error, Info }

private data class ActionFeedbackVisuals(
    override val message: String,
    val kind: ActionFeedbackKind,
    override val actionLabel: String?,
) : androidx.compose.material3.SnackbarVisuals {
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = if (kind == ActionFeedbackKind.Error) {
        SnackbarDuration.Long
    } else {
        SnackbarDuration.Short
    }
}

suspend fun SnackbarHostState.showActionFeedback(
    message: String,
    kind: ActionFeedbackKind,
    retry: Boolean = false,
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(ActionFeedbackVisuals(message, kind, if (retry) "Coba lagi" else null))
}

@Composable
fun ActionFeedbackHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data -> ActionFeedbackCard(data) }
}

@Composable
private fun ActionFeedbackCard(data: SnackbarData) {
    val visuals = data.visuals as? ActionFeedbackVisuals
    val kind = visuals?.kind ?: ActionFeedbackKind.Info
    val accent = when (kind) {
        ActionFeedbackKind.Success -> Color(0xFF15825B)
        ActionFeedbackKind.Error -> Color(0xFFC44142)
        ActionFeedbackKind.Info -> Color(0xFF176AAB)
    }
    val icon = when (kind) {
        ActionFeedbackKind.Success -> Icons.Rounded.CheckCircle
        ActionFeedbackKind.Error -> Icons.Rounded.Error
        ActionFeedbackKind.Info -> Icons.Rounded.Info
    }
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f, fill = false),
                color = Color(0xFF173747),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = data::performAction) { Text(label, color = accent) }
            }
        }
    }
}
