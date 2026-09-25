package id.waspadai.app.feature.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import id.waspadai.app.R
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.core.ui.SkeletonBlock
import id.waspadai.app.core.ui.rememberSkeletonBrush
import id.waspadai.app.core.ui.waspadAIBottomNavigationContentPadding
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAIValid

private val HomeText = Color(0xFF1E1E1E)

@Composable
fun HomeRoute(
    onDestinationSelected: (String) -> Unit,
    onCommunityCaseSelected: (String) -> Unit,
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onDestinationSelected = onDestinationSelected,
        onCommunityCaseSelected = onCommunityCaseSelected,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onDestinationSelected: (String) -> Unit,
    onCommunityCaseSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomNavigationPadding = waspadAIBottomNavigationContentPadding()
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = WaspadAIBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = bottomNavigationPadding + 20.dp),
            ) {
                item { HomeHero() }
                item {
                    HomeBody(
                        uiState = uiState,
                        onAction = onAction,
                        onCasesClick = { onDestinationSelected("Koneksi") },
                        onCaseClick = onCommunityCaseSelected,
                        onVerifyClick = { onDestinationSelected("Periksa") },
                        onLearningClick = { onDestinationSelected("Pelajari") },
                    )
                }
            }
        }
        WaspadAIBottomNavigation(
            selectedDestination = "Beranda",
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaspadAIBlue),
    ) {
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
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.waspadai_logo),
                contentDescription = "Logo WaspadAI",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "WaspadAI",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeBody(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onCasesClick: () -> Unit,
    onCaseClick: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onLearningClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Color.White)
            .padding(top = 14.dp, bottom = 20.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .shadow(5.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSearchField(
                    value = uiState.searchQuery,
                    onValueChange = { onAction(HomeAction.SearchChanged(it)) },
                    modifier = Modifier.weight(1f),
                )
                HomeFilterButton(uiState = uiState, onAction = onAction)
            }
        }

        HomeSectionHeader(
            title = "Verifikasi Kasus Terbaru",
            onSeeAll = onCasesClick,
            modifier = Modifier.padding(start = 21.dp, top = 13.dp, end = 21.dp),
        )
        if (uiState.loading && uiState.cases.isEmpty()) {
            HomeCasesSkeleton()
        } else if (uiState.error != null && uiState.cases.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HomeEmptyMessage(uiState.error)
                TextButton(onClick = { onAction(HomeAction.Refresh) }) {
                    Text("Coba Lagi", color = WaspadAIBlue)
                }
            }
        } else if (uiState.visibleCases.isEmpty()) {
            HomeEmptyMessage("Kasus tidak ditemukan.")
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                uiState.visibleCases.take(2).forEach { item ->
                    HomeCaseCard(
                        item = item,
                        onClick = { onCaseClick(item.communityId) },
                    )
                }
                HorizontalDivider(color = Color.Black.copy(alpha = .14f))
            }
        }

        HomeVerificationCta(
            onClick = onVerifyClick,
            modifier = Modifier.padding(start = 21.dp, top = 16.dp, end = 21.dp),
        )

        HomeSectionHeader(
            title = "Rekomendasi Pembelajaran",
            onSeeAll = onLearningClick,
            modifier = Modifier.padding(start = 21.dp, top = 16.dp, end = 21.dp),
        )
        if (uiState.loading && uiState.learningRecommendations.isEmpty()) {
            HomeLearningSkeleton()
        } else if (uiState.visibleLearningRecommendations.isEmpty()) {
            HomeEmptyMessage("Materi tidak ditemukan.")
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 21.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                uiState.visibleLearningRecommendations.take(2).forEach { item ->
                    HomeLearningCard(
                        item = item,
                        accessToken = uiState.accessToken,
                        onClick = onLearningClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (uiState.visibleLearningRecommendations.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HomeCasesSkeleton() {
    val brush = rememberSkeletonBrush()
    Column(
        modifier = Modifier.fillMaxWidth().testTag("home-cases-skeleton"),
    ) {
        repeat(2) {
            HorizontalDivider(color = Color.Black.copy(alpha = .08f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(Modifier.size(44.dp), CircleShape, brush)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth(.42f).height(13.dp), RoundedCornerShape(6.dp), brush)
                        SkeletonBlock(Modifier.fillMaxWidth(.25f).height(9.dp), RoundedCornerShape(5.dp), brush)
                    }
                    SkeletonBlock(Modifier.width(62.dp).height(30.dp), RoundedCornerShape(4.dp), brush)
                }
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(10.dp), RoundedCornerShape(5.dp), brush)
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(Modifier.fillMaxWidth(.78f).height(10.dp), RoundedCornerShape(5.dp), brush)
            }
        }
    }
}

@Composable
private fun HomeLearningSkeleton() {
    val brush = rememberSkeletonBrush()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp)
            .testTag("home-learning-skeleton"),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        repeat(2) {
            Column(
                modifier = Modifier.weight(1f).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth().height(104.dp), RoundedCornerShape(6.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth(.78f).height(11.dp), RoundedCornerShape(5.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth().height(9.dp), RoundedCornerShape(5.dp), brush)
                SkeletonBlock(Modifier.fillMaxWidth(.62f).height(9.dp), RoundedCornerShape(5.dp), brush)
            }
        }
    }
}

@Composable
private fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.Black.copy(alpha = .18f)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            textStyle = TextStyle(color = HomeText, fontSize = 14.sp),
            cursorBrush = SolidColor(WaspadAIBlue),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Cari kasus, materi, atau informasi...",
                                color = Color.Black.copy(alpha = .35f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun HomeFilterButton(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(WaspadAIBlue)
                .clickable { onAction(HomeAction.FilterClicked) }
                .semantics {
                    contentDescription = "Filter kasus: ${uiState.selectedFilter.label}"
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
        if (uiState.isFilterMenuVisible) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { onAction(HomeAction.FilterDismissed) },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 54.dp)
                        .width(148.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFD7EAF7), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    HomeCaseFilter.entries.forEach { filter ->
                        val selected = filter == uiState.selectedFilter
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(if (selected) WaspadAIBlue else Color.White)
                                .clickable { onAction(HomeAction.FilterSelected(filter)) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = filter.label,
                                color = if (selected) Color.White else Color.Black.copy(alpha = .65f),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = HomeText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Lihat Semua", color = HomeText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomeVerificationCta(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .clickable(role = Role.Button, onClick = onClick),
        color = WaspadAIBlue,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
    ) {
        Box {
            Image(
                painter = painterResource(R.drawable.community_header_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = .58f,
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 17.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = .16f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(29.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Khawatir dengan pesan anonim yang Anda dapat?",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Periksa dengan WaspadAI saja",
                        modifier = Modifier.padding(top = 5.dp),
                        color = Color(0xFFFFDC6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCaseCard(
    item: HomeCaseUiModel,
    onClick: () -> Unit,
) {
    val interactionSource = remember(item.communityId) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(color = Color.Black.copy(alpha = .14f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isPressed || isHovered) Color.Black.copy(alpha = .07f) else Color.Transparent,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 21.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(homeAvatarFor(item.creatorName)),
                    contentDescription = "Foto ${item.creatorName}",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.creatorName,
                        color = Color.Black,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.timestamp,
                        color = Color.Black.copy(alpha = .4f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HomeCaseBadge(item)
            }
            Text(
                text = item.description,
                modifier = Modifier.padding(top = 8.dp),
                color = Color.Black.copy(alpha = .78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun homeAvatarFor(creatorName: String): Int = when {
    creatorName.contains("Alya", ignoreCase = true) -> R.drawable.community_avatar_alya
    creatorName.contains("Dimas", ignoreCase = true) -> R.drawable.community_avatar_dimas
    creatorName.contains("Rifqi", ignoreCase = true) -> R.drawable.community_avatar_rifqi
    else -> R.drawable.community_avatar_putu
}

@Composable
private fun HomeCaseBadge(item: HomeCaseUiModel) {
    val background = when (item.tone) {
        HomeCaseTone.Hoax -> WaspadAIHoax
        HomeCaseTone.Valid -> WaspadAIValid
    }
    Surface(
        modifier = Modifier
            .width(62.dp)
            .height(30.dp),
        color = background,
        shape = RoundedCornerShape(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = item.status,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeLearningCard(
    item: HomeLearningUiModel,
    accessToken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val imageRequest = remember(context, item.moduleId, item.imageUrl, accessToken) {
        item.imageUrl?.let { imageUrl ->
            val headers = NetworkHeaders.Builder().apply {
                if (accessToken.isNotBlank()) {
                    set("Authorization", "Bearer ${accessToken.trim()}")
                }
            }.build()
            ImageRequest.Builder(context)
                .data(imageUrl)
                .httpHeaders(headers)
                .memoryCacheKey("home-learning:${item.moduleId}")
                .diskCacheKey("home-learning:${item.moduleId}")
                .build()
        }
    }
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0069AC)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.home_learning_safe_info),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = item.title,
            modifier = Modifier.padding(top = 7.dp),
            color = HomeText,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.description,
            color = Color.Black,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeEmptyMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        color = Color(0xFF71808A),
        fontSize = 13.sp,
    )
}
