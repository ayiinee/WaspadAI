package id.waspadai.app.feature.verification.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.R
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.ui.theme.WaspadAIBlue
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VerificationMobileHeader(
    title: String,
    onOpenDrawer: () -> Unit,
    onOpenQuickAccess: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth().background(WaspadAIBlue)) {
        Image(
            painter = painterResource(R.drawable.community_header_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = .68f,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("verification-hamburger")) {
                Icon(Icons.Rounded.Menu, contentDescription = "Buka riwayat percakapan", tint = Color.White)
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            TextButton(
                onClick = onOpenQuickAccess,
                modifier = Modifier.testTag("verification-quick-access"),
            ) {
                Text(
                    "Akses Cepat",
                    color = Color.White.copy(alpha = .9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun VerificationDrawerContent(
    conversations: List<VerificationConversationSummary>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    editingId: String?,
    renameDraft: String,
    mutationRunning: Boolean,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onLoadMore: () -> Unit,
    onStartRename: (String) -> Unit,
    onRenameChanged: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, hasMore, isLoadingMore) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalItems) ->
                if (hasMore && !isLoadingMore && totalItems > 0 && lastVisible >= totalItems - 3) {
                    onLoadMore()
                }
            }
    }
    Column(
        modifier = Modifier.fillMaxHeight().fillMaxWidth()
            .padding(top = 12.dp).testTag("verification-drawer"),
    ) {
        Text("WaspadAI", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WaspadAIBlue,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
        TextButton(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("verification-new-chat-drawer"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("Chat Baru", modifier = Modifier.weight(1f).padding(start = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        }
        HorizontalDivider(color = Color(0xFFD8E4EC))
        Text("Riwayat", fontWeight = FontWeight.SemiBold, color = Color(0xFF557383),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
        when {
            isLoading && conversations.isEmpty() -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
            conversations.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Belum ada percakapan", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Mulai chat baru untuk melakukan pemeriksaan.", color = Color(0xFF557383), fontSize = 13.sp)
            }
            else -> LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                groupedConversations(conversations).forEach { (label, entries) ->
                    item(key = "group-$label") {
                        Text(label, color = Color(0xFF718793), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 5.dp))
                    }
                    items(entries, key = { it.conversationId }) { conversation ->
                        ConversationDrawerRow(
                            conversation = conversation,
                            isEditing = editingId == conversation.conversationId,
                            renameDraft = renameDraft,
                            mutationRunning = mutationRunning,
                            onOpen = { onOpen(conversation.conversationId) },
                            onStartRename = { onStartRename(conversation.conversationId) },
                            onRenameChanged = onRenameChanged,
                            onConfirmRename = onConfirmRename,
                            onCancelRename = onCancelRename,
                            onDelete = { onDelete(conversation.conversationId) },
                        )
                    }
                }
                if (hasMore) item {
                    TextButton(onClick = onLoadMore, enabled = !isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                        if (isLoadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Muat lebih banyak")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationDrawerRow(
    conversation: VerificationConversationSummary,
    isEditing: Boolean,
    renameDraft: String,
    mutationRunning: Boolean,
    onOpen: () -> Unit,
    onStartRename: () -> Unit,
    onRenameChanged: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).clickable(enabled = !isEditing, onClick = onOpen)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEditing) {
            OutlinedTextField(
                value = renameDraft,
                onValueChange = onRenameChanged,
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("conversation-rename-input"),
            )
            IconButton(onClick = onConfirmRename, enabled = !mutationRunning && renameDraft.isNotBlank()) {
                Icon(Icons.Rounded.Check, contentDescription = "Simpan judul")
            }
            IconButton(onClick = onCancelRename, enabled = !mutationRunning) {
                Icon(Icons.Rounded.Close, contentDescription = "Batalkan rename")
            }
        } else {
            Text(
                conversation.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu ${conversation.title}", tint = Color(0xFF65747C))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Ubah nama") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onStartRename() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Color(0xFFC62828)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFC62828)) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFFD8E4EC), modifier = Modifier.padding(start = 20.dp))
}

private fun groupedConversations(
    conversations: List<VerificationConversationSummary>,
): List<Pair<String, List<VerificationConversationSummary>>> {
    val today = LocalDate.now()
    return conversations.groupBy { conversation ->
        val date = runCatching {
            OffsetDateTime.parse(conversation.updatedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
        when (date) {
            today -> "Hari ini"
            today.minusDays(1) -> "Kemarin"
            else -> "Sebelumnya"
        }
    }.let { groups ->
        listOf("Hari ini", "Kemarin", "Sebelumnya").mapNotNull { label ->
            groups[label]?.let { label to it }
        }
    }
}
