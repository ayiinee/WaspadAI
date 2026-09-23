package id.waspadai.app.feature.community.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import id.waspadai.app.feature.community.domain.CommunityMedia

/** Backward-compatible single-image wrapper used by response attachments. */
@Composable
fun CommunityEvidenceImage(
    imageUrl: String?,
    accessToken: String,
    author: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) return
    CommunityMediaCarousel(
        media = listOf(CommunityMedia(id = imageUrl, url = imageUrl)),
        accessToken = accessToken,
        author = author,
        modifier = modifier,
    )
}

@Composable
fun CommunityMediaCarousel(
    media: List<CommunityMedia>,
    accessToken: String,
    author: String,
    modifier: Modifier = Modifier,
) {
    val orderedMedia = remember(media) { media.sortedBy(CommunityMedia::position).take(4) }
    if (orderedMedia.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { orderedMedia.size })
    var viewerIndex by rememberSaveable(orderedMedia.map(CommunityMedia::id)) { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF2F5F7)),
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = if (orderedMedia.size > 1) 1 else 0,
            key = { index -> orderedMedia[index].id },
        ) { index ->
            StableCommunityImage(
                media = orderedMedia[index],
                accessToken = accessToken,
                contentDescription = "Bukti visual ${index + 1} dari ${orderedMedia.size} oleh $author",
                modifier = Modifier.fillMaxWidth().clickable { viewerIndex = index },
                contentScale = ContentScale.Crop,
            )
        }
        if (orderedMedia.size > 1) {
            MediaIndicator(
                currentIndex = pagerState.currentPage,
                total = orderedMedia.size,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            )
        }
    }

    viewerIndex?.let { initialIndex ->
        CommunityImageViewer(
            media = orderedMedia,
            initialIndex = initialIndex,
            accessToken = accessToken,
            author = author,
            onDismiss = { viewerIndex = null },
        )
    }
}

@Composable
private fun CommunityImageViewer(
    media: List<CommunityMedia>,
    initialIndex: Int,
    accessToken: String,
    author: String,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(media.indices),
        pageCount = { media.size },
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Preview foto",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Foto ${pagerState.currentPage + 1} dari ${media.size} · $author",
                            color = Color.Black.copy(alpha = .4f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup preview foto",
                            tint = Color.Black,
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 620.dp).aspectRatio(4f / 3f),
                    beyondViewportPageCount = if (media.size > 1) 1 else 0,
                    key = { index -> media[index].id },
                    verticalAlignment = Alignment.CenterVertically,
                ) { index ->
                    StableCommunityImage(
                        media = media[index],
                        accessToken = accessToken,
                        contentDescription = "Gambar ${index + 1} dari ${media.size} oleh $author",
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF2F5F7)),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (media.size > 1) {
                    MediaIndicator(
                        currentIndex = pagerState.currentPage,
                        total = media.size,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp),
                        dark = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun StableCommunityImage(
    media: CommunityMedia,
    accessToken: String,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val context = LocalContext.current
    val request = remember(context, media.id, media.url, accessToken) {
        val headers = NetworkHeaders.Builder().apply {
            if (accessToken.isNotBlank()) set("Authorization", "Bearer ${accessToken.trim()}")
        }.build()
        ImageRequest.Builder(context)
            .data(media.url)
            .httpHeaders(headers)
            .memoryCacheKey("community-media:${media.id}")
            .diskCacheKey("community-media:${media.id}")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    key(media.id, media.url) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

@Composable
private fun MediaIndicator(
    currentIndex: Int,
    total: Int,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    Row(
        modifier = modifier
            .semantics {
                contentDescription = "Foto ${currentIndex + 1} dari $total"
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color.Transparent else Color.Black.copy(alpha = 0.48f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 8.dp else 6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (dark) {
                            if (index == currentIndex) Color.Black else Color.Black.copy(alpha = .16f)
                        } else {
                            if (index == currentIndex) Color.White else Color.White.copy(alpha = 0.55f)
                        },
                    ),
            )
        }
    }
}
