package id.waspadai.app.feature.community.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPost = uiState.posts.firstOrNull { it.id == selectedPostId }

    if (selectedPost != null) {
        CommunityDetailScreen(
            post = selectedPost,
            onBack = { selectedPostId = null },
            onSupportClick = { viewModel.onAction(CommunityAction.SupportClicked(selectedPost.id)) },
            onVerdictClick = { verdict -> viewModel.onAction(CommunityAction.VerdictSelected(selectedPost.id, verdict)) },
            onDestinationSelected = onDestinationSelected,
        )
        return
    }

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
        onOpenPost = { selectedPostId = it.id },
        onDestinationSelected = onDestinationSelected,
    )
}

@Composable
fun CommunityScreen(
    uiState: CommunityUiState,
    onAction: (CommunityAction) -> Unit,
    onBack: () -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearchVisible by rememberSaveable {
        mutableStateOf(uiState.searchQuery.isNotBlank())
    }
    val closeSearch: () -> Unit = {
        isSearchVisible = false
        onAction(CommunityAction.SearchChanged(""))
        onAction(CommunityAction.FilterSelected(CommunityFeedFilter.Semua))
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Header tetap terlihat saat daftar koneksi digulir.
            CommunityPageHeader(
                title = "Koneksi",
                onBack = onBack,
                isSearchVisible = isSearchVisible,
                onSearchClick = {
                    if (isSearchVisible) closeSearch() else isSearchVisible = true
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    AnimatedVisibility(
                        visible = isSearchVisible,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        Column {
                            CommunitySearchBar(
                                query = uiState.searchQuery,
                                selectedFilter = uiState.selectedFilter,
                                isFilterMenuVisible = uiState.isFilterMenuVisible,
                                onAction = onAction,
                                requestFocus = isSearchVisible,
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }
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
                            onOpenDetails = { onOpenPost(post) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunityPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isSearchVisible: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WaspadAIBlue),
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
                .height(64.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Text(
                text = title,
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            if (onSearchClick != null) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (isSearchVisible) "Tutup pencarian" else "Buka pencarian",
                        tint = if (isSearchVisible) WaspadAICaution else Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
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
    requestFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

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
                .focusRequester(focusRequester)
                .border(1.5.dp, WaspadAILightBlue, RoundedCornerShape(28.dp)),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Cari kasus, materi, atau informasi...",
                                color = Color.Black.copy(alpha = 0.35f),
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
                        tint = WaspadAIBlue,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(WaspadAIBlue)
                    .clickable { onAction(CommunityAction.FilterClicked) }
                    .semantics {
                        contentDescription = "Filter: ${selectedFilter.label}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            DropdownMenu(
                expanded = isFilterMenuVisible,
                onDismissRequest = { onAction(CommunityAction.FilterDismissed) },
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .border(1.dp, WaspadAILightBlue, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                shadowElevation = 10.dp,
            ) {
                Text(
                    text = "Tampilkan koneksi",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaspadAIBlue)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                CommunityFeedFilter.entries.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = filter.label,
                                color = if (isSelected) WaspadAIDarkBlue else WaspadAIMuted,
                                fontWeight = if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = WaspadAIBlue,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        },
                        onClick = { onAction(CommunityAction.FilterSelected(filter)) },
                        modifier = Modifier.background(
                            if (isSelected) WaspadAIBlue.copy(alpha = .09f) else Color.White,
                        ),
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
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
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
                Column(modifier = Modifier.weight(1f)) {
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
                    Text(
                        text = post.statusLabel,
                        color = post.statusTextColor(),
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Bagikan kasus",
                        tint = WaspadAIDarkBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = post.title,
                    color = WaspadAIDarkBlue,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
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
            CommunityEvidenceImage(
                evidenceRes = post.evidenceRes,
                author = post.author,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(WaspadAIBlue)
                    .clickable(onClick = onOpenDetails),
                contentAlignment = Alignment.Center,
            ) {
                Text("Beri penilaian", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Agregat: Hoaks ${post.hoaksCount} - Waspada ${post.waspadaCount} - Valid ${post.validCount}",
                color = WaspadAIMuted,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InlineAction(
                    icon = if (post.isSupported) {
                        Icons.Rounded.Favorite
                    } else {
                        Icons.Rounded.FavoriteBorder
                    },
                    label = post.totalVoteCount.toString(),
                    contentDescription = "Total penilaian komunitas",
                    tint = if (post.isSupported) WaspadAIHoax else WaspadAIDarkBlue,
                    containerColor = if (post.isSupported) {
                        WaspadAIHoax.copy(alpha = 0.14f)
                    } else {
                        WaspadAILightBlue.copy(alpha = 0.5f)
                    },
                    onClick = onSupportClick,
                )
                InlineAction(
                    icon = Icons.Rounded.Visibility,
                    label = post.viewCount.toString(),
                    contentDescription = "Dilihat ${post.viewCount} kali",
                    tint = WaspadAIBlue,
                    containerColor = Color(0xFFE7F3FC),
                    onClick = {},
                )
                InlineAction(
                    icon = Icons.Rounded.ChatBubble,
                    label = post.commentCount.toString(),
                    contentDescription = "Komentar",
                    tint = WaspadAIBlue,
                    containerColor = Color(0xFFE7F3FC),
                    onClick = {},
                )
            }
        }
        HorizontalDivider(color = WaspadAILightBlue, thickness = 1.dp)
    }
}

@Composable
fun CommunityAssessmentPanel(
    post: CommunityPost,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSubmit: (CommunityVerdict, String) -> Unit,
) {
    val context = LocalContext.current
    var reason by rememberSaveable(post.id) { mutableStateOf("") }
    var evidenceKeys by rememberSaveable(post.id) { mutableStateOf(emptyList<String>()) }
    var evidenceNames by rememberSaveable(post.id) { mutableStateOf(emptyList<String>()) }
    var evidenceMimeTypes by rememberSaveable(post.id) { mutableStateOf(emptyList<String>()) }
    var evidenceMessage by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var previewIndex by rememberSaveable(post.id) { mutableStateOf<Int?>(null) }
    var selectedVerdict by rememberSaveable(post.id) { mutableStateOf(post.selectedVerdict) }
    var submissionState by remember(post.id) { mutableStateOf(AssessmentSubmissionState.Editing) }
    val scope = rememberCoroutineScope()
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val attachment = resolveEvidenceAttachment(context.contentResolver, uri, evidenceNames.size + 1)
        val fileName = attachment.displayName
        val duplicate = evidenceKeys.contains(uri.toString()) ||
            evidenceNames.any { it.equals(fileName, ignoreCase = true) }
        if (duplicate) {
            evidenceMessage = "Dokumen telah dilampirkan. Pilih dokumen lain."
        } else {
            evidenceKeys = evidenceKeys + uri.toString()
            evidenceNames = evidenceNames + fileName
            evidenceMimeTypes = evidenceMimeTypes + attachment.mimeType
            evidenceMessage = "$fileName berhasil dilampirkan."
        }
    }
    val isFormValid = selectedVerdict != null && reason.trim().length >= 10 && evidenceNames.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(WaspadAIBlue)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = post.selectedVerdict?.let { "Penilaian: ${it.label}" } ?: "Beri penilaian",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    if (!expanded) return

    Dialog(
        onDismissRequest = {
            if (submissionState == AssessmentSubmissionState.Editing) onToggle()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header biru full-width
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .background(WaspadAIBlue)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Beri penilaian",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (submissionState == AssessmentSubmissionState.Editing) {
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(32.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tutup formulir", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (submissionState == AssessmentSubmissionState.Success) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE8F7F1))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = WaspadAIValid,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Penilaian berhasil disimpan", fontWeight = FontWeight.Bold, color = WaspadAIDarkBlue)
                        Text("Hasil polling dan tanggapan sedang diperbarui.", fontSize = 12.sp, color = WaspadAIMuted)
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Pilih kategori", color = Color(0xFF15212A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            CommunityVerdict.entries.forEach { verdict ->
                                VerdictButton(
                                    verdict = verdict,
                                    selected = selectedVerdict == verdict,
                                    onClick = { selectedVerdict = verdict },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text("Alasan penilaian", color = Color(0xFF15212A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            enabled = submissionState == AssessmentSubmissionState.Editing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, WaspadAILightBlue, RoundedCornerShape(8.dp)),
                            placeholder = {
                                Text(
                                    "Jelaskan sumber, konteks, atau alasan penilaian Anda.",
                                    fontSize = 12.sp,
                                )
                            },
                            minLines = 3,
                            maxLines = 4,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Lampirkan bukti",
                                color = Color(0xFF15212A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Tambah bukti",
                                tint = WaspadAIBlue,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = submissionState == AssessmentSubmissionState.Editing) {
                                        evidencePicker.launch(arrayOf("*/*"))
                                    },
                            )
                        }
                        if (evidenceNames.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, WaspadAILightBlue, RoundedCornerShape(8.dp))
                                    .clickable(enabled = submissionState == AssessmentSubmissionState.Editing) {
                                        evidencePicker.launch(arrayOf("*/*"))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "+ Tambahkan bukti",
                                    color = WaspadAIBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                evidenceNames.forEachIndexed { index, fileName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(WaspadAIValid)
                                            .clickable { previewIndex = index }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Bukti terlampir",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = fileName,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (evidenceKind(evidenceMimeTypes[index], fileName) == EvidenceKind.Pdf) "Lihat PDF" else "Preview",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                        evidenceMessage?.let { message ->
                            Text(
                                text = message,
                                color = if (message.startsWith("Dokumen telah")) WaspadAIHoax else WaspadAIValid,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = {
                                val verdict = selectedVerdict ?: return@Button
                                val submittedReason = reason.trim()
                                scope.launch {
                                    submissionState = AssessmentSubmissionState.Saving
                                    delay(900)
                                    submissionState = AssessmentSubmissionState.Success
                                    delay(750)
                                    onSubmit(verdict, submittedReason)
                                    reason = ""
                                    evidenceKeys = emptyList()
                                    evidenceNames = emptyList()
                                    evidenceMimeTypes = emptyList()
                                    evidenceMessage = null
                                    submissionState = AssessmentSubmissionState.Editing
                                }
                            },
                            enabled = isFormValid && submissionState == AssessmentSubmissionState.Editing,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                        ) {
                            if (submissionState == AssessmentSubmissionState.Saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Menyimpan...")
                            } else {
                                Text("Kirim penilaian", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    previewIndex?.let { index ->
        if (index in evidenceKeys.indices && index in evidenceMimeTypes.indices) {
            EvidencePreviewDialog(
                attachment = CommunityEvidenceAttachment(
                    uri = Uri.parse(evidenceKeys[index]),
                    displayName = evidenceNames[index],
                    mimeType = evidenceMimeTypes[index],
                ),
                onDismiss = { previewIndex = null },
                onOpenFailed = {
                    evidenceMessage = "Tidak ada aplikasi yang dapat membuka format file ini."
                },
            )
        } else {
            previewIndex = null
        }
    }
}

private enum class AssessmentSubmissionState { Editing, Saving, Success }

@Composable
private fun VerdictButton(
    verdict: CommunityVerdict,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = when (verdict) {
        CommunityVerdict.Hoaks -> WaspadAIHoax
        CommunityVerdict.Waspada -> WaspadAICaution
        CommunityVerdict.Valid -> WaspadAIValid
    }
    val activeColor = WaspadAIContribution
    val activeBorderColor = Color(0xFFE0A800)
    val background = Color.White
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) activeBorderColor else WaspadAILightBlue,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = activeBorderColor,
                unselectedColor = WaspadAIMuted,
            ),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = verdict.label,
            color = if (selected) Color(0xFF8A6500) else WaspadAIDarkBlue,
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
    containerColor: Color = Color.Transparent,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = Color.Black,
            fontSize = 13.sp,
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
            onOpenPost = {},
            onDestinationSelected = {},
        )
    }
}
