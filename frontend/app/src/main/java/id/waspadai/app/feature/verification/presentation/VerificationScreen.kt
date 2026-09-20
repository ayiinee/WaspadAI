package id.waspadai.app.feature.verification.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import id.waspadai.app.ui.theme.WaspadAITheme

@Composable
fun VerificationRoute(
    viewModel: VerificationViewModel,
    onDestinationSelected: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    var activeTab by rememberSaveable { mutableStateOf("Periksa") }
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
            CommunityConsentDialog(
                preview = sharePhase.preview,
                ragReuseConsent = state.communityShare.ragReuseConsent,
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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selection = runCatching { context.readImageSelection(uri) }.getOrNull()
        if (selection == null) {
            onAction(
                VerificationAction.ImageSelectionFailed(
                    "Gambar belum dapat dibaca. Pilih file JPG, PNG, atau WEBP lain."
                )
            )
        } else {
            onAction(
                VerificationAction.ImageSelected(
                    imageBytes = selection.bytes,
                    contentType = selection.contentType,
                    fileName = selection.fileName,
                )
            )
        }
    }

    LaunchedEffect(state.conversation.size, state.phase) {
        if (state.conversation.isNotEmpty()) {
            listState.animateScrollToItem(state.conversation.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
    ) {
        WaspadAiHeader(onHistoryClick = { onAction(VerificationAction.ToggleHistory) })
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
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
            state.pendingImagePreview?.let { preview ->
                item {
                    ImageVerificationPreviewCard(
                        preview = preview,
                        onDismiss = { onAction(VerificationAction.DismissImagePreview) },
                    )
                }
            }
            items(state.conversation) { item ->
                when (item) {
                    is VerificationConversationItem.UserMessage -> {
                        UserMessage(item.text, item.hasAttachment, item.attachmentName)
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
        VerificationComposer(
            value = state.draft,
            enabled = !isSubmitting,
            overlayModeEnabled = state.isOverlayModeEnabled,
            onValueChange = { onAction(VerificationAction.InputChanged(it)) },
            onSubmit = {
                onAction(
                    if (state.pendingImagePreview != null) {
                        VerificationAction.SubmitPendingImage
                    } else {
                        VerificationAction.SubmitText
                    }
                )
            },
            hasPendingImage = state.pendingImagePreview != null,
            onToggleOverlayMode = {
                if (state.isOverlayModeEnabled) {
                    FloatingVerifyService.stop(context)
                    onAction(VerificationAction.RequestOverlayMode)
                } else {
                    onAction(VerificationAction.RequestOverlayMode)
                }
            },
            onRequestImageCapture = {
                onAction(VerificationAction.RequestImageCapture)
                imagePicker.launch("image/*")
            }
        )
        WaspadAIBottomNavigation(
            selectedDestination = activeTab,
            onDestinationSelected = {
                activeTab = it
                onDestinationSelected(it)
            },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun CommunityConsentDialog(
    preview: id.waspadai.app.feature.community.domain.CommunityPreview,
    ragReuseConsent: Boolean,
    onRagReuseConsentChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPublish: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preview Bagikan ke Koneksi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Periksa teks aman berikut sebelum dipublikasikan. " +
                        "Identitas pribadi dan data mentah tidak ikut dibagikan."
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FB)),
                ) {
                    Text(
                        preview.redactedText,
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
            Button(onClick = onPublish) {
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
        title = { Text("Aktifkan overlay WaspadAI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WaspadAI akan menampilkan bubble di atas aplikasi lain.")
                Text("Screenshot hanya diambil setelah kamu menekan Verify pada bubble.")
                Text("Hasil tangkapan layar akan ditampilkan untuk preview sebelum dikirim.")
            }
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
            Text("Preview gambar pemeriksaan", color = Color(0xFF153A52))
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview tangkapan layar overlay",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE7EEF3)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Preview belum dapat dibuka. Ambil ulang screenshot.", color = Color(0xFFA52219))
            }
            Text(
                "Gambar belum dikirim. Tulis pesan atau konteks di kolom bawah, " +
                    "lalu tekan kirim untuk memeriksa gambar ini.",
                color = Color(0xFF557383),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Hapus gambar")
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
