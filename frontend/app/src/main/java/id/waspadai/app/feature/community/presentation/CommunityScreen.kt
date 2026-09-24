package id.waspadai.app.feature.community.presentation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import id.waspadai.app.R
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.core.ui.WaspadAIPageHeader
import id.waspadai.app.feature.community.domain.CommunityRepository
import id.waspadai.app.feature.community.domain.CommunityResponseItem
import id.waspadai.app.feature.verification.data.StaticAccessTokenProvider
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CommunityRoute(
    repository: CommunityRepository,
    defaultBaseUrl: String,
    defaultAccessToken: String,
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit = {},
    initialPostId: String? = null,
    viewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.Factory(
            repository = repository,
            accessTokenProvider = StaticAccessTokenProvider(defaultAccessToken),
            communityBaseUrl = defaultBaseUrl,
            defaultBaseUrl = defaultBaseUrl,
            defaultAccessToken = defaultAccessToken,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedPostId by rememberSaveable { mutableStateOf(initialPostId) }
    val selectedPost = uiState.posts.firstOrNull { it.id == selectedPostId }
    val timelineState = rememberCommunityTimelineState(uiState.selectedFeedScope)

    // Trigger auto-refresh saat layar pertama kali ditampilkan
    LaunchedEffect(Unit) {
        viewModel.onAction(CommunityAction.InitScreen)
    }
    LaunchedEffect(selectedPostId, selectedPost?.id) {
        selectedPostId?.let { postId ->
            viewModel.onAction(CommunityAction.LoadPostDetail(postId))
        }
    }
    LaunchedEffect(uiState.requestedPostId) {
        uiState.requestedPostId?.let { postId ->
            selectedPostId = postId
            viewModel.onAction(CommunityAction.PublishedPostOpened)
        }
    }
    LaunchedEffect(uiState.shareLink) {
        val sharePath = uiState.shareLink ?: return@LaunchedEffect
        val sharedPost = uiState.posts.firstOrNull { it.id == selectedPostId }
            ?: uiState.posts.firstOrNull()
        val shareUrl = if (sharePath.startsWith("http://") || sharePath.startsWith("https://")) {
            sharePath
        } else {
            "${defaultBaseUrl.trimEnd('/')}$sharePath"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${sharedPost?.body.orEmpty()}\n\n$shareUrl")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan kasus"))
        viewModel.onAction(CommunityAction.ShareLinkConsumed)
    }

    BackHandler(enabled = selectedPost != null) { selectedPostId = null }

    AnimatedContent(
        targetState = selectedPost?.id,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(tween(250)) { width -> width } togetherWith
                    slideOutHorizontally(tween(180)) { width -> -width / 4 }
            } else {
                slideInHorizontally(tween(220)) { width -> -width / 4 } togetherWith
                    slideOutHorizontally(tween(220)) { width -> width }
            }
        },
        label = "communityTimelineDetail",
    ) { visiblePostId ->
        val visiblePost = uiState.posts.firstOrNull { it.id == visiblePostId }
        if (visiblePost != null) {
            CommunityDetailScreen(
                post = visiblePost,
                accessToken = uiState.accessTokenDraft,
                onBack = { selectedPostId = null },
                onSupportClick = {
                    viewModel.onAction(CommunityAction.SupportClicked(visiblePost.id))
                },
                onShareClick = {
                    viewModel.onAction(CommunityAction.ShareClicked(visiblePost.id))
                },
                onVerdictClick = { verdict ->
                    viewModel.onAction(CommunityAction.VerdictSelected(visiblePost.id, verdict))
                },
                detail = uiState.detailByPostId[visiblePost.id],
                imageBaseUrl = defaultBaseUrl,
                isDetailLoading = uiState.detailLoadingPostId == visiblePost.id,
                isResponseSubmitting = uiState.responseSubmittingPostId == visiblePost.id,
                detailError = uiState.detailError,
                onRetryDetail = {
                    viewModel.onAction(CommunityAction.LoadPostDetail(visiblePost.id))
                },
                onSubmitResponse = { verdict, reasoning, bytes, fileName, contentType ->
                    viewModel.onAction(
                        CommunityAction.SubmitCommunityResponse(
                            postId = visiblePost.id,
                            verdict = verdict,
                            reasoning = reasoning,
                            evidenceBytes = bytes,
                            evidenceFileName = fileName,
                            evidenceContentType = contentType,
                        )
                    )
                },
                onDestinationSelected = onDestinationSelected,
            )
        } else {
            CommunityScreen(
                uiState = uiState,
                timelineState = timelineState,
                onAction = viewModel::onAction,
                onBack = onBack,
                onSharePost = { post ->
                    viewModel.onAction(CommunityAction.ShareClicked(post.id))
                },
                onOpenPost = { selectedPostId = it.id },
                onDestinationSelected = onDestinationSelected,
            )
        }
    }
}

@Composable
fun CommunityScreen(
    uiState: CommunityUiState,
    timelineState: CommunityTimelineState,
    onAction: (CommunityAction) -> Unit,
    onBack: () -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletingPost by remember { mutableStateOf<CommunityPost?>(null) }
    var isSearchVisible by rememberSaveable {
        mutableStateOf(uiState.searchQuery.isNotBlank())
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val scopes = CommunityFeedScope.entries
    val closeSearch: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        isSearchVisible = false
        onAction(CommunityAction.SearchChanged(""))
        onAction(CommunityAction.FilterSelected(CommunityFeedFilter.BelumDinilai))
    }

    LaunchedEffect(uiState.selectedFeedScope) {
        val targetPage = scopes.indexOf(uiState.selectedFeedScope)
        if (targetPage >= 0 && timelineState.pagerState.settledPage != targetPage) {
            timelineState.pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(timelineState.pagerState) {
        snapshotFlow { timelineState.pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                scopes.getOrNull(page)?.let { scope ->
                    onAction(CommunityAction.FeedScopeSelected(scope))
                }
            }
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
                showBack = false,
                isSearchVisible = isSearchVisible,
                onSearchClick = {
                    if (isSearchVisible) closeSearch() else isSearchVisible = true
                },
            )
            CommunityFeedTabs(
                pagerState = timelineState.pagerState,
                onScopeSelected = { scope ->
                    coroutineScope.launch {
                        timelineState.pagerState.animateScrollToPage(scopes.indexOf(scope))
                    }
                },
            )
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(150),
                ) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(120),
                ) + fadeOut(animationSpec = tween(90)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WaspadAIBackground)
                        .padding(top = 14.dp, bottom = 8.dp),
                ) {
                    CommunitySearchBar(
                        query = uiState.searchQuery,
                        selectedFilter = uiState.selectedFilter,
                        isFilterMenuVisible = uiState.isFilterMenuVisible,
                        onAction = onAction,
                        requestFocus = isSearchVisible,
                    )
                }
            }
            HorizontalPager(
                state = timelineState.pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("community-feed-pager"),
                key = { page -> scopes[page].name },
            ) { page ->
                val scope = scopes[page]
                CommunityFeedPage(
                    scope = scope,
                    posts = uiState.visiblePosts(scope),
                    listState = timelineState.listState(scope),
                    accessToken = uiState.accessTokenDraft,
                    isRefreshing = uiState.backendPhase == CommunityBackendPhase.Loading,
                    onRefresh = { onAction(CommunityAction.RefreshBackend) },
                    onSupportPost = { post ->
                        onAction(CommunityAction.SupportClicked(post.id))
                    },
                    onVerdictPost = { post, verdict ->
                        onAction(CommunityAction.VerdictSelected(post.id, verdict))
                    },
                    onSharePost = onSharePost,
                    onOpenPost = onOpenPost,
                    onDeletePost = { deletingPost = it },
                )
            }
        }
    }

    deletingPost?.let { post ->
        AlertDialog(
            onDismissRequest = { if (uiState.managingPostId == null) deletingPost = null },
            title = { Text("Hapus postingan?") },
            text = { Text("Postingan akan dihapus dari Koneksi. Konfirmasi untuk melanjutkan.") },
            confirmButton = {
                TextButton(
                    enabled = uiState.managingPostId == null,
                    onClick = {
                        onAction(CommunityAction.DeletePost(post.id))
                        deletingPost = null
                    },
                ) { Text("Delete", color = WaspadAIHoax) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPost = null }) { Text("Batal") }
            },
        )
    }
    uiState.postManagementError?.let { message ->
        AlertDialog(
            onDismissRequest = { onAction(CommunityAction.PostManagementErrorDismissed) },
            title = { Text("Postingan belum dapat diubah") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onAction(CommunityAction.PostManagementErrorDismissed) }) {
                    Text("Tutup")
                }
            },
        )
    }
}

@Composable
internal fun CommunityPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    isSearchVisible: Boolean = false,
    onSearchClick: (() -> Unit)? = null,
) {
    WaspadAIPageHeader(
        title = title,
        modifier = modifier,
        onBack = onBack.takeIf { showBack },
    ) {
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (isSearchVisible) "Tutup pencarian" else "Buka pencarian",
                    tint = Color.White,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
private fun CommunityFeedTabs(
    pagerState: PagerState,
    onScopeSelected: (CommunityFeedScope) -> Unit,
) {
    val scopes = CommunityFeedScope.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            scopes.forEachIndexed { page, scope ->
                val selected = page == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onScopeSelected(scope) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = scope.label,
                        color = if (selected) WaspadAIBlue else Color.Black.copy(alpha = .48f),
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
        ) {
            val indicatorWidth = maxWidth / scopes.size
            val indicatorOffset = indicatorWidth *
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .height(2.dp)
                    .background(WaspadAIBlue),
            )
        }
    }
}

@Composable
private fun CommunityFeedPage(
    scope: CommunityFeedScope,
    posts: List<CommunityPost>,
    listState: LazyListState,
    accessToken: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSupportPost: (CommunityPost) -> Unit,
    onVerdictPost: (CommunityPost, CommunityVerdict) -> Unit,
    onSharePost: (CommunityPost) -> Unit,
    onOpenPost: (CommunityPost) -> Unit,
    onDeletePost: (CommunityPost) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = WaspadAIBlue,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("community-feed-${scope.name}"),
            contentPadding = PaddingValues(top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (posts.isEmpty()) {
                item { EmptyCommunityResult() }
            } else {
                items(items = posts, key = CommunityPost::id) { post ->
                    CommunityPostCard(
                        post = post,
                        accessToken = accessToken,
                        onSupportClick = { onSupportPost(post) },
                        onVerdictClick = { verdict -> onVerdictPost(post, verdict) },
                        onShareClick = { onSharePost(post) },
                        onOpenDetails = { onOpenPost(post) },
                        onDelete = { onDeletePost(post) },
                        modifier = Modifier.animateItem(),
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
                tint = Color.Black,
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
private fun CommunitySearchBar(
    query: String,
    selectedFilter: CommunityFeedFilter,
    isFilterMenuVisible: Boolean,
    onAction: (CommunityAction) -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterPopupOffset = with(LocalDensity.current) { IntOffset(0, 52.dp.roundToPx()) }
    val horizontalPadding = if (LocalConfiguration.current.screenWidthDp < 360) 16.dp else 22.dp
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            // Let the small expand animation finish before the IME starts resizing the screen.
            delay(150)
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
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
                .border(1.5.dp, Color.Black.copy(alpha = .18f), RoundedCornerShape(28.dp)),
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
                        tint = Color.Black,
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
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onAction(CommunityAction.FilterClicked)
                    }
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
            if (isFilterMenuVisible) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = filterPopupOffset,
                    onDismissRequest = { onAction(CommunityAction.FilterDismissed) },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        modifier = Modifier
                            .width(152.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 8.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .border(1.dp, WaspadAILightBlue, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                        ) {
                val visibleFilters = CommunityFeedFilter.entries.filterNot { it == CommunityFeedFilter.Semua }
                visibleFilters.forEachIndexed { index, filter ->
                    val isSelected = filter == selectedFilter
                    val itemShape = when (index) {
                        0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        visibleFilters.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(itemShape)
                            .background(if (isSelected) WaspadAIBlue else Color.White)
                            .clickable(
                                interactionSource = remember(filter) { MutableInteractionSource() },
                                indication = null,
                                onClick = { onAction(CommunityAction.FilterSelected(filter)) },
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                            Text(
                                text = filter.label,
                                color = if (isSelected) Color.White else Color.Black.copy(alpha = .6f),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                    }
                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    accessToken: String,
    onSupportClick: () -> Unit,
    onVerdictClick: (CommunityVerdict) -> Unit,
    onShareClick: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = when {
        screenWidth < 360 -> 14.dp
        screenWidth < 600 -> 20.dp
        else -> 32.dp
    }
    val likeAnimation = rememberCommunityLikeAnimation(post.id, post.isSupported)
    val cardInteractionSource = remember(post.id) { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    var isOpening by remember(post.id) { mutableStateOf(false) }
    val cardBackground by animateColorAsState(
        targetValue = if (isPressed || isOpening) {
            Color.Black.copy(alpha = .05f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = if (isPressed || isOpening) 70 else 100),
        label = "communityCardPressed",
    )
    val coroutineScope = rememberCoroutineScope()
    val openDetailsWithFeedback = {
        if (!isOpening) {
            isOpening = true
            coroutineScope.launch {
                delay(90)
                onOpenDetails()
                isOpening = false
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardBackground)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                enabled = !isOpening,
                onClick = openDetailsWithFeedback,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp)) {
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
                        color = Color.Black,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = post.timestamp,
                        color = Color.Black.copy(alpha = .4f),
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
                if (post.isOwner) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Hapus postingan",
                            tint = WaspadAIHoax,
                        )
                    }
                }
            }
            if (post.body.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = post.body,
                    color = Color.Black,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
            if (post.media.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                CommunityMediaCarousel(
                    media = post.media,
                    accessToken = accessToken,
                    author = post.author,
                )
            } else post.imageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                Spacer(Modifier.height(7.dp))
                CommunityEvidenceImage(imageUrl, accessToken, post.author)
            }
            if (!post.isOwner) {
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
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Agregat: Hoaks ${post.hoaksCount} - Waspada ${post.waspadaCount} - Valid ${post.validCount}",
                color = Color.Black.copy(alpha = .4f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            CommunitySocialActions(
                post = post,
                likeAnimation = likeAnimation,
                onLikeClick = onSupportClick,
                onCommentClick = onOpenDetails,
                onSeenClick = onOpenDetails,
                onShareClick = onShareClick,
            )
        }
        HorizontalDivider(color = Color.Black.copy(alpha = .16f), thickness = 1.dp)
    }
}

@Composable
fun CommunityAssessmentPanel(
    post: CommunityPost,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSubmit: (CommunityVerdict, String, ByteArray?, String?, String?) -> Unit,
    isSubmitting: Boolean = false,
    submitError: String? = null,
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
    val imageIndex = evidenceMimeTypes.indexOfFirst { it.startsWith("image/") }
    val isFormValid = selectedVerdict != null && reason.trim().length >= 10 && imageIndex >= 0

    LaunchedEffect(isSubmitting, submitError) {
        submissionState = when {
            isSubmitting -> AssessmentSubmissionState.Saving
            submitError != null -> AssessmentSubmissionState.Editing
            submissionState == AssessmentSubmissionState.Saving -> {
                reason = ""
                evidenceKeys = emptyList()
                evidenceNames = emptyList()
                evidenceMimeTypes = emptyList()
                evidenceMessage = null
                AssessmentSubmissionState.Success
            }
            else -> submissionState
        }
    }

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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                        evidencePicker.launch(arrayOf("image/*"))
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
                                    evidencePicker.launch(arrayOf("image/*"))
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
                        submitError?.let { error ->
                            Text(text = error, color = WaspadAIHoax, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val verdict = selectedVerdict ?: return@Button
                                val submittedReason = reason.trim()
                                if (imageIndex < 0) {
                                    evidenceMessage = "Lampirkan gambar bukti terlebih dahulu."
                                    return@Button
                                }
                                val imageUri = Uri.parse(evidenceKeys[imageIndex])
                                val imageBytes = runCatching {
                                    context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                                }.getOrNull()
                                if (imageBytes == null || imageBytes.isEmpty()) {
                                    evidenceMessage = "Gambar bukti tidak dapat dibaca."
                                    return@Button
                                }
                                scope.launch {
                                    submissionState = AssessmentSubmissionState.Saving
                                    onSubmit(
                                        verdict,
                                        submittedReason,
                                        imageBytes,
                                        evidenceNames[imageIndex],
                                        evidenceMimeTypes[imageIndex],
                                    )
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
internal fun CommunitySocialActions(
    post: CommunityPost,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSeenClick: (() -> Unit)? = null,
    likeAnimation: CommunityLikeAnimation = rememberCommunityLikeAnimation(post.id, post.isSupported),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InlineAction(
            icon = if (post.isSupported) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = post.likeCount.toString(),
            contentDescription = if (post.isSupported) "Batalkan suka" else "Sukai postingan",
            tint = if (post.isSupported) CommunityLikePink else CommunityActionGray,
            iconModifier = Modifier.communityLikeEffect(likeAnimation),
            onClick = onLikeClick,
        )
        InlineAction(
            icon = null,
            label = post.commentCount.toString(),
            contentDescription = "Lihat tanggapan",
            tint = CommunityActionGray,
            labelTint = CommunityActionGray,
            iconContent = {
                RoundCommentIcon(
                    tint = CommunityActionGray,
                    contentDescription = "Lihat tanggapan",
                    filled = false,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onCommentClick,
        )
        InlineAction(
            icon = Icons.Rounded.Visibility,
            label = post.viewCount.toString(),
            contentDescription = "Dilihat ${post.viewCount} kali",
            tint = CommunityActionGray,
            enabled = onSeenClick != null,
            onClick = onSeenClick ?: {},
        )
        Spacer(Modifier.weight(1f))
        val shareInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    interactionSource = shareInteractionSource,
                    indication = null,
                    onClick = onShareClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Bagikan kasus",
                tint = CommunityActionGray,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun InlineAction(
    icon: ImageVector?,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CommunityActionGray,
    labelTint: Color = CommunityActionGray,
    iconModifier: Modifier = Modifier,
    iconContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconContent != null) {
            iconContent()
        } else {
            Icon(
                imageVector = requireNotNull(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = iconModifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = labelTint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal val CommunityActionGray = Color.Black.copy(alpha = .52f)
internal val CommunityLikePink = Color(0xFFF21D4B)

@Composable
internal fun RoundCommentIcon(
    tint: Color,
    contentDescription: String,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        val bubble = Path().apply {
            moveTo(size.width * .50f, size.height * .08f)
            cubicTo(
                size.width * .25f, size.height * .08f,
                size.width * .08f, size.height * .24f,
                size.width * .08f, size.height * .48f,
            )
            cubicTo(
                size.width * .08f, size.height * .63f,
                size.width * .15f, size.height * .75f,
                size.width * .28f, size.height * .82f,
            )
            lineTo(size.width * .20f, size.height * .95f)
            lineTo(size.width * .42f, size.height * .87f)
            cubicTo(
                size.width * .70f, size.height * .91f,
                size.width * .92f, size.height * .74f,
                size.width * .92f, size.height * .48f,
            )
            cubicTo(
                size.width * .92f, size.height * .24f,
                size.width * .75f, size.height * .08f,
                size.width * .50f, size.height * .08f,
            )
            close()
        }
        if (filled) {
            drawPath(path = bubble, color = tint)
        } else {
            drawPath(
                path = bubble,
                color = tint,
                style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round),
            )
        }
        listOf(.36f, .50f, .64f).forEach { x ->
            drawCircle(
                color = if (filled) Color.White else tint,
                radius = 1.15.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * .48f),
            )
        }
    }
}

@Composable
private fun InlineCommunityResponses(
    responses: List<CommunityResponseItem>?,
    isLoading: Boolean,
    avatarRes: Int,
    caseId: String,
    imageBaseUrl: String,
    accessToken: String,
    contentModifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black.copy(alpha = .10f),
        )
        Column(modifier = contentModifier) {
            Text(
                text = "Tanggapan komunitas",
                color = Color.Black.copy(alpha = .72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            when {
                isLoading -> Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = WaspadAIBlue,
                    )
                    Text("Memuat tanggapan...", color = CommunityActionGray, fontSize = 12.sp)
                }
                responses.isNullOrEmpty() -> Text(
                    text = "Belum ada tanggapan komunitas.",
                    color = CommunityActionGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                else -> responses.forEachIndexed { index, response ->
                    Row(modifier = Modifier.padding(vertical = 10.dp)) {
                        Image(
                            painter = painterResource(avatarRes),
                            contentDescription = "Foto ${response.author}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    response.author,
                                    color = Color.Black.copy(alpha = .82f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(response.createdAt, color = Color.Black.copy(alpha = .38f), fontSize = 10.sp)
                            }
                            Text(
                                response.reasoning,
                                color = Color.Black.copy(alpha = .72f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Text(
                                text = when (response.vote.wireValue) {
                                    "HOAKS" -> "Hoaks"
                                    "WASPADA" -> "Waspada"
                                    else -> "Valid"
                                },
                                color = WaspadAIBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            if (response.hasImage) {
                                CommunityEvidenceImage(
                                    imageUrl = "${imageBaseUrl.trimEnd('/')}/api/v1/community/$caseId/responses/${response.responseId}/image",
                                    accessToken = accessToken,
                                    author = response.author,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .padding(top = 7.dp),
                                )
                            }
                        }
                    }
                    if (index != responses.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 44.dp),
                            color = Color.Black.copy(alpha = .08f),
                        )
                    }
                }
            }
        }
    }
}

internal data class CommunityLikeAnimation(
    val scale: Float,
    val burstProgress: Float,
)

@Composable
internal fun rememberCommunityLikeAnimation(
    postId: String,
    isSupported: Boolean,
): CommunityLikeAnimation {
    val scale = remember(postId) { Animatable(1f) }
    val burst = remember(postId) { Animatable(1f) }
    var previousSupportState by remember(postId) { mutableStateOf(isSupported) }
    LaunchedEffect(isSupported) {
        if (isSupported && !previousSupportState) {
            burst.snapTo(0f)
            launch { burst.animateTo(1f, tween(durationMillis = 620)) }
            scale.snapTo(.72f)
            scale.animateTo(1.3f, spring(dampingRatio = .38f, stiffness = 720f))
            scale.animateTo(1f, spring(dampingRatio = .55f, stiffness = 520f))
        } else if (!isSupported) {
            scale.animateTo(1f)
            burst.snapTo(1f)
        }
        previousSupportState = isSupported
    }
    return CommunityLikeAnimation(scale.value, burst.value)
}

internal fun Modifier.communityLikeEffect(animation: CommunityLikeAnimation): Modifier =
    graphicsLayer {
        scaleX = animation.scale
        scaleY = animation.scale
    }.drawWithContent {
        drawContent()
        val progress = animation.burstProgress
        if (progress < 1f) {
            repeat(10) { index ->
                val angle = (2.0 * PI * index / 10.0) - (PI / 2.0)
                val distance = 8.dp.toPx() + (14.dp.toPx() * progress)
                val particleCenter = androidx.compose.ui.geometry.Offset(
                    x = center.x + (cos(angle) * distance).toFloat(),
                    y = center.y + (sin(angle) * distance).toFloat(),
                )
                drawCircle(
                    color = CommunityLikePink.copy(alpha = 1f - progress),
                    radius = (2.4.dp.toPx() * (1f - progress)).coerceAtLeast(.6f),
                    center = particleCenter,
                )
            }
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
            tint = Color.Black.copy(alpha = .4f),
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Kasus tidak ditemukan",
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Coba kata kunci atau filter lain.",
            color = Color.Black.copy(alpha = .4f),
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
            timelineState = rememberCommunityTimelineState(),
            onAction = {},
            onBack = {},
            onSharePost = {},
            onOpenPost = {},
            onDestinationSelected = {},
        )
    }
}
