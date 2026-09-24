package id.waspadai.app.feature.verification.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.waspadai.app.core.capture.CaptureEvent
import id.waspadai.app.core.capture.CaptureResultBus
import id.waspadai.app.core.overlay.FloatingVerifyService
import id.waspadai.app.feature.verification.presentation.component.AnalysisCard
import id.waspadai.app.feature.verification.presentation.component.FailureNotice
import id.waspadai.app.feature.verification.presentation.component.HistoryPanel
import id.waspadai.app.feature.verification.presentation.component.ThinkingBubble
import id.waspadai.app.feature.verification.presentation.component.UserMessage
import id.waspadai.app.feature.verification.presentation.component.VerificationComposer
import id.waspadai.app.feature.verification.presentation.component.WaspadAiHeader
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.core.ui.WaspadAIBottomNavigationHeight
import id.waspadai.app.ui.theme.WaspadAITheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VerificationRoute(
    viewModel: VerificationViewModel,
    onDestinationSelected: (String) -> Unit = {},
    onCommunityPublished: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.communityShare.phase) {
        (state.communityShare.phase as? CommunitySharePhase.Published)?.let { published ->
            onCommunityPublished(published.post.caseId)
        }
    }
    VerificationScreen(
        state = state,
        onAction = viewModel::onAction,
        onDestinationSelected = onDestinationSelected,
    )
}

@Composable
fun VerificationScreen(
    state: VerificationUiState,
    onAction: (VerificationAction) -> Unit,
    onDestinationSelected: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val pickerScope = rememberCoroutineScope()
    val isSubmitting = state.phase is VerificationPhase.Validating || state.phase is VerificationPhase.Submitting
    val mediaProjectionManager = context.getSystemService(MediaProjectionManager::class.java)
    val mediaProjectionConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            FloatingVerifyService.start(context, result.resultCode, data)
            onAction(VerificationAction.OverlayModeConsentResult(granted = true))
        } else {
            onAction(VerificationAction.OverlayModeConsentResult(granted = false))
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(context)
        onAction(VerificationAction.OverlayPermissionResult(granted))
        if (granted) {
            mediaProjectionConsent.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
    val openOverlayPermissionSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { overlayPermission.launch(intent) }
            .onFailure {
                onAction(
                    VerificationAction.ImageSelectionFailed(
                        "Pengaturan izin overlay belum dapat dibuka di perangkat ini."
                    )
                )
            }
        Unit
    }

    LaunchedEffect(Unit) {
        CaptureResultBus.events.collect { event ->
            when (event) {
                is CaptureEvent.Success -> onAction(
                    VerificationAction.OverlayCaptureReady(
                        imageBytes = event.imageBytes,
                        contentType = event.contentType,
                        fileName = event.fileName,
                    )
                )
                is CaptureEvent.Failure -> onAction(VerificationAction.ImageSelectionFailed(event.message))
                is CaptureEvent.PermissionExpired -> onAction(
                    VerificationAction.OverlayPermissionExpired(event.message)
                )
                is CaptureEvent.Conversation -> onAction(
                    VerificationAction.OverlayConversationReady(
                        imageBytes = event.imageBytes,
                        contentType = event.contentType,
                        fileName = event.fileName,
                        turns = event.turns,
                    )
                )
                CaptureEvent.Stopped -> onAction(VerificationAction.OverlayStopped)
            }
        }
    }

    val startOverlayFlow: () -> Unit = {
        onAction(VerificationAction.AcceptOverlayPrivacy)
        if (Settings.canDrawOverlays(context)) {
            mediaProjectionConsent.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            openOverlayPermissionSettings()
        }
        Unit
    }

    if (state.isOverlayPrivacyDialogVisible) {
        OverlayPrivacyDialog(
            onDismiss = { onAction(VerificationAction.DismissOverlayPrivacy) },
            onContinue = startOverlayFlow,
        )
    }

    when (val sharePhase = state.communityShare.phase) {
        CommunitySharePhase.RequestingPreview,
        CommunitySharePhase.Publishing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Menyiapkan publikasi") },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text(
                            if (sharePhase is CommunitySharePhase.Publishing) {
                                "Mempublikasikan kasus ke Koneksi..."
                            } else {
                                "Membuat preview aman..."
                            }
                        )
                    }
                },
                confirmButton = {},
            )
        }
        is CommunitySharePhase.PreviewReady -> {
            val sourceAttachment = state.conversation.asReversed()
                .filterIsInstance<VerificationConversationItem.UserMessage>()
                .firstOrNull()
                ?.let { message ->
                    message.attachmentGroup.lastOrNull() ?: message.attachmentBytes?.let { bytes ->
                        ImageVerificationPreview(
                            imageBytes = bytes,
                            contentType = message.attachmentContentType.orEmpty(),
                            fileName = message.attachmentName.orEmpty(),
                        )
                    }
                }
            CommunityConsentDialog(
                preview = sharePhase.preview,
                sourceAttachment = sourceAttachment,
                caption = state.communityShare.caption,
                ragReuseConsent = state.communityShare.ragReuseConsent,
                onCaptionChanged = {
                    onAction(VerificationAction.CommunityCaptionChanged(it))
                },
                onRagReuseConsentChanged = {
                    onAction(VerificationAction.CommunityRagConsentChanged(it))
                },
                onDismiss = { onAction(VerificationAction.DismissCommunityShare) },
                onPublish = { onAction(VerificationAction.PublishCommunity) },
            )
        }
        is CommunitySharePhase.Published -> {
            AlertDialog(
                onDismissRequest = { onAction(VerificationAction.DismissCommunityShare) },
                title = { Text("Berhasil dibagikan") },
                text = {
                    Text("Kasus sudah dipublikasikan ke Koneksi sebagai konten yang belum diverifikasi.")
                },
                confirmButton = {
                    Button(onClick = { onAction(VerificationAction.DismissCommunityShare) }) {
                        Text("Selesai")
                    }
                },
            )
        }
        is CommunitySharePhase.Failure -> {
            AlertDialog(
                onDismissRequest = { onAction(VerificationAction.DismissCommunityShare) },
                title = { Text("Gagal membagikan kasus") },
                text = { Text(sharePhase.message) },
                confirmButton = {
                    TextButton(onClick = { onAction(VerificationAction.DismissCommunityShare) }) {
                        Text("Tutup")
                    }
                },
            )
        }
        CommunitySharePhase.Idle -> Unit
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pickerScope.launch {
            val selections = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.readImageSelection(uri) }.getOrNull()
                }
            }
            if (selections.isNotEmpty()) {
                onAction(VerificationAction.AttachmentsSelected(selections.map(ImageSelection::toAction)))
            }
            if (selections.size < uris.size) {
                onAction(
                    VerificationAction.ImageSelectionFailed(
                        "Sebagian gambar belum dapat dibaca. Gunakan file JPG, PNG, atau WEBP."
                    )
                )
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pickerScope.launch {
            val selections = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.readPdfSelection(uri) }.getOrNull()
                }
            }
            if (selections.isNotEmpty()) {
                onAction(VerificationAction.AttachmentsSelected(selections.map(ImageSelection::toAction)))
            }
            if (selections.size < uris.size) {
                onAction(
                    VerificationAction.ImageSelectionFailed(
                        "Sebagian file belum dapat dipreview. Gunakan PDF yang tidak terkunci."
                    )
                )
            }
        }
    }

    LaunchedEffect(state.conversation.size, state.phase) {
        if (state.conversation.isNotEmpty()) {
            listState.animateScrollToItem(state.conversation.lastIndex)
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val keyboardBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val contentGutter = if (configuration.screenWidthDp < 360) 16.dp else 20.dp
    val restingGap = if (configuration.screenHeightDp < 700) 8.dp else 12.dp
    val restingBottomPadding = with(density) {
        WaspadAIBottomNavigationHeight + navigationBottom.toDp() + restingGap
    }
    // Follow the IME directly, then ease its last 36 dp into the resting position.
    // This keeps the keyboard's speed while avoiding an abrupt stop or navbar overlap.
    val composerBottomPadding = with(density) {
        val remainingTravel = (keyboardBottom.toDp() + 12.dp - restingBottomPadding)
            .coerceAtLeast(0.dp)
        val settleDistance = 36.dp
        val easedTravel = if (remainingTravel < settleDistance) {
            val fraction = remainingTravel.value / settleDistance.value
            settleDistance * (2f * fraction * fraction - fraction * fraction * fraction)
        } else {
            remainingTravel
        }
        restingBottomPadding + easedTravel
    }
    val verificationComposer: @Composable (Modifier) -> Unit = { composerModifier ->
        VerificationComposer(
            value = state.draft,
            enabled = !isSubmitting,
            modifier = composerModifier,
            onValueChange = { onAction(VerificationAction.InputChanged(it)) },
            onSubmit = {
                onAction(
                    if (state.pendingAttachments.isNotEmpty()) {
                        VerificationAction.SubmitPendingImage
                    } else {
                        VerificationAction.SubmitText
                    }
                )
            },
            pendingAttachments = state.pendingAttachments,
            onRemovePendingAttachment = { index ->
                onAction(VerificationAction.RemovePendingAttachment(index))
            },
            onRequestImageCapture = {
                onAction(VerificationAction.RequestImageCapture)
                imagePicker.launch("image/*")
            },
            onRequestFileCapture = {
                onAction(VerificationAction.RequestImageCapture)
                filePicker.launch(arrayOf("application/pdf"))
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WaspadAiHeader(
                overlayModeEnabled = state.isOverlayModeEnabled,
                enabled = !isSubmitting,
                onToggleOverlayMode = {
                    if (state.isOverlayModeEnabled && !state.isOverlayPrivacyDialogVisible) {
                        FloatingVerifyService.stop(context)
                    }
                    onAction(VerificationAction.RequestOverlayMode)
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = contentGutter,
                    end = contentGutter,
                    top = 8.dp,
                    bottom = WaspadAIBottomNavigationHeight + restingGap + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (state.isHistoryVisible) {
                item {
                    HistoryPanel(
                        items = state.history,
                        isLoading = state.isHistoryLoading,
                        onRefresh = { onAction(VerificationAction.RefreshHistory) },
                        onOpen = { onAction(VerificationAction.OpenHistory(it)) }
                    )
                }
            }
            items(state.conversation) { item ->
                when (item) {
                    is VerificationConversationItem.UserMessage -> {
                        UserMessage(
                            text = item.text,
                            hasAttachment = item.hasAttachment,
                            attachmentName = item.attachmentName,
                            attachmentBytes = item.attachmentBytes,
                            attachmentContentType = item.attachmentContentType,
                            attachmentGroup = item.attachmentGroup,
                        )
                    }
                    is VerificationConversationItem.Analysis -> AnalysisCard(
                        result = item.result,
                        isSample = item.isSample,
                        onShareToCommunity = {
                            onAction(VerificationAction.RequestCommunityPreview)
                        },
                    )
                }
            }
            when (val phase = state.phase) {
                VerificationPhase.Validating -> item { ThinkingBubble("Menyiapkan pemeriksaan…") }
                VerificationPhase.Submitting -> item { ThinkingBubble("WaspadAI sedang memeriksa…") }
                is VerificationPhase.Failure -> item {
                    FailureNotice(phase.message) { onAction(VerificationAction.DismissFailure) }
                }
                else -> Unit
            }
            item { Spacer(Modifier.padding(bottom = 1.dp)) }
            }
            WaspadAIBottomNavigation(
                selectedDestination = "Periksa",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
        verificationComposer(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = contentGutter,
                    end = contentGutter,
                    bottom = composerBottomPadding,
                ),
        )
    }
}

@Composable
private fun CommunityConsentDialog(
    preview: id.waspadai.app.feature.community.domain.CommunityPreview,
    sourceAttachment: ImageVerificationPreview?,
    caption: String,
    ragReuseConsent: Boolean,
    onCaptionChanged: (String) -> Unit,
    onRagReuseConsentChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPublish: () -> Unit,
) {
    val sourceBitmap = remember(sourceAttachment?.imageBytes) {
        sourceAttachment?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview Bagikan ke Koneksi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Tambahkan caption sebelum kasus dipublikasikan. " +
                        "Gambar berasal dari pemeriksaan yang baru kamu kirim."
                )
                if (sourceBitmap != null) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = sourceAttachment?.fileName?.let { "Preview $it" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE7EEF3)),
                        contentScale = ContentScale.Fit,
                    )
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = onCaptionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Caption") },
                    placeholder = { Text("Tuliskan konteks kasus ini…") },
                    supportingText = { Text("Wajib diisi · ${caption.length}/5000") },
                    isError = caption.isBlank(),
                    minLines = 3,
                    maxLines = 6,
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FB)),
                ) {
                    Text(
                        "Ringkasan hasil verifikasi:\n${preview.redactedText}",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF153A52),
                    )
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = ragReuseConsent,
                        onCheckedChange = onRagReuseConsentChanged,
                    )
                    Text("Izinkan kasus ini dipakai sebagai evidence AI berikutnya.")
                }
                Text(
                    "Preview berlaku sampai ${preview.expiresAt}.",
                    color = Color(0xFF557383),
                    fontSize = 12.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        confirmButton = {
            Button(onClick = onPublish, enabled = caption.isNotBlank()) {
                Text("Publikasikan")
            }
        },
    )
}

@Composable
private fun OverlayPrivacyDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aktifkan Tanya Area") },
        text = {
            Text(
                "Tombol Tanyain akan bergeser ke kanan saat aktif. Setelah lanjut, Android meminta izin berbagi layar agar kamu dapat memilih area; gambar hanya ditangkap setelah Kirim area dan baru dianalisis setelah kamu menyetujui pratinjaunya."
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Lanjut")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
    )
}

@Composable
private fun ImageVerificationPreviewCard(
    preview: ImageVerificationPreview,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(preview.imageBytes) {
        BitmapFactory.decodeByteArray(preview.imageBytes, 0, preview.imageBytes.size)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FB)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (preview.fileName.endsWith(".pdf", ignoreCase = true)) {
                    "Preview halaman pertama PDF"
                } else {
                    "Preview gambar pemeriksaan"
                },
                color = Color(0xFF153A52),
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview ${preview.fileName}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE7EEF3)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Preview belum dapat dibuka. Pilih ulang lampiran.", color = Color(0xFFA52219))
            }
            Text(
                "Lampiran belum dikirim. Tulis pesan atau konteks di kolom bawah, " +
                    "lalu tekan kirim untuk memeriksanya.",
                color = Color(0xFF557383),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Hapus lampiran")
                }
            }
        }
    }
}

private data class ImageSelection(
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
)

private fun ImageSelection.toAction() = VerificationAction.ImageSelected(
    imageBytes = bytes,
    contentType = contentType,
    fileName = fileName,
)

private fun Context.readImageSelection(uri: Uri): ImageSelection? {
    val contentType = contentResolver.getType(uri)?.lowercase()
        ?.takeIf { it in supportedImageContentTypes }
        ?: return null
    val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return ImageSelection(
        bytes = bytes,
        contentType = contentType,
        fileName = queryDisplayName(uri) ?: defaultFileName(contentType),
    )
}

private fun Context.readPdfSelection(uri: Uri): ImageSelection? {
    val mimeType = contentResolver.getType(uri)?.lowercase()
    if (mimeType != "application/pdf") return null
    val displayName = queryDisplayName(uri) ?: "dokumen-verifikasi.pdf"
    val bytes = contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use rendererUse@ { renderer ->
            if (renderer.pageCount == 0) return@rendererUse null
            renderer.openPage(0).use { page ->
                val scale = minOf(
                    1f,
                    1600f / page.width,
                    2200f / page.height,
                )
                val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (page.height * scale)
                    .toInt()
                    .coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                ByteArrayOutputStream().use { output ->
                    if (bitmap.compress(Bitmap.CompressFormat.PNG, 95, output)) {
                        output.toByteArray()
                    } else {
                        null
                    }
                }.also { bitmap.recycle() }
            }
        }
    } ?: return null
    return ImageSelection(
        bytes = bytes,
        contentType = "image/png",
        fileName = displayName,
    )
}

private fun Context.queryDisplayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
        ?.takeIf(String::isNotBlank)
}

private fun defaultFileName(contentType: String): String {
    val extension = when (contentType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return "verification-image.$extension"
}

private val supportedImageContentTypes = setOf("image/jpeg", "image/png", "image/webp")

@Preview(showBackground = true, heightDp = 900, widthDp = 412)
@Composable
private fun VerificationScreenPreview() {
    WaspadAITheme {
        VerificationScreen(VerificationUiState(), onAction = {})
    }
}
