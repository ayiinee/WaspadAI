package id.waspadai.app.feature.profile.presentation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.feature.profile.domain.*
import id.waspadai.app.ui.theme.WaspadAIBlue
import kotlinx.coroutines.launch

private val PageBackground = Color(0xFFF5F8FA)

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
    var passwordDialog by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onAction(ProfileAction.DismissMessage)
        }
    }
    BackHandler(state.page != ProfilePage.Dashboard) { onAction(ProfileAction.Back) }
    Scaffold(
        containerColor = PageBackground,
        bottomBar = { WaspadAIBottomNavigation("Profil", onDestinationSelected) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = WaspadAIBlue)
                state.page == ProfilePage.Dashboard -> Dashboard(state, accessToken, onAction)
                state.page == ProfilePage.Edit -> EditProfile(state, accessToken, onAction)
                state.page == ProfilePage.Settings -> SettingsPage(
                    onBack = { onAction(ProfileAction.Back) },
                    onPassword = { passwordDialog = true },
                    onLogout = { scope.launch { onLogout() } },
                )
                state.page == ProfilePage.ItemDetail -> ItemDetailPage(state) { onAction(ProfileAction.Back) }
                else -> DetailPage(state, onAction, onCommunityPostSelected)
            }
        }
    }
    if (passwordDialog) PasswordDialog(onDismiss = { passwordDialog = false }, onSubmit = { password -> scope.launch {
        onChangePassword(password).fold(
            onSuccess = { snackbarHostState.showSnackbar("Kata sandi berhasil diperbarui.") },
            onFailure = { snackbarHostState.showSnackbar(it.message ?: "Kata sandi belum dapat diperbarui.") },
        )
        passwordDialog = false
    } })
}

@Composable private fun Dashboard(state: ProfileUiState, token: String, onAction: (ProfileAction) -> Unit) {
    val profile = state.profile
    val overview = state.overview
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Profil", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183B4E)) }
        if (profile != null) item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile, token, 72)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(profile.email.orEmpty(), color = Color(0xFF607D8B), fontSize = 13.sp)
                        profile.bio?.let { Text(it, color = Color(0xFF455A64), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    IconButton(onClick = { onAction(ProfileAction.OpenPage(ProfilePage.Edit)) }) { Icon(Icons.Rounded.Edit, "Edit profil", tint = WaspadAIBlue) }
                }
            }
        }
        state.error?.let { item { ErrorCard(it) { onAction(ProfileAction.Refresh) } } }
        if (overview != null) {
            item { DashboardCard("Pemeriksaan Saya", "${overview.verificationTotal} kasus", "${overview.verificationPrivate} privat • ${overview.verificationPublished + overview.verificationVerified} dipublikasikan", Icons.Rounded.FactCheck) { onAction(ProfileAction.OpenPage(ProfilePage.Verifications)) } }
            item { DashboardCard("Publikasi Koneksi", "${overview.publicationTotal} postingan", "${overview.publicationUnverified} menunggu • ${overview.publicationVerified} terverifikasi", Icons.Rounded.People) { onAction(ProfileAction.OpenPage(ProfilePage.Publications)) } }
            item { DashboardCard("Aktivitas Komunitas", "${overview.assessments} penilaian", "${overview.evidenceAdded} bukti • ${overview.resolvedCases} terselesaikan", Icons.Rounded.Groups) { onAction(ProfileAction.OpenPage(ProfilePage.Community)) } }
            item { DashboardCard("Progres Pembelajaran", "${overview.completedModules}/${overview.totalModules} modul selesai", "${overview.progressPercent.toInt()}% keseluruhan • Skor terbaik ${overview.bestScore?.toInt() ?: 0}", Icons.Rounded.School) { onAction(ProfileAction.OpenPage(ProfilePage.Learning)) } }
        }
        item { DashboardCard("Pengaturan Akun", "Keamanan dan sesi", "Ubah kata sandi atau keluar dari akun", Icons.Rounded.Settings) { onAction(ProfileAction.OpenPage(ProfilePage.Settings)) } }
    }
}

@Composable private fun DashboardCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFFE7F3F8), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = WaspadAIBlue) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(value, color = WaspadAIBlue, fontWeight = FontWeight.Bold); Text(detail, color = Color(0xFF607D8B), fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF90A4AE))
        }
    }
}

@Composable private fun EditProfile(state: ProfileUiState, token: String, onAction: (ProfileAction) -> Unit) {
    val profile = state.profile ?: return
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio.orEmpty()) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readAvatar(context, it)?.let { data -> onAction(ProfileAction.UploadAvatar(data.first, data.second)) } } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Edit Profil") { onAction(ProfileAction.Back) } }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { ProfileAvatar(profile, token, 96); TextButton(onClick = { picker.launch("image/*") }) { Text("Ganti foto") }; if (profile.avatarUrl != null) TextButton(onClick = { onAction(ProfileAction.DeleteAvatar) }) { Text("Hapus foto", color = Color(0xFFD32F2F)) } } } }
        item { OutlinedTextField(name, { if (it.length <= 80) name = it }, Modifier.fillMaxWidth(), label = { Text("Nama tampilan") }, singleLine = true) }
        item { OutlinedTextField(bio, { if (it.length <= 500) bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3) }
        state.error?.let { item { ErrorCard(it) {} } }
        item { Button(onClick = { onAction(ProfileAction.SaveProfile(name.trim(), bio.trim().ifBlank { null })) }, enabled = name.isNotBlank() && !state.saving, modifier = Modifier.fillMaxWidth()) { if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Simpan perubahan") } }
    }
}

@Composable private fun DetailPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onCommunityPostSelected: (String) -> Unit,
) {
    val title = when (state.page) { ProfilePage.Verifications -> "Pemeriksaan Saya"; ProfilePage.Publications -> "Publikasi Koneksi"; ProfilePage.Community -> "Aktivitas Komunitas"; ProfilePage.Learning -> "Progres Pembelajaran"; else -> "Profil" }
    var selectedStatus by remember(state.page) { mutableStateOf("Semua") }
    val statuses = listOf("Semua") + state.activities.map { it.status }.distinct()
    val visibleActivities = if (selectedStatus == "Semua") state.activities else state.activities.filter { it.status == selectedStatus }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(title) { onAction(ProfileAction.Back) } }
        if (state.page != ProfilePage.Learning && statuses.size > 1) item { Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { statuses.forEach { status -> FilterChip(selected = selectedStatus == status, onClick = { selectedStatus = status }, label = { Text(status) }) } } }
        if (state.detailLoading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WaspadAIBlue) } }
        state.error?.let { item { ErrorCard(it) { onAction(ProfileAction.OpenPage(state.page)) } } }
        if (!state.detailLoading && state.activities.isEmpty() && state.learning.isEmpty() && state.error == null) item { Text("Belum ada data untuk ditampilkan.", color = Color(0xFF607D8B)) }
        items(visibleActivities, key = { it.id }) { item ->
            ActivityRow(item) {
                if (
                    item.communityId != null && item.status != "Ditarik" &&
                    state.page in listOf(ProfilePage.Verifications, ProfilePage.Publications)
                ) {
                    onCommunityPostSelected(item.communityId)
                } else {
                    onAction(ProfileAction.OpenActivity(item))
                }
            }
        }
        items(state.learning, key = { it.id }) { LearningRow(it) { onAction(ProfileAction.OpenLearning(it)) } }
        if (state.hasMore) item { OutlinedButton(onClick = { onAction(ProfileAction.LoadMore) }, enabled = !state.detailLoading, modifier = Modifier.fillMaxWidth()) { Text("Muat lebih banyak") } }
    }
}

@Composable private fun ActivityRow(item: ProfileActivityItem, onClick: () -> Unit) { Surface(Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = Color.White) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Row { Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(item.status) }; Text(item.subtitle, color = Color(0xFF607D8B), fontSize = 13.sp); Text(item.createdAt.take(10), color = Color(0xFF90A4AE), fontSize = 11.sp) } } }
@Composable private fun LearningRow(item: ProfileLearningItem, onClick: () -> Unit) { Surface(Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = Color.White) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); LinearProgressIndicator({ (item.progressPercent / 100).toFloat() }, Modifier.fillMaxWidth()); Text("${item.completedLessons}/${item.totalLessons} pelajaran • ${item.progressPercent.toInt()}%", fontSize = 12.sp, color = Color(0xFF607D8B)); Text("Skor terakhir ${item.latestScore?.toInt() ?: 0} • terbaik ${item.bestScore?.toInt() ?: 0}", fontSize = 12.sp, color = Color(0xFF607D8B)) } } }

@Composable private fun ItemDetailPage(state: ProfileUiState, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp)) { PageHeader("Detail", onBack); Spacer(Modifier.height(16.dp)); Surface(shape = RoundedCornerShape(18.dp), color = Color.White) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { state.selectedActivity?.let { Text(it.title, fontSize = 20.sp, fontWeight = FontWeight.Bold); StatusPill(it.status); Text(it.subtitle, color = Color(0xFF455A64)); Text("Dibuat ${it.createdAt}", color = Color(0xFF607D8B), fontSize = 13.sp); Text("ID ${it.id}", color = Color(0xFF90A4AE), fontSize = 11.sp) }; state.selectedLearning?.let { Text(it.title, fontSize = 20.sp, fontWeight = FontWeight.Bold); LinearProgressIndicator({ (it.progressPercent / 100).toFloat() }, Modifier.fillMaxWidth()); Text("${it.completedLessons} dari ${it.totalLessons} pelajaran selesai"); Text("Progres ${it.progressPercent.toInt()}%"); Text("Skor terakhir: ${it.latestScore?.toInt()?.toString() ?: "Belum ada"}"); Text("Skor terbaik: ${it.bestScore?.toInt()?.toString() ?: "Belum ada"}") } } } } }

@Composable private fun SettingsPage(onBack: () -> Unit, onPassword: () -> Unit, onLogout: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp)) { PageHeader("Pengaturan Akun", onBack); Spacer(Modifier.height(18.dp)); ListItem(headlineContent = { Text("Ubah kata sandi") }, leadingContent = { Icon(Icons.Rounded.Lock, null) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onPassword)); Spacer(Modifier.height(12.dp)); ListItem(headlineContent = { Text("Keluar", color = Color(0xFFD32F2F)) }, leadingContent = { Icon(Icons.Rounded.Logout, null, tint = Color(0xFFD32F2F)) }, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onLogout)) } }
@Composable private fun PageHeader(title: String, back: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }; Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun StatusPill(status: String) { Text(status, Modifier.background(Color(0xFFE7F3F8), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp), color = WaspadAIBlue, fontSize = 11.sp) }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFEBEE)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), color = Color(0xFFB71C1C)); TextButton(onClick = retry) { Text("Coba lagi") } } } }

@Composable private fun ProfileAvatar(profile: UserProfile, token: String, size: Int) { val initials = profile.displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }; Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFD9EEF6)), contentAlignment = Alignment.Center) { Text(initials, color = WaspadAIBlue, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp); profile.avatarUrl?.let { url -> AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build()).build(), contentDescription = "Foto profil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } } }

@Composable private fun PasswordDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) { var password by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Ubah kata sandi") }, text = { OutlinedTextField(password, { password = it }, label = { Text("Kata sandi baru") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.length >= 8) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }) }

private fun readAvatar(context: Context, uri: Uri): Pair<ByteArray, String>? = runCatching { val type = context.contentResolver.getType(uri) ?: "image/jpeg"; context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { it to type } }.getOrNull()
