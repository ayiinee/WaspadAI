package id.waspadai.app.feature.verification.presentation

import android.app.Activity
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfRenderer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.waspadai.app.core.capture.CaptureEvent
import id.waspadai.app.core.capture.CaptureResultBus
import id.waspadai.app.core.overlay.FloatingVerifyService
import id.waspadai.app.core.trigger.WaspadAIQuickTileService
import id.waspadai.app.R
import id.waspadai.app.feature.verification.presentation.component.AnalysisCard
import id.waspadai.app.feature.verification.presentation.component.FailureNotice
import id.waspadai.app.feature.verification.presentation.component.ThinkingBubble
import id.waspadai.app.feature.verification.presentation.component.UserMessage
import id.waspadai.app.feature.verification.presentation.component.VerificationComposer
import id.waspadai.app.feature.verification.presentation.component.VerificationChatShell
import id.waspadai.app.core.ui.waspadAIBottomNavigationContentPadding
import id.waspadai.app.ui.theme.WaspadAITheme
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VerificationRoute(
    viewModel: VerificationViewModel,
    isChatScreen: Boolean = false,
    onBackToConversations: () -> Unit = {},
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
        isChatScreen = isChatScreen,
        onBackToConversations = onBackToConversations,
        onDestinationSelected = onDestinationSelected,
    )
}

@Composable
fun VerificationScreen(
    state: VerificationUiState,
    onAction: (VerificationAction) -> Unit,
    isChatScreen: Boolean = false,
    onBackToConversations: () -> Unit = {},
    onDestinationSelected: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val composerFocusRequester = remember { FocusRequester() }
    var isBottomNavigationVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val quickTileLabel = stringResource(R.string.quick_tile_label)
    val pickerScope = rememberCoroutineScope()
    var showQuickAccess by rememberSaveable { mutableStateOf(false) }
    val roleManager = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
        } else {
            null
        }
    }
    var assistantRoleHeld by remember(roleManager) {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
                roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        )
    }
    val assistantRoleRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        assistantRoleHeld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        if (!assistantRoleHeld) {
            Toast.makeText(
                context,
                "Pilih WaspadAI pada menu Aplikasi asisten digital.",
                Toast.LENGTH_LONG,
            ).show()
            context.openAssistantSettings()
        }
    }
    DisposableEffect(lifecycleOwner, roleManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                assistantRoleHeld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isSubmitting = state.phase is VerificationPhase.Validating || state.phase is VerificationPhase.Submitting

    BackHandler(enabled = isChatScreen, onBack = onBackToConversations)

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collect { (index, offset, isScrolling) ->
                if (index == 0 && offset <= 8) {
                    isBottomNavigationVisible = true
                } else if (isScrolling) {
                    val scrollingDown = index > previousIndex ||
                        (index == previousIndex && offset > previousOffset)
                    val scrollingUp = index < previousIndex ||
                        (index == previousIndex && offset < previousOffset)
                    when {
                        scrollingDown -> isBottomNavigationVisible = false
                        scrollingUp -> isBottomNavigationVisible = true
                    }
                }
                previousIndex = index
                previousOffset = offset
            }
    }
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
                        source = event.source,
                    )
                )
                is CaptureEvent.TextConversation -> onAction(
                    VerificationAction.TextConversationReady(
                        text = event.text,
                        sourceUrl = event.sourceUrl,
                        pageContext = event.pageContext,
                        turns = event.turns,
                        source = event.source,
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

    if (showQuickAccess) {
        QuickAccessDialog(
            assistantAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true,
            assistantEnabled = assistantRoleHeld,
            bubbleEnabled = state.isOverlayModeEnabled,
            onDismiss = { showQuickAccess = false },
            onSetAssistant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true
                ) {
                    runCatching {
                        assistantRoleRequest.launch(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                        )
                    }.onFailure {
                        context.openAssistantSettings()
                    }
                } else {
                    context.openAssistantSettings()
                }
            },
            onOpenAssistantSettings = { context.openAssistantSettings() },
            onAddQuickTile = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, WaspadAIQuickTileService::class.java),
                        quickTileLabel,
                        Icon.createWithResource(context, R.mipmap.ic_launcher),
                        context.mainExecutor,
                    ) {
                        Toast.makeText(
                            context,
                            "Permintaan tile Periksa layar selesai.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Buka panel Quick Settings lalu tambahkan tile Periksa layar.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onToggleBubble = {
                if (state.isOverlayModeEnabled && !state.isOverlayPrivacyDialogVisible) {
                    FloatingVerifyService.stop(context)
                }
                onAction(VerificationAction.RequestOverlayMode)
            },
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
    LaunchedEffect(state.composerFocusRequest) {
        if (state.composerFocusRequest > 0) {
            composerFocusRequester.requestFocus()
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val keyboardBottom = WindowInsets.ime.getBottom(density)
    val shouldShowBottomNavigation = isBottomNavigationVisible && keyboardBottom == 0
    val contentGutter = if (configuration.screenWidthDp < 360) 16.dp else 20.dp
    val bottomNavigationPadding = waspadAIBottomNavigationContentPadding()
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
            focusRequester = composerFocusRequester,
        )
    }

    VerificationChatShell(
        state = state,
        listState = listState,
        contentGutter = contentGutter,
        bottomNavigationPadding = bottomNavigationPadding,
        showBottomNavigation = shouldShowBottomNavigation,
        onAction = onAction,
        onDestinationSelected = onDestinationSelected,
        composer = verificationComposer,
    )
}

@Composable
private fun QuickAccessDialog(
    assistantAvailable: Boolean,
    assistantEnabled: Boolean,
    bubbleEnabled: Boolean,
    onDismiss: () -> Unit,
    onSetAssistant: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onAddQuickTile: () -> Unit,
    onToggleBubble: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Akses Cepat WaspadAI") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Pilih cara memanggil verifikasi saat kamu menemukan konten mencurigakan. " +
                        "Tidak ada screenshot atau teks yang dikirim sebelum kamu menekan Periksa sekarang."
                )
                QuickAccessStatus(
                    title = "Gesture assistant perangkat",
                    status = when {
                        assistantEnabled -> "Aktif"
                        assistantAvailable -> "Belum dipilih"
                        else -> "Tidak didukung langsung"
                    },
                    detail = "Gesture berbeda tiap perangkat: long-press tombol samping, home-hold, atau corner swipe.",
                )
                Button(
                    onClick = onSetAssistant,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !assistantEnabled,
                ) {
                    Text(if (assistantEnabled) "WaspadAI sudah menjadi assistant" else "Pilih WaspadAI sebagai assistant")
                }
                Text(
                    "Mengganti assistant sebelumnya memerlukan persetujuan sistem. Kamu dapat mengembalikannya melalui Default Apps.",
                    fontSize = 12.sp,
                    color = Color(0xFF557383),
                )
                TextButton(
                    onClick = onOpenAssistantSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Buka pengaturan assistant perangkat")
                }
                QuickAccessStatus(
                    title = "Konteks layar",
                    status = if (assistantEnabled) "Siap bila diizinkan aplikasi sumber" else "Memerlukan assistant",
                    detail = "Secure window tetap dilindungi. WaspadAI tidak mencoba melewati FLAG_SECURE.",
                )
                QuickAccessStatus(
                    title = "Quick Settings",
                    status = "Fallback",
                    detail = "Tile Periksa layar meminta persetujuan capture satu kali untuk setiap pemeriksaan.",
                )
                TextButton(onClick = onAddQuickTile, modifier = Modifier.fillMaxWidth()) {
                    Text("Tambahkan tile Periksa layar")
                }
                QuickAccessStatus(
                    title = "Floating Verify",
                    status = if (bubbleEnabled) "Aktif" else "Nonaktif (disarankan)",
                    detail = "Fallback lanjutan yang membutuhkan izin tampil di atas aplikasi lain.",
                )
                TextButton(onClick = onToggleBubble, modifier = Modifier.fillMaxWidth()) {
                    Text(if (bubbleEnabled) "Matikan floating bubble" else "Aktifkan floating bubble")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Selesai") }
        },
    )
}

private fun Context.openAssistantSettings() {
    val candidates = listOf(
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    )
    val target = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
    if (target == null) {
        Toast.makeText(
            this,
            "Pengaturan assistant tidak tersedia di perangkat ini.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    runCatching {
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }.onFailure {
        Toast.makeText(
            this,
            "Pengaturan assistant belum dapat dibuka.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

@Composable
private fun QuickAccessStatus(title: String, status: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FB)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(status, color = Color(0xFF0A6FA4), fontSize = 12.sp)
            }
            Text(detail, color = Color(0xFF557383), fontSize = 12.sp)
        }
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
