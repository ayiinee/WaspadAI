package id.waspadai.app.feature.community.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.waspadai.app.R
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAICaution
import id.waspadai.app.ui.theme.WaspadAIContribution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAILightBlue
import id.waspadai.app.ui.theme.WaspadAIMuted
import id.waspadai.app.ui.theme.WaspadAITheme
import id.waspadai.app.ui.theme.WaspadAIValid

@Composable
fun CommunityRoute(
    repository: CommunityRepository,
    defaultBaseUrl: String,
    defaultAccessToken: String,
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit = {},
    viewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.Factory(
            repository = repository,
            defaultBaseUrl = defaultBaseUrl,
            defaultAccessToken = defaultAccessToken,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    CommunityScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        onSharePost = { post ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${post.body}\n\nDibagikan dari WaspadAI")
            }
            context.startActivity(Intent.createChooser(sendIntent, "Bagikan kasus"))
        },
        onDestinationSelected = { label ->
            if (label == "Periksa") {
                onDestinationSelected(label)
            } else if (label != "Koneksi") {
                Toast.makeText(
                    context,
                    "$label belum tersedia pada slicing ini",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
    )
}

@Composable
fun CommunityScreen(
    uiState: CommunityUiState,
    onAction: (CommunityAction) -> Unit,
    onBack: () -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Koneksi",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CommunityHeader(onBack = onBack, summary = uiState.summary)
            }
            item {
                BackendConnectionPanel(
                    uiState = uiState,
                    onAction = onAction,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }
            item {
                CommunitySearchBar(
                    query = uiState.searchQuery,
                    selectedFilter = uiState.selectedFilter,
                    isFilterMenuVisible = uiState.isFilterMenuVisible,
                    onAction = onAction,
                )
            }
            if (uiState.visiblePosts.isEmpty()) {
                item {
                    EmptyCommunityResult(modifier = Modifier.animateItem())
                }
            } else {
                items(
                    items = uiState.visiblePosts,
                    key = CommunityPost::id,
                ) { post ->
                    CommunityPostCard(
                        post = post,
                        onSupportClick = {
                            onAction(CommunityAction.SupportClicked(post.id))
                        },
                        onVerdictClick = { verdict ->
                            onAction(CommunityAction.VerdictSelected(post.id, verdict))
                        },
                        onShareClick = { onSharePost(post) },
                        modifier = Modifier
                            .padding(horizontal = 23.dp)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityHeader(
    onBack: () -> Unit,
    summary: CommunitySummary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.community_header_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
                    .border(1.5.dp, Color.White, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White,
                )
            }
            Text(
                text = "Koneksi",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        ContributionCard(
            summary = summary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 25.dp),
        )
    }
}

@Composable
private fun ContributionCard(
    summary: CommunitySummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(7.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = WaspadAIContribution,
    ) {
        Column(modifier = Modifier.padding(horizontal = 23.dp, vertical = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Kontribusi Saya",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Lihat kontribusi",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContributionMetric(
                    icon = Icons.Rounded.Groups,
                    value = summary.assessmentsCount.toString(),
                    label = "Penilaian",
                    modifier = Modifier.weight(1f),
                )
                ContributionDivider()
                ContributionMetric(
                    icon = Icons.Rounded.Article,
                    value = summary.evidenceAddedCount.toString(),
                    label = "Bukti Ditambahkan",
                    modifier = Modifier.weight(1.25f),
                )
                ContributionDivider()
                ContributionMetric(
                    icon = Icons.Rounded.Verified,
                    value = summary.resolvedCasesCount.toString(),
                    label = "Kasus Selesai",
                    modifier = Modifier.weight(1.15f),
                )
            }
        }
    }
}

@Composable
private fun ContributionMetric(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(33.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WaspadAIBlue,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                fontSize = 16.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Text(
                text = label,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = Color.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ContributionDivider() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(Color.Black.copy(alpha = 0.2f)),
    )
}

@Composable
private fun BackendConnectionPanel(
    uiState: CommunityUiState,
    onAction: (CommunityAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaspadAILightBlue),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backend Koneksi",
                        color = WaspadAIDarkBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiState.backendMessage,
                        color = when (uiState.backendPhase) {
                            CommunityBackendPhase.Connected -> WaspadAIValid
                            CommunityBackendPhase.Failure -> WaspadAIHoax
                            else -> WaspadAIMuted
                        },
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
                if (uiState.backendPhase == CommunityBackendPhase.Loading || uiState.isVoteSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = WaspadAIBlue,
                    )
                }
            }
            BackendInputField(
                label = "Base URL Product API",
                value = uiState.baseUrlDraft,
                onValueChange = { onAction(CommunityAction.BaseUrlChanged(it)) },
            )
            BackendInputField(
                label = "Bearer token Supabase",
                value = uiState.accessTokenDraft,
                onValueChange = { onAction(CommunityAction.AccessTokenChanged(it)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAction(CommunityAction.RefreshBackend) },
                    enabled = uiState.backendPhase != CommunityBackendPhase.Loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refresh feed", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAction(CommunityAction.BaseUrlChanged("http://10.0.2.2:8001")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Emulator local", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BackendInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = WaspadAIMuted,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.Black, fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, WaspadAILightBlue, RoundedCornerShape(4.dp)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = if (label.startsWith("Base")) "http://10.0.2.2:8001" else "Supabase access token",
                            color = Color.Black.copy(alpha = 0.22f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun CommunitySearchBar(
    query: String,
    selectedFilter: CommunityFeedFilter,
    isFilterMenuVisible: Boolean,
    onAction: (CommunityAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { onAction(CommunityAction.SearchChanged(it)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 14.sp,
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(1.dp, Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp)),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Cari kasus, materi, atau informasi...",
                                color = Color.Black.copy(alpha = 0.18f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cari",
                        tint = Color.Black,
                        modifier = Modifier.size(27.dp),
                    )
                }
            },
        )
        Box {
            IconButton(
                onClick = { onAction(CommunityAction.FilterClicked) },
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Filter: ${selectedFilter.label}"
                    },
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = WaspadAIBlue,
                    modifier = Modifier.size(37.dp),
                )
            }
            DropdownMenu(
                expanded = isFilterMenuVisible,
                onDismissRequest = { onAction(CommunityAction.FilterDismissed) },
            ) {
                CommunityFeedFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = filter.label,
                                fontWeight = if (filter == selectedFilter) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                        onClick = { onAction(CommunityAction.FilterSelected(filter)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    onSupportClick: () -> Unit,
    onVerdictClick: (CommunityVerdict) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaspadAIDarkBlue),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(post.avatarRes),
                    contentDescription = "Foto ${post.author}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        text = post.author,
                        color = WaspadAIDarkBlue,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = post.timestamp,
                        color = WaspadAIMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = post.title,
                    color = WaspadAIDarkBlue,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = post.statusLabel,
                    color = if (post.statusLabel.contains("terverifikasi", ignoreCase = true)) {
                        WaspadAIValid
                    } else {
                        WaspadAICaution
                    },
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = post.body,
                color = Color.Black,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(7.dp))
            Image(
                painter = painterResource(post.evidenceRes),
                contentDescription = "Bukti visual dari ${post.author}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 148.dp)
                    .width(148.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CommunityVerdict.entries.forEach { verdict ->
                    VerdictButton(
                        verdict = verdict,
                        count = when (verdict) {
                            CommunityVerdict.Hoaks -> post.hoaksCount
                            CommunityVerdict.Waspada -> post.waspadaCount
                            CommunityVerdict.Valid -> post.validCount
                        },
                        selected = post.selectedVerdict == verdict,
                        onClick = { onVerdictClick(verdict) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Agregat: Hoaks ${post.hoaksCount} - Waspada ${post.waspadaCount} - Valid ${post.validCount}",
                color = WaspadAIMuted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineAction(
                    icon = if (post.isSupported) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    label = if (post.isSupported) "Ditandai" else "Tandai",
                    contentDescription = "Tandai kasus",
                    tint = if (post.isSupported) WaspadAIHoax else Color.Black,
                    onClick = onSupportClick,
                )
                Spacer(Modifier.weight(1f))
                InlineAction(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    contentDescription = "Bagikan kasus",
                    onClick = onShareClick,
                )
            }
        }
    }
}

@Composable
private fun VerdictButton(
    verdict: CommunityVerdict,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when (verdict) {
        CommunityVerdict.Hoaks -> WaspadAIHoax
        CommunityVerdict.Waspada -> WaspadAICaution
        CommunityVerdict.Valid -> WaspadAIValid
    }
    Box(
        modifier = modifier
            .height(35.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .then(
                if (selected) Modifier.border(2.dp, Color.Black, RoundedCornerShape(3.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "✓ ${verdict.label}" else verdict.label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InlineAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun CommunityPost.countFor(verdict: CommunityVerdict): Int = when (verdict) {
    CommunityVerdict.Hoaks -> hoaksCount
    CommunityVerdict.Waspada -> waspadaCount
    CommunityVerdict.Valid -> validCount
}

@Composable
private fun EmptyCommunityResult(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = WaspadAIMuted,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Kasus tidak ditemukan",
            color = WaspadAIDarkBlue,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Coba kata kunci atau filter lain.",
            color = WaspadAIMuted,
            fontSize = 13.sp,
        )
    }
}

@Preview(showBackground = true, widthDp = 468, heightDp = 1024)
@Composable
private fun CommunityScreenPreview() {
    WaspadAITheme {
        CommunityScreen(
            uiState = CommunityUiState(),
            onAction = {},
            onBack = {},
            onSharePost = {},
            onDestinationSelected = {},
        )
    }
}
