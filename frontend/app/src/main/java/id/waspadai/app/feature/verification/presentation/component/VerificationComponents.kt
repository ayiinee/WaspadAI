package id.waspadai.app.feature.verification.presentation.component

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.ui.BrandBlue
import id.waspadai.app.core.ui.DeepBlue
import id.waspadai.app.core.ui.Ink
import id.waspadai.app.core.ui.RiskRed
import id.waspadai.app.core.ui.SoftBlue
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.presentation.ImageVerificationPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WaspadAiHeader(
    enabled: Boolean,
    onOpenQuickAccess: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaspadAIBlue)
    ) {
        Image(
            painter = painterResource(id = R.drawable.community_header_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = .6f,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.waspadai_logo),
                contentDescription = "Logo WaspadAI",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(WaspadAIDarkBlue)
                    .border(1.5.dp, Color.White, RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled, onClick = onOpenQuickAccess)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Akses Cepat",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
fun VerificationChatHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaspadAIBlue)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Kembali ke daftar percakapan",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun HistoryPanel(
    items: List<VerificationConversationSummary>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlue)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ruang chat tersimpan",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onNewChat) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Chat baru", color = BrandBlue, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Muat ulang history",
                        tint = BrandBlue
                    )
                }
            }
            when {
                isLoading -> Text("Memuat ruang chat...", color = Color(0xFF557383), fontSize = 13.sp)
                items.isEmpty() -> Text(
                    "Belum ada percakapan. Tulis pesan di atas untuk memulai.",
                    color = Color(0xFF557383),
                    fontSize = 13.sp,
                )
                else -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items.forEach { item ->
                        HistoryRow(item = item, onOpen = onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: VerificationConversationSummary, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onOpen(item.conversationId) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = item.title,
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.latestMessagePreview,
            color = Color(0xFF355263),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${item.lastVerdict} • ${item.updatedAt.asChatTimestamp()}",
            color = Color(0xFF557383),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun String.asChatTimestamp(): String = runCatching {
    DateTimeFormatter
        .ofPattern("d MMM, HH.mm", Locale.forLanguageTag("id-ID"))
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(this))
}.getOrDefault(this)

@Composable
fun ModeNotice(isRemoteEnabled: Boolean) {
    val text = if (isRemoteEnabled) {
        "Mode API publik — pesan akan dikirim ke WaspadAI untuk diperiksa."
    } else {
        "Mode simulasi — hasil ini bukan pemeriksaan AI live."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SoftBlue)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(Color(0xFFE89D14), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF436575), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun UserMessage(
    text: String,
    hasAttachment: Boolean,
    attachmentName: String? = null,
    attachmentBytes: ByteArray? = null,
    attachmentContentType: String? = null,
    attachmentGroup: List<ImageVerificationPreview> = emptyList(),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (hasAttachment) {
            AttachmentPreview(
                attachments = attachmentGroup.ifEmpty {
                    listOf(
                        ImageVerificationPreview(
                            imageBytes = attachmentBytes ?: byteArrayOf(),
                            contentType = attachmentContentType.orEmpty(),
                            fileName = attachmentName ?: "Gambar verifikasi",
                        )
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                .background(BrandBlue)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun AttachmentPreview(
    attachments: List<ImageVerificationPreview>,
) {
    var selectedAttachment by remember { mutableStateOf<ImageVerificationPreview?>(null) }
    when (attachments.size) {
        1 -> SingleAttachmentPreview(
            attachment = attachments.first(),
            onClick = { selectedAttachment = attachments.first() },
        )
        else -> AttachmentCollage(
            attachments = attachments,
            onClick = { selectedAttachment = it },
        )
    }
    selectedAttachment?.let { attachment ->
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { selectedAttachment = null })
    }
}

@Composable
private fun SingleAttachmentPreview(
    attachment: ImageVerificationPreview,
    onClick: () -> Unit,
) {
    val isDocument = attachment.fileName.endsWith(".pdf", ignoreCase = true)
    val bitmap = remember(attachment.imageBytes) {
        BitmapFactory.decodeByteArray(attachment.imageBytes, 0, attachment.imageBytes.size)
    }
    if (isDocument) {
        Row(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .border(1.dp, WaspadAILightBlue.copy(alpha = .72f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8493F)),
                contentAlignment = Alignment.Center,
            ) { Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text(
                text = attachment.fileName,
                color = Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Preview ${attachment.fileName}",
            modifier = Modifier
                .widthIn(max = 350.dp)
                .heightIn(max = 260.dp)
                .border(1.dp, WaspadAILightBlue.copy(alpha = .72f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .height(112.dp)
                .border(1.dp, WaspadAILightBlue.copy(alpha = .72f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(SoftBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Preview lampiran tidak tersedia",
                color = Color(0xFF557383),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun AttachmentCollage(
    attachments: List<ImageVerificationPreview>,
    onClick: (ImageVerificationPreview) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 350.dp)
            .border(1.dp, WaspadAILightBlue.copy(alpha = .72f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        attachments.chunked(2).forEach { rowAttachments ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rowAttachments.forEach { attachment ->
                    CollageTile(
                        attachment = attachment,
                        onClick = { onClick(attachment) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowAttachments.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CollageTile(
    attachment: ImageVerificationPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDocument = attachment.fileName.endsWith(".pdf", ignoreCase = true)
    val bitmap = remember(attachment.imageBytes) {
        BitmapFactory.decodeByteArray(attachment.imageBytes, 0, attachment.imageBytes.size)
    }
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F6F8))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!isDocument && bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview ${attachment.fileName}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(if (isDocument) "PDF" else "Gambar", color = WaspadAIBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: ImageVerificationPreview,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(attachment.imageBytes) {
        BitmapFactory.decodeByteArray(attachment.imageBytes, 0, attachment.imageBytes.size)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .9f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview besar ${attachment.fileName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(attachment.fileName, color = Color.White)
            }
        }
    }
}

@Composable
fun AnalysisCard(
    result: VerificationResult,
    isSample: Boolean,
    onShareToCommunity: () -> Unit = {},
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(7.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✦", color = BrandBlue, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text("Hasil Analisis", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            if (isSample) {
                Text("CONTOH TAMPILAN", color = Color(0xFF728995), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (result.headline.isNotBlank()) {
                Text(
                    result.headline,
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultPill("Truth: ${result.verdict.label}")
                ResultPill("Fakta: ${result.factualStatus.label}")
            }
            Spacer(Modifier.height(10.dp))
            Text(result.narrative, color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
            if (result.reasons.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Mengapa berisiko", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                result.reasons.forEach { Bullet(it) }
            }
            if (result.recommendedActions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Tindakan yang disarankan", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                result.recommendedActions.forEach { Bullet(it) }
            }
            Spacer(Modifier.height(13.dp))
            RiskLabel(result.riskLevel)
            if (result.evidence.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Evidence", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                result.evidence.forEach { evidence ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftBlue),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                evidence.title.ifBlank { evidence.publisher.ifBlank { "Bukti pendukung" } },
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                            )
                            if (evidence.publisher.isNotBlank()) {
                                Text(evidence.publisher, color = BrandBlue, fontSize = 12.sp)
                            }
                            if (evidence.excerpt.isNotBlank()) {
                                Text(evidence.excerpt, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                            if (evidence.stance.isNotBlank() || evidence.verificationStatus.isNotBlank()) {
                                Text(
                                    listOf(evidence.stance, evidence.verificationStatus)
                                        .filter(String::isNotBlank)
                                        .joinToString(" • "),
                                    color = Color(0xFF557383),
                                    fontSize = 11.sp,
                                )
                            }
                            if (evidence.url.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(evidence.url)))
                                        }
                                    },
                                ) { Text("Buka bukti") }
                            }
                        }
                    }
                }
            }
            if (result.sources.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Sumber", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                result.sources.forEach { source ->
                    Text(
                        text = "${source.publisher.ifBlank { "Sumber" }} — ${source.title.ifBlank { source.url }}",
                        color = BrandBlue,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                                }
                            }
                            .padding(vertical = 7.dp),
                    )
                }
            }
            if (result.uncertainty.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Ketidakpastian", color = Ink, fontWeight = FontWeight.Bold)
                Text(result.uncertainty, color = Color(0xFF557383), fontSize = 13.sp)
            }
            if (result.requiresHumanReview) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hasil ini memerlukan peninjauan manusia sebelum dijadikan dasar keputusan.",
                    color = Color(0xFF9B5D00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (result.disclaimer.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(result.disclaimer, color = Color(0xFF6A7880), fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (result.communityEligible &&
                result.communityState == "PRIVATE" &&
                result.riskLevel == RiskLevel.UNKNOWN &&
                !result.caseId.isNullOrBlank()
            ) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onShareToCommunity,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Bagikan ke Koneksi")
                }
            }
        }
    }
}

@Composable
private fun ResultPill(text: String) {
    Text(
        text = text,
        color = BrandBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SoftBlue)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun Bullet(value: String) {
    Row(Modifier.padding(top = 5.dp)) {
        Text("•", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(end = 9.dp))
        Text(value, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RiskLabel(riskLevel: RiskLevel) {
    val color = when (riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFF8C1D18)
        RiskLevel.HIGH -> RiskRed
        RiskLevel.MEDIUM -> Color(0xFFB86E00)
        RiskLevel.LOW -> Color(0xFF197A3D)
        RiskLevel.UNKNOWN -> Color(0xFF557383)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            "Status Risiko: ${riskLevel.label}",
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun ThinkingBubble(text: String) {
    Text(
        text = text,
        color = Color(0xFF557383),
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SoftBlue)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
fun FailureNotice(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFEFED))
            .padding(start = 14.dp, top = 7.dp, end = 6.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = Color(0xFFA52219), fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Tutup", color = Color(0xFFA52219)) }
    }
}

@Composable
fun VerificationComposer(
    value: String,
    enabled: Boolean,
    pendingAttachments: List<ImageVerificationPreview> = emptyList(),
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemovePendingAttachment: (Int) -> Unit,
    onRequestImageCapture: () -> Unit,
    onRequestFileCapture: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var isAttachmentMenuVisible by remember { mutableStateOf(false) }
    val sendColor by animateColorAsState(
        targetValue = if (enabled) BrandBlue else Color(0xFF9FC8DD),
        label = "sendColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .background(Color.White, RoundedCornerShape(29.dp))
            .border(2.dp, WaspadAILightBlue, RoundedCornerShape(29.dp))
            .clip(RoundedCornerShape(29.dp)),
    ) {
        if (pendingAttachments.isNotEmpty()) {
            PendingAttachmentDrafts(
                attachments = pendingAttachments,
                onRemove = onRemovePendingAttachment,
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WaspadAILightBlue.copy(alpha = .7f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(BrandBlue)
                        .clickable(enabled = enabled) { isAttachmentMenuVisible = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Tambah lampiran",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                DropdownMenu(
                    expanded = isAttachmentMenuVisible,
                    onDismissRequest = { isAttachmentMenuVisible = false },
                    modifier = Modifier
                        .widthIn(min = 180.dp)
                        .border(1.dp, WaspadAILightBlue, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    shadowElevation = 8.dp,
                ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("File", color = DeepBlue, fontWeight = FontWeight.SemiBold)
                            Text("PDF, DOCX, dan lainnya", color = Color(0xFF71808A), fontSize = 11.sp)
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = WaspadAIBlue)
                    },
                    onClick = {
                        isAttachmentMenuVisible = false
                        onRequestFileCapture()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Gambar", color = DeepBlue, fontWeight = FontWeight.SemiBold)
                            Text("JPG, PNG, atau WEBP", color = Color(0xFF71808A), fontSize = 11.sp)
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = WaspadAIBlue)
                    },
                    onClick = {
                        isAttachmentMenuVisible = false
                        onRequestImageCapture()
                    },
                )
            }
            }
            Spacer(Modifier.width(10.dp))
            BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            textStyle = TextStyle(color = Ink, fontSize = 15.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            if (pendingAttachments.isNotEmpty()) "Tulis pesan untuk lampiran…"
                            else "Ketik pesan untuk diperiksa…",
                            color = Color(0xFF71808A),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
            )
            Spacer(Modifier.width(8.dp))
            Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(sendColor)
                .clickable(enabled = enabled, onClick = onSubmit),
            contentAlignment = Alignment.Center
            ) {
                Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "Kirim pemeriksaan",
                tint = Color.White,
                modifier = Modifier.size(27.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentDrafts(
    attachments: List<ImageVerificationPreview>,
    onRemove: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = attachments,
            key = { index, attachment -> "$index-${attachment.fileName}" },
        ) { index, attachment ->
            PendingAttachmentDraft(
                attachment = attachment,
                onRemove = { onRemove(index) },
            )
        }
    }
}

@Composable
private fun PendingAttachmentDraft(
    attachment: ImageVerificationPreview,
    onRemove: () -> Unit,
) {
    val isDocument = attachment.fileName.endsWith(".pdf", ignoreCase = true)
    val bitmap = remember(attachment.imageBytes) {
        BitmapFactory.decodeByteArray(attachment.imageBytes, 0, attachment.imageBytes.size)
    }
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF2F6F8)),
    ) {
        if (isDocument) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE8493F)),
                contentAlignment = Alignment.Center,
            ) {
                Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Draft ${attachment.fileName}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .padding(2.dp)
                .background(Color.Black.copy(alpha = .5f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hapus ${attachment.fileName}",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun BottomNavigation(activeTab: String, onTabSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(Color.White)
            .border(1.dp, Color(0xFFE3EAF0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            NavigationItem("Beranda", "⌂", activeTab) { onTabSelected("Beranda") }
            NavigationItem("Pelajari", "▤", activeTab) { onTabSelected("Pelajari") }
            Spacer(Modifier.weight(1f))
            NavigationItem("Koneksi", "♧", activeTab) { onTabSelected("Koneksi") }
            NavigationItem("Profil", "●", activeTab) { onTabSelected("Profil") }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 0.dp).clickable { onTabSelected("Periksa") },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(56.dp).border(4.dp, Color.White, CircleShape).clip(CircleShape).background(BrandBlue),
                contentAlignment = Alignment.Center
            ) { }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(label: String, symbol: String, activeTab: String, onClick: () -> Unit) {
    val selected = label == activeTab
    Column(
        modifier = Modifier.weight(1f).padding(bottom = 12.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 24.dp else 20.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) BrandBlue else Color.Transparent)
                .padding(if (selected) 3.dp else 0.dp)
        ) {
            NavigationIcon(label, if (selected) Color.White else Color(0xFFB7D5E9))
        }
        Text(label, color = if (selected) BrandBlue else Color(0xFFB7D5E9), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp)
    }
}

@Composable
private fun NavigationIcon(label: String, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 2.25.dp.toPx()
        val style = Stroke(stroke, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (label) {
            "Beranda" -> {
                val roof = Path().apply {
                    moveTo(w * .12f, h * .47f)
                    lineTo(w * .5f, h * .14f)
                    lineTo(w * .88f, h * .47f)
                }
                drawPath(roof, color, style = style)
                drawLine(color, Offset(w * .22f, h * .39f), Offset(w * .22f, h * .86f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, h * .39f), Offset(w * .78f, h * .86f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .22f, h * .86f), Offset(w * .78f, h * .86f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .5f, h * .86f), Offset(w * .5f, h * .62f), stroke, StrokeCap.Round)
            }
            "Pelajari" -> {
                drawRoundRect(color, Offset(w * .08f, h * .16f), Size(w * .39f, h * .7f), CornerRadius(2.dp.toPx()), style)
                drawRoundRect(color, Offset(w * .53f, h * .16f), Size(w * .39f, h * .7f), CornerRadius(2.dp.toPx()), style)
                drawLine(color, Offset(w * .5f, h * .22f), Offset(w * .5f, h * .9f), stroke, StrokeCap.Round)
            }
            "Koneksi" -> {
                drawCircle(color, radius = w * .14f, center = Offset(w * .36f, h * .32f), style = style)
                drawCircle(color, radius = w * .11f, center = Offset(w * .7f, h * .39f), style = style)
                drawArc(color, 194f, 152f, false, Offset(w * .1f, h * .42f), Size(w * .55f, h * .5f), style = style)
                drawArc(color, 204f, 135f, false, Offset(w * .45f, h * .49f), Size(w * .45f, h * .43f), style = style)
            }
            else -> {
                drawArc(color, 180f, 180f, false, Offset(w * .12f, h * .19f), Size(w * .76f, h * .7f), style = style)
                drawLine(color, Offset(w * .5f, h * .62f), Offset(w * .72f, h * .42f), stroke, StrokeCap.Round)
                drawCircle(color, radius = stroke, center = Offset(w * .5f, h * .62f))
            }
        }
    }
}

@Composable
private fun MagnifierIcon() {
    Canvas(Modifier.size(35.dp)) {
        val strokeWidth = 3.dp.toPx()
        val center = Offset(size.width * .43f, size.height * .43f)
        drawCircle(Color.White, radius = size.minDimension * .27f, center = center, style = Stroke(strokeWidth))
        drawLine(
            color = Color.White,
            start = Offset(size.width * .63f, size.height * .63f),
            end = Offset(size.width * .87f, size.height * .87f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
