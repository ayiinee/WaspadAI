package id.waspadai.app.core.assistant

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult

@Composable
fun AssistantPanel(
    controller: AssistantVerificationController,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5FAFD)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WaspadAI Assistant", style = MaterialTheme.typography.titleLarge, color = Color(0xFF075E85))
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onClose) { Text("Tutup") }
            }
            Text(
                "Konteks belum dikirim. Periksa preview lalu pilih Periksa sekarang.",
                color = Color(0xFF557383),
                fontSize = 13.sp,
            )
            when (val phase = state.phase) {
                AssistantSessionPhase.WaitingForContext -> WaitingCard(onOpenApp)
                is AssistantSessionPhase.PreviewImage -> ImagePreviewCard(phase.bitmap, controller::confirmImage)
                is AssistantSessionPhase.PreviewText -> TextPreviewCard(
                    text = phase.text,
                    onTextChanged = controller::updateText,
                    onConfirm = controller::confirmText,
                )
                AssistantSessionPhase.Submitting -> LoadingCard()
                is AssistantSessionPhase.Result -> Unit
                is AssistantSessionPhase.Failure -> FailureCard(phase.message, onOpenApp)
                AssistantSessionPhase.Closed -> Unit
            }
            state.conversation.forEachIndexed { index, turn ->
                turn.question?.let { QuestionCard(it) }
                ResultCard(result = turn.result, label = "Hasil ${index + 1}")
            }
            if (state.phase is AssistantSessionPhase.Result) {
                FollowUp(controller::askFollowUp)
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(bitmap: android.graphics.Bitmap, onConfirm: (ByteArray) -> Unit) {
    var cropView by remember(bitmap) { mutableStateOf<BitmapCropView?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pilih area yang ingin diperiksa", style = MaterialTheme.typography.titleMedium)
            AndroidView(
                factory = { context -> BitmapCropView(context).also { cropView = it; it.setBitmap(bitmap) } },
                update = { it.setBitmap(bitmap) },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            Button(
                onClick = { cropView?.croppedPng()?.let(onConfirm) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Periksa sekarang") }
        }
    }
}

@Composable
private fun TextPreviewCard(text: String, onTextChanged: (String) -> Unit, onConfirm: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Teks yang terbaca dari layar", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onConfirm, enabled = text.trim().length >= 10, modifier = Modifier.fillMaxWidth()) {
                Text("Periksa sekarang")
            }
        }
    }
}

@Composable
private fun WaitingCard(onOpenApp: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Menunggu konteks layar…", style = MaterialTheme.typography.titleMedium)
            Text("Jika aplikasi melindungi layar atau akses konteks dimatikan, gunakan Share ke WaspadAI, upload manual, atau tile Periksa layar.")
            OutlinedButton(onClick = onOpenApp) { Text("Buka WaspadAI") }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Text("Memeriksa konteks…", Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun FailureCard(message: String, onOpenApp: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F0))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message, color = Color(0xFF9B2C2C))
            OutlinedButton(onClick = onOpenApp) { Text("Gunakan fallback di aplikasi") }
        }
    }
}

@Composable
private fun QuestionCard(question: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4FA))) {
        Text(question, Modifier.padding(14.dp))
    }
}

@Composable
private fun ResultCard(result: VerificationResult, label: String) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Color(0xFF557383), fontSize = 12.sp)
            if (result.headline.isNotBlank()) Text(result.headline, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Verdict: ${result.verdict.label}", Color(0xFF075E85))
                StatusPill("Risk: ${result.riskLevel.label}", riskColor(result.riskLevel))
            }
            StatusPill("Fakta: ${result.factualStatus.label}", Color(0xFF075E85))
            Text(result.narrative)
            if (result.reasons.isNotEmpty()) {
                Text("Mengapa", style = MaterialTheme.typography.titleMedium)
                result.reasons.forEach { Bullet(it) }
            }
            if (result.recommendedActions.isNotEmpty()) {
                Text("Tindakan aman", style = MaterialTheme.typography.titleMedium)
                result.recommendedActions.forEach { Bullet(it) }
            }
            if (result.evidence.isNotEmpty()) {
                Text("Evidence", style = MaterialTheme.typography.titleMedium)
                result.evidence.forEach { evidence ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F8FA))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(evidence.title.ifBlank { evidence.publisher }, style = MaterialTheme.typography.titleSmall)
                            if (evidence.excerpt.isNotBlank()) Text(evidence.excerpt, fontSize = 13.sp)
                            Text("${evidence.stance} · ${evidence.verificationStatus}", color = Color(0xFF557383), fontSize = 12.sp)
                            if (evidence.url.isNotBlank()) {
                                Text(
                                    "Buka sumber",
                                    color = Color(0xFF075E85),
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(evidence.url))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (result.sources.isNotEmpty()) {
                Text("Sumber tambahan", style = MaterialTheme.typography.titleMedium)
                result.sources.forEach { source ->
                    Text(
                        "${source.publisher}: ${source.title}",
                        color = Color(0xFF075E85),
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                }
            }
            if (result.uncertainty.isNotBlank()) Text("Ketidakpastian: ${result.uncertainty}", fontSize = 13.sp)
            if (result.requiresHumanReview) Text("Hasil ini memerlukan tinjauan manusia.", color = Color(0xFFB35C00))
            if (result.disclaimer.isNotBlank()) Text(result.disclaimer, color = Color(0xFF557383), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FollowUp(onSubmit: (String) -> Unit) {
    var question by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = question,
            onValueChange = { question = it.take(500) },
            label = { Text("Tanya lanjutan") },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSubmit(question); question = "" },
            enabled = question.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Kirim") }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun Bullet(text: String) = Row {
    Box(Modifier.padding(top = 7.dp).size(6.dp).background(Color(0xFF075E85), CircleShape))
    Text(text, Modifier.padding(start = 8.dp))
}

private fun riskColor(level: RiskLevel) = when (level) {
    RiskLevel.CRITICAL, RiskLevel.HIGH -> Color(0xFFB3261E)
    RiskLevel.MEDIUM -> Color(0xFFB35C00)
    RiskLevel.LOW -> Color(0xFF197A3D)
    RiskLevel.UNKNOWN -> Color(0xFF557383)
}
