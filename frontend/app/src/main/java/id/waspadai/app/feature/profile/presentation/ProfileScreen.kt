package id.waspadai.app.feature.profile.presentation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.core.ui.WaspadAIPageHeader
import id.waspadai.app.feature.profile.domain.ProfileActivityItem
import id.waspadai.app.feature.profile.domain.ProfileLearningItem
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue
import id.waspadai.app.ui.theme.WaspadAIMuted
import kotlinx.coroutines.launch

@Composable
fun ProfileRoute(
    state: ProfileUiState,
    accessToken: String,
    onAction: (ProfileAction) -> Unit,
    onDestinationSelected: (String) -> Unit,
    onChangePassword: suspend (String) -> Result<Unit>,
    onLogout: suspend () -> Unit,
    onCommunityPostSelected: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var passwordDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            onAction(ProfileAction.DismissMessage)
        }
    }

    BackHandler(state.page != ProfilePage.Dashboard) {
        onAction(ProfileAction.Back)
    }

    Scaffold(
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Profil",
                onDestinationSelected = onDestinationSelected,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WaspadAIPageHeader(
                title = state.page.title(),
                modifier = Modifier.testTag("profile-header"),
                onBack = if (state.page == ProfilePage.Dashboard) null else {
                    { onAction(ProfileAction.Back) }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.loading -> ProfileLoadingState()
                    state.page == ProfilePage.Dashboard -> ProfileDashboard(
                        state = state,
                        accessToken = accessToken,
                        onAction = onAction,
                    )
                    state.page == ProfilePage.Edit -> EditProfilePage(
                        state = state,
                        accessToken = accessToken,
                        onAction = onAction,
                    )
                    state.page == ProfilePage.Settings -> SettingsPage(
                        onPassword = { passwordDialogVisible = true },
                        onLogout = { scope.launch { onLogout() } },
                    )
                    state.page == ProfilePage.ItemDetail -> ItemDetailPage(state)
                    else -> ActivityDetailPage(
                        state = state,
                        onAction = onAction,
                        onCommunityPostSelected = onCommunityPostSelected,
                    )
                }
            }
        }
    }

    if (passwordDialogVisible) {
        ProfilePasswordDialog(
            onDismiss = { passwordDialogVisible = false },
            onSubmit = { password ->
                scope.launch {
                    onChangePassword(password).fold(
                        onSuccess = {
                            snackbarHostState.showSnackbar("Kata sandi berhasil diperbarui.")
                        },
                        onFailure = {
                            snackbarHostState.showSnackbar(
                                it.message ?: "Kata sandi belum dapat diperbarui.",
                            )
                        },
                    )
                    passwordDialogVisible = false
                }
            },
        )
    }
}

@Composable
private fun ProfileDashboard(
    state: ProfileUiState,
    accessToken: String,
    onAction: (ProfileAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.profile?.let { profile ->
            item(key = "profile-identity") {
                ProfileIdentityCard(
                    profile = profile,
                    accessToken = accessToken,
                    onEdit = { onAction(ProfileAction.OpenPage(ProfilePage.Edit)) },
                )
            }
        }

        state.error?.let { message ->
            item(key = "profile-error") {
                ProfileErrorState(
                    message = message,
                    onRetry = { onAction(ProfileAction.Refresh) },
                )
            }
        }

        item(key = "activity-heading") {
            ProfileSectionTitle(
                title = "Aktivitas Saya",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item(key = "activity-menu") {
            Column {
                ProfileNavigationRow(
                    title = "Pemeriksaan Saya",
                    subtitle = "Riwayat pemeriksaan informasi",
                    icon = Icons.AutoMirrored.Rounded.FactCheck,
                    testTag = "profile-action-Verifications",
                    onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Verifications)) },
                )
                ProfileNavigationRow(
                    title = "Publikasi Koneksi",
                    subtitle = "Kelola publikasi yang dibagikan",
                    icon = Icons.Rounded.People,
                    testTag = "profile-action-Publications",
                    onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Publications)) },
                )
                ProfileNavigationRow(
                    title = "Aktivitas Komunitas",
                    subtitle = "Penilaian, bukti, dan kontribusi",
                    icon = Icons.Rounded.Groups,
                    testTag = "profile-action-Community",
                    onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Community)) },
                )
                ProfileNavigationRow(
                    title = "Progres Pembelajaran",
                    subtitle = "Materi, progres, dan hasil latihan",
                    icon = Icons.Rounded.School,
                    testTag = "profile-action-Learning",
                    onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Learning)) },
                )
            }
        }

        item(key = "account-heading") {
            ProfileSectionTitle(
                title = "Akun",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item(key = "settings") {
            ProfileNavigationRow(
                title = "Pengaturan Akun",
                subtitle = "Kata sandi dan sesi akun",
                icon = Icons.Rounded.Settings,
                testTag = "profile-action-Settings",
                onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Settings)) },
            )
        }
    }
}

@Composable
private fun EditProfilePage(
    state: ProfileUiState,
    accessToken: String,
    onAction: (ProfileAction) -> Unit,
) {
    val profile = state.profile ?: return
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio.orEmpty()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            readAvatar(context, selectedUri)?.let { (bytes, type) ->
                onAction(ProfileAction.UploadAvatar(bytes, type))
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ProfileAvatarEditor(
                profile = profile,
                accessToken = accessToken,
                enabled = !state.saving,
                onPickAvatar = { picker.launch("image/*") },
                onDeleteAvatar = if (profile.avatarUrl != null) {
                    { onAction(ProfileAction.DeleteAvatar) }
                } else null,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nama tampilan") },
                supportingText = { Text("${name.length}/80") },
                singleLine = true,
                enabled = !state.saving,
                colors = profileTextFieldColors(),
            )
        }
        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 500) bio = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bio") },
                supportingText = { Text("${bio.length}/500") },
                minLines = 3,
                enabled = !state.saving,
                colors = profileTextFieldColors(),
            )
        }
        state.error?.let { message ->
            item {
                ProfileErrorState(message = message)
            }
        }
        item {
            Button(
                onClick = {
                    onAction(
                        ProfileAction.SaveProfile(
                            name = name.trim(),
                            bio = bio.trim().ifBlank { null },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile-save"),
                enabled = name.isNotBlank() && !state.saving,
                colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Simpan perubahan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActivityDetailPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onCommunityPostSelected: (String) -> Unit,
) {
    var selectedStatus by remember(state.page) { mutableStateOf("Semua") }
    val statuses = listOf("Semua") + state.activities.map { it.status }.distinct()
    val visibleActivities = if (selectedStatus == "Semua") {
        state.activities
    } else {
        state.activities.filter { it.status == selectedStatus }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.page != ProfilePage.Learning && statuses.size > 1) {
            item(key = "status-filters") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    statuses.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(status) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WaspadAILightBlue,
                                selectedLabelColor = WaspadAIDarkBlue,
                            ),
                        )
                    }
                }
            }
        }

        if (state.detailLoading && state.activities.isEmpty() && state.learning.isEmpty()) {
            item(key = "detail-loading") { ProfileInlineLoadingState() }
        }

        state.error?.let { message ->
            item(key = "detail-error") {
                ProfileErrorState(
                    message = message,
                    onRetry = { onAction(ProfileAction.OpenPage(state.page)) },
                )
            }
        }

        if (
            !state.detailLoading &&
            visibleActivities.isEmpty() &&
            state.learning.isEmpty() &&
            state.error == null
        ) {
            item(key = "detail-empty") {
                ProfileEmptyState(
                    title = "Belum ada aktivitas",
                    description = state.page.emptyDescription(),
                )
            }
        }

        items(visibleActivities, key = { it.id }) { item ->
            ProfileActivityCard(item = item) {
                if (
                    item.communityId != null &&
                    item.status != "Ditarik" &&
                    state.page in listOf(ProfilePage.Verifications, ProfilePage.Publications)
                ) {
                    onCommunityPostSelected(item.communityId)
                } else {
                    onAction(ProfileAction.OpenActivity(item))
                }
            }
        }

        items(state.learning, key = { it.id }) { item ->
            ProfileLearningCard(item = item) {
                onAction(ProfileAction.OpenLearning(item))
            }
        }

        if (state.hasMore) {
            item(key = "load-more") {
                OutlinedButton(
                    onClick = { onAction(ProfileAction.LoadMore) },
                    enabled = !state.detailLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (state.detailLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = WaspadAIBlue,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Muat lebih banyak", color = WaspadAIBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemDetailPage(state: ProfileUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.selectedActivity?.let { item ->
            item { ActivityItemDetail(item) }
        }
        state.selectedLearning?.let { item ->
            item { LearningItemDetail(item) }
        }
        if (state.selectedActivity == null && state.selectedLearning == null) {
            item {
                ProfileEmptyState(
                    title = "Detail tidak tersedia",
                    description = "Kembali dan pilih aktivitas yang ingin dilihat.",
                )
            }
        }
    }
}

@Composable
private fun ActivityItemDetail(item: ProfileActivityItem) {
    ProfileDetailSurface {
        Text(text = item.title, color = WaspadAIDarkBlue, fontWeight = FontWeight.Bold)
        ProfileStatusPill(item.status)
        Text(item.subtitle, color = Color(0xFF455A64))
        Text("Dibuat ${item.createdAt}", color = WaspadAIMuted)
        Text("ID ${item.id}", color = WaspadAIMuted)
    }
}

@Composable
private fun LearningItemDetail(item: ProfileLearningItem) {
    ProfileDetailSurface {
        Text(text = item.title, color = WaspadAIDarkBlue, fontWeight = FontWeight.Bold)
        ProfileLearningProgress(item)
        Text("${item.completedLessons} dari ${item.totalLessons} pelajaran selesai")
        Text("Progres ${item.progressPercent.toInt()}%")
        Text("Skor terakhir: ${item.latestScore?.toInt()?.toString() ?: "Belum ada"}")
        Text("Skor terbaik: ${item.bestScore?.toInt()?.toString() ?: "Belum ada"}")
    }
}

@Composable
private fun SettingsPage(
    onPassword: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        item {
            ProfileSettingsGroup(onPassword = onPassword, onLogout = onLogout)
        }
    }
}

@Composable
private fun profileTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WaspadAIBlue,
    focusedLabelColor = WaspadAIBlue,
    cursorColor = WaspadAIBlue,
    unfocusedBorderColor = WaspadAILightBlue,
    disabledBorderColor = WaspadAILightBlue,
)

private fun ProfilePage.title(): String = when (this) {
    ProfilePage.Dashboard -> "Profil"
    ProfilePage.Verifications -> "Pemeriksaan Saya"
    ProfilePage.Publications -> "Publikasi Koneksi"
    ProfilePage.Community -> "Aktivitas Komunitas"
    ProfilePage.Learning -> "Progres Pembelajaran"
    ProfilePage.Edit -> "Edit Profil"
    ProfilePage.Settings -> "Pengaturan Akun"
    ProfilePage.ItemDetail -> "Detail"
}

private fun ProfilePage.emptyDescription(): String = when (this) {
    ProfilePage.Verifications -> "Pemeriksaan yang kamu lakukan akan muncul di sini."
    ProfilePage.Publications -> "Publikasi yang kamu kirim ke Koneksi akan muncul di sini."
    ProfilePage.Community -> "Penilaian dan kontribusimu akan muncul di sini."
    ProfilePage.Learning -> "Mulai materi untuk melihat progres pembelajaranmu."
    else -> "Belum ada data untuk ditampilkan."
}

private fun readAvatar(context: Context, uri: Uri): Pair<ByteArray, String>? = runCatching {
    val type = context.contentResolver.getType(uri) ?: "image/jpeg"
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { it to type }
}.getOrNull()
