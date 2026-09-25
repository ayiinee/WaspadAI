package id.waspadai.app.feature.verification.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.feature.verification.presentation.VerificationAction
import id.waspadai.app.feature.verification.presentation.VerificationConversationItem
import id.waspadai.app.feature.verification.presentation.VerificationPhase
import id.waspadai.app.feature.verification.presentation.VerificationUiState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VerificationChatShell(
    state: VerificationUiState,
    listState: LazyListState,
    scrollDirectionListener: NestedScrollConnection,
    contentGutter: Dp,
    bottomNavigationPadding: Dp,
    showBottomNavigation: Boolean,
    onAction: (VerificationAction) -> Unit,
    onDestinationSelected: (String) -> Unit,
    composer: @Composable (Modifier) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val emptyChat = state.conversation.isEmpty() && state.activeConversationId == null

    LaunchedEffect(state.isDrawerOpen) {
        if (state.isDrawerOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }
            .distinctUntilChanged()
            .collect { value ->
                onAction(
                    if (value == DrawerValue.Open) VerificationAction.OpenDrawer
                    else VerificationAction.CloseDrawer
                )
            }
    }
    LaunchedEffect(state.uiMessage) {
        state.uiMessage?.let { message ->
            val retry = state.uiMessageRetryAction
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = retry?.let { "Coba lagi" },
                withDismissAction = true,
            )
            onAction(VerificationAction.DismissUiMessage)
            if (result == SnackbarResult.ActionPerformed && retry != null) {
                onAction(retry)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f).widthIn(max = 360.dp),
                drawerContainerColor = Color.White,
            ) {
                VerificationDrawerContent(
                    conversations = state.history,
                    isLoading = state.isHistoryLoading,
                    isLoadingMore = state.isHistoryLoadingMore,
                    hasMore = state.historyNextCursor != null,
                    editingId = state.editingConversationId,
                    renameDraft = state.renameDraft,
                    mutationRunning = state.isConversationMutationRunning,
                    onNewChat = { onAction(VerificationAction.NewConversation) },
                    onOpen = { onAction(VerificationAction.OpenConversation(it)) },
                    onLoadMore = { onAction(VerificationAction.LoadMoreHistory) },
                    onStartRename = { onAction(VerificationAction.StartRenameConversation(it)) },
                    onRenameChanged = { onAction(VerificationAction.RenameDraftChanged(it)) },
                    onConfirmRename = { onAction(VerificationAction.ConfirmRenameConversation) },
                    onCancelRename = { onAction(VerificationAction.CancelRenameConversation) },
                    onDelete = { onAction(VerificationAction.RequestDeleteConversation(it)) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(Modifier.fillMaxSize()) {
                VerificationMobileHeader(
                    title = if (emptyChat) "WaspadAI" else state.activeConversationTitle,
                    onOpenDrawer = { onAction(VerificationAction.OpenDrawer) },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).nestedScroll(scrollDirectionListener),
                    contentPadding = PaddingValues(horizontal = contentGutter, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (emptyChat && !state.isConversationLoading) item {
                        Column(
                            modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Apa yang ingin kamu periksa?",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF122D3D),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Kirim teks, gambar, atau dokumen yang ingin diverifikasi.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = Color(0xFF557383),
                            )
                        }
                    }
                    if (state.isConversationLoading) item { ThinkingBubble("Memuat percakapan…") }
                    items(state.conversation) { item ->
                        when (item) {
                            is VerificationConversationItem.UserMessage -> UserMessage(
                                text = item.text,
                                hasAttachment = item.hasAttachment,
                                attachmentName = item.attachmentName,
                                attachmentBytes = item.attachmentBytes,
                                attachmentContentType = item.attachmentContentType,
                                attachmentGroup = item.attachmentGroup,
                            )
                            is VerificationConversationItem.Analysis -> AnalysisCard(
                                result = item.result,
                                isSample = item.isSample,
                                onShareToCommunity = { onAction(VerificationAction.RequestCommunityPreview) },
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
                }
                composer(
                    Modifier.fillMaxWidth().background(Color.White)
                        .padding(horizontal = contentGutter, vertical = 10.dp)
                        .then(if (emptyChat) Modifier.padding(bottom = bottomNavigationPadding) else Modifier.navigationBarsPadding())
                        .imePadding(),
                )
            }
            AnimatedVisibility(
                visible = emptyChat && showBottomNavigation,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                WaspadAIBottomNavigation("Periksa", onDestinationSelected)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (state.pendingDeleteConversationId != null) {
        AlertDialog(
            onDismissRequest = { onAction(VerificationAction.CancelDeleteConversation) },
            title = { Text("Hapus percakapan?") },
            text = { Text("Percakapan akan hilang dari riwayat dan tidak dapat dikembalikan.") },
            dismissButton = {
                TextButton(onClick = { onAction(VerificationAction.CancelDeleteConversation) }) { Text("Batal") }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(VerificationAction.ConfirmDeleteConversation) },
                    enabled = !state.isConversationMutationRunning,
                ) { Text("Hapus", color = Color(0xFFC62828)) }
            },
        )
    }
}
