package id.waspadai.app.feature.verification.presentation.component

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAICaution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.core.ui.BrandBlue
import id.waspadai.app.core.ui.DeepBlue
import id.waspadai.app.core.ui.Ink
import id.waspadai.app.core.ui.RiskRed
import id.waspadai.app.core.ui.SoftBlue
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem

@Composable
fun WaspadAiHeader(
    overlayModeEnabled: Boolean,
    enabled: Boolean,
    onToggleOverlayMode: () -> Unit,
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
            Text(
                text = "WaspadAI",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
            )
            Switch(
                checked = overlayModeEnabled,
                onCheckedChange = { onToggleOverlayMode() },
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = WaspadAICaution,
                    checkedBorderColor = WaspadAICaution,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = WaspadAIDarkBlue.copy(alpha = .72f),
                    uncheckedBorderColor = Color.White.copy(alpha = .75f),
                ),
            )
        }
    }
}

@Composable
fun HistoryPanel(
    items: List<VerificationHistoryItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
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
                    "History Verifikasi",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Muat ulang history",
                        tint = BrandBlue
                    )
                }
            }
            when {
                isLoading -> Text("Memuat history...", color = Color(0xFF557383), fontSize = 13.sp)
                items.isEmpty() -> Text("Belum ada history tersimpan.", color = Color(0xFF557383), fontSize = 13.sp)
                else -> items.forEach { item ->
                    HistoryRow(item = item, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: VerificationHistoryItem, onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onOpen(item.caseId) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            item.headline,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "${item.verdict} • ${item.createdAt}",
            color = Color(0xFF557383),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (hasAttachment) {
            AttachmentPreview(
                fileName = attachmentName ?: "Gambar verifikasi",
                imageBytes = attachmentBytes,
                contentType = attachmentContentType,
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
    fileName: String,
    imageBytes: ByteArray?,
    contentType: String?,
) {
    val bitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Card(
        modifier = Modifier.widthIn(max = 350.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = BrandBlue)
    ) {
        Column(Modifier.padding(9.dp)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF2F6F8))
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview $fileName",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        fileName,
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (contentType == "image/png" && fileName.endsWith(".pdf", true)) {
                            "Preview halaman pertama PDF"
                        } else {
                            "Preview gambar verifikasi"
                        },
                        color = Color(0xFF71808A),
                        fontSize = 10.sp,
                    )
                }
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
            if (result.communityEligible &&
                result.communityState == "PRIVATE" &&
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
private fun Bullet(value: String) {
    Row(Modifier.padding(top = 5.dp)) {
        Text("•", fontSize = 18.sp, color = Ink, modifier = Modifier.padding(end = 9.dp))
        Text(value, color = Ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RiskLabel(riskLevel: RiskLevel) {
    val color = when (riskLevel) {
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
    hasPendingImage: Boolean = false,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestImageCapture: () -> Unit,
    onRequestFileCapture: () -> Unit,
) {
    var isAttachmentMenuVisible by remember { mutableStateOf(false) }
    val sendColor by animateColorAsState(
        targetValue = if (enabled) BrandBlue else Color(0xFF9FC8DD),
        label = "sendColor",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .heightIn(min = 54.dp)
            .border(1.5.dp, WaspadAILightBlue, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BrandBlue)
                    .clickable(enabled = enabled) { isAttachmentMenuVisible = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Tambah lampiran",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            DropdownMenu(
                expanded = isAttachmentMenuVisible,
                onDismissRequest = { isAttachmentMenuVisible = false },
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .border(1.dp, WaspadAILightBlue, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                shadowElevation = 10.dp,
            ) {
                Text(
                    text = "Tambahkan lampiran",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaspadAIBlue)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("File", color = DeepBlue, fontWeight = FontWeight.SemiBold)
                            Text("PDF • preview halaman pertama", color = Color(0xFF71808A), fontSize = 11.sp)
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
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = Ink, fontSize = 15.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            if (hasPendingImage) "Tulis pesan untuk lampiran…"
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
                .size(44.dp)
                .clip(CircleShape)
                .background(sendColor)
                .clickable(enabled = enabled, onClick = onSubmit),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "Kirim pemeriksaan",
                tint = Color.White,
                modifier = Modifier.size(25.dp),
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
            NavigationItem("Progres", "▥", activeTab) { onTabSelected("Progres") }
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
