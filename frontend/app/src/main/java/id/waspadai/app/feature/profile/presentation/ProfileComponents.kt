package id.waspadai.app.feature.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import id.waspadai.app.feature.profile.domain.ProfileActivityItem
import id.waspadai.app.feature.profile.domain.ProfileLearningItem
import id.waspadai.app.feature.profile.domain.UserProfile
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAILightBlue
import id.waspadai.app.ui.theme.WaspadAIMuted

private val ProfileBorder = Color(0xFFD8E4EC)
private val ProfileMenuIcon = Color(0xFF697177)
private val ProfileMenuText = Color(0xFF111111)
private val ProfileMenuSupporting = Color(0xFF6B6F72)
private val DangerBackground = Color(0xFFFFEBEE)
private val DangerText = Color(0xFFB71C1C)

@Composable
internal fun ProfileSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        color = WaspadAIDarkBlue,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun ProfileIdentityCard(
    profile: UserProfile,
    accessToken: String,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileAvatar(profile = profile, accessToken = accessToken, size = 72)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        color = WaspadAIDarkBlue,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    profile.email?.takeIf { it.isNotBlank() }?.let { email ->
                        Text(
                            text = email,
                            color = WaspadAIMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            profile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    text = bio,
                    color = Color(0xFF455A64),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("profile-edit"),
                border = BorderStroke(1.dp, WaspadAIBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WaspadAIBlue),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Edit profil", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ProfileNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag(testTag)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = ProfileMenuIcon, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ProfileMenuText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = ProfileMenuSupporting,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Buka $title",
                tint = ProfileMenuIcon,
                modifier = Modifier.size(24.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 44.dp),
                thickness = 1.dp,
                color = ProfileBorder,
            )
        }
    }
}

@Composable
internal fun ProfileAvatarEditor(
    profile: UserProfile,
    accessToken: String,
    enabled: Boolean,
    onPickAvatar: () -> Unit,
    onDeleteAvatar: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileAvatar(profile = profile, accessToken = accessToken, size = 96)
            TextButton(onClick = onPickAvatar, enabled = enabled) {
                Text("Ganti foto", color = WaspadAIBlue, fontWeight = FontWeight.Bold)
            }
            if (onDeleteAvatar != null) {
                TextButton(onClick = onDeleteAvatar, enabled = enabled) {
                    Text("Hapus foto", color = WaspadAIHoax)
                }
            }
        }
    }
}

@Composable
internal fun ProfileActivityCard(
    item: ProfileActivityItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    color = WaspadAIDarkBlue,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ProfileStatusPill(item.status)
            }
            Text(
                text = item.subtitle,
                color = Color(0xFF607D8B),
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.createdAt.take(10),
                color = WaspadAIMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun ProfileLearningCard(
    item: ProfileLearningItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    color = WaspadAIDarkBlue,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Buka ${item.title}",
                    tint = WaspadAIBlue,
                )
            }
            ProfileLearningProgress(item)
            Text(
                text = "${item.completedLessons}/${item.totalLessons} pelajaran • ${item.progressPercent.toInt()}%",
                color = WaspadAIMuted,
                fontSize = 12.sp,
            )
            Text(
                text = "Skor terakhir ${item.latestScore?.toInt() ?: 0} • terbaik ${item.bestScore?.toInt() ?: 0}",
                color = WaspadAIMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun ProfileLearningProgress(item: ProfileLearningItem) {
    val progress = (item.progressPercent / 100.0).toFloat().coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(WaspadAILightBlue, RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .background(WaspadAIBlue, RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
internal fun ProfileStatusPill(status: String) {
    val normalized = status.lowercase()
    val (background, content) = when {
        listOf("hoaks", "critical", "ditarik", "gagal").any(normalized::contains) ->
            Color(0xFFFFE7E2) to WaspadAIHoax
        listOf("waspada", "menunggu", "unverified", "pending").any(normalized::contains) ->
            Color(0xFFFFF2CC) to Color(0xFF8A6500)
        listOf("valid", "verified", "selesai", "published").any(normalized::contains) ->
            Color(0xFFE5F7F0) to Color(0xFF087A55)
        else -> Color(0xFFE7F3FC) to WaspadAIBlue
    }
    Text(
        text = status,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = content,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun ProfileDetailSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun ProfileSettingsGroup(
    onQuickAccess: () -> Unit,
    onPassword: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ProfileSettingsRow(
            title = "Akses Cepat",
            subtitle = "Kelola Assistant, Quick Settings, dan Tanyain",
            icon = Icons.Rounded.TouchApp,
            contentColor = ProfileMenuIcon,
            showChevron = true,
            onClick = onQuickAccess,
        )
        ProfileSettingsRow(
            title = "Ubah kata sandi",
            subtitle = "Perbarui keamanan akun",
            icon = Icons.Rounded.Lock,
            contentColor = ProfileMenuIcon,
            showChevron = true,
            onClick = onPassword,
        )
        ProfileSettingsRow(
            title = "Keluar",
            subtitle = "Akhiri sesi pada perangkat ini",
            icon = Icons.AutoMirrored.Rounded.Logout,
            contentColor = ProfileMenuIcon,
            showChevron = false,
            onClick = onLogout,
        )
    }
}

@Composable
private fun ProfileSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    contentColor: Color,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ProfileMenuText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = ProfileMenuSupporting,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Buka $title",
                    tint = ProfileMenuIcon,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            thickness = 1.dp,
            color = ProfileBorder,
        )
    }
}

@Composable
internal fun ProfileLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile-loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = WaspadAIBlue)
    }
}

@Composable
internal fun ProfileInlineLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = WaspadAIBlue, modifier = Modifier.size(28.dp))
    }
}

@Composable
internal fun ProfileErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-error"),
        shape = RoundedCornerShape(14.dp),
        color = DangerBackground,
        border = BorderStroke(1.dp, WaspadAIHoax.copy(alpha = .25f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = WaspadAIHoax)
            Text("Data belum dapat dimuat", color = DangerText, fontWeight = FontWeight.Bold)
            Text(message, color = DangerText, fontSize = 13.sp)
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Coba lagi", color = WaspadAIHoax, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ProfileEmptyState(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp)
            .testTag("profile-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = WaspadAIMuted,
            modifier = Modifier.size(40.dp),
        )
        Text(title, color = WaspadAIDarkBlue, fontWeight = FontWeight.Bold)
        Text(description, color = WaspadAIMuted, fontSize = 13.sp)
    }
}

@Composable
internal fun ProfileAvatar(
    profile: UserProfile,
    accessToken: String,
    size: Int,
) {
    val initials = profile.displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFD9EEF6)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = WaspadAIBlue,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 3).sp,
        )
        profile.avatarUrl?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Authorization", "Bearer $accessToken")
                            .build(),
                    )
                    .build(),
                contentDescription = "Foto profil",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun ProfilePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah kata sandi") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Kata sandi baru") },
                supportingText = { Text("Minimal 8 karakter") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.length >= 8,
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
    )
}
