package id.waspadai.app.feature.home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import id.waspadai.app.R
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAICaution
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAIValid

private val HomeText = Color(0xFF1E1E1E)
private val HomeProfileBackground = Color(0xFF263847)

@Composable
fun HomeRoute(
    onDestinationSelected: (String) -> Unit,
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onDestinationSelected = onDestinationSelected,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Beranda",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                HomeHero(
                    displayName = uiState.displayName,
                    onVerifyClick = { onDestinationSelected("Periksa") },
                )
            }
            item {
                HomeBody(
                    uiState = uiState,
                    onAction = onAction,
                    onCasesClick = { onDestinationSelected("Koneksi") },
                    onLearningClick = { onDestinationSelected("Pelajari") },
                )
            }
        }
    }
}

@Composable
private fun HomeHero(displayName: String, onVerifyClick: () -> Unit) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "WaspadAI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Surface(
                        modifier = Modifier.padding(top = 3.dp),
                        color = HomeProfileBackground,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.community_avatar_putu),
                                contentDescription = "Foto profil $displayName",
                                modifier = Modifier
                                    .size(27.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                alignment = BiasAlignment(0f, -.45f),
                            )
                            Text(
                                text = displayName,
                                modifier = Modifier.padding(start = 5.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    HomeHeaderAction(
                        contentDescription = "Notifikasi",
                        icon = { Icon(Icons.Outlined.Notifications, null, tint = Color.White) },
                    )
                    HomeHeaderAction(
                        contentDescription = "Pengaturan",
                        icon = { Icon(Icons.Outlined.Settings, null, tint = Color.White) },
                    )
                }
            }
            Image(
                painter = painterResource(R.drawable.home_verification_banner),
                contentDescription = "Ragu sama informasi yang kamu terima? Mulai verifikasi dengan WaspadAI.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .aspectRatio(1916f / 821f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = onVerifyClick,
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun HomeHeaderAction(
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .semantics { this.contentDescription = contentDescription },
        color = Color.Black.copy(alpha = .48f),
        shape = CircleShape,
    ) {
        IconButton(onClick = {}, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}

@Composable
private fun HomeBody(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onCasesClick: () -> Unit,
    onLearningClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .padding(horizontal = 21.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeSearchField(
                value = uiState.searchQuery,
                onValueChange = { onAction(HomeAction.SearchChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (uiState.cautionOnly) Color(0xFFE7F3FC) else Color.Transparent,
            ) {
                IconButton(onClick = { onAction(HomeAction.ToggleCautionFilter) }) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = if (uiState.cautionOnly) {
                            "Tampilkan semua kasus"
                        } else {
                            "Tampilkan kasus waspada"
                        },
                        tint = if (uiState.cautionOnly) WaspadAIBlue else Color.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        HomeSectionHeader(
            title = "Verifikasi Kasus Terbaru",
            onSeeAll = onCasesClick,
            modifier = Modifier.padding(top = 13.dp),
        )
        if (uiState.loading && uiState.cases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = WaspadAIBlue, modifier = Modifier.size(28.dp))
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.visibleCases.forEach { item ->
                    HomeCaseCard(item = item, onClick = onCasesClick)
                }
            }
        }

        HomeSectionHeader(
            title = "Rekomendasi Pembelajaran",
            onSeeAll = onLearningClick,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (uiState.visibleLearningRecommendations.isEmpty()) {
            HomeEmptyMessage("Materi tidak ditemukan.")
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
private fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, HomeText),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            textStyle = TextStyle(color = HomeText, fontSize = 13.sp),
            cursorBrush = SolidColor(WaspadAIBlue),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 13.dp, end = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Cari kasus, materi, atau informasi...",
                                color = HomeText.copy(alpha = .24f),
                                fontSize = 13.sp,
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
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
        )
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onSeeAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Lihat Semua", color = HomeText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HomeCaseCard(
    item: HomeCaseUiModel,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = HomeText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.description,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color.Black,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HomeCaseBadge(item)
        }
    }
}

@Composable
private fun HomeCaseBadge(item: HomeCaseUiModel) {
    val background = when (item.tone) {
        HomeCaseTone.Hoax -> WaspadAIHoax
        HomeCaseTone.Caution -> WaspadAICaution
        HomeCaseTone.Valid -> WaspadAIValid
    }
    Surface(
        modifier = Modifier
            .width(64.dp)
            .height(40.dp),
        color = background,
        shape = RoundedCornerShape(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = item.status,
                color = Color.White,
                fontSize = if (item.tone == HomeCaseTone.Caution) 10.sp else 12.sp,
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
