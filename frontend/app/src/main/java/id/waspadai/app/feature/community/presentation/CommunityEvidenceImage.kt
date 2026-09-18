package id.waspadai.app.feature.community.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIMuted

/**
 * Renders all evidence in one 16:9 landscape frame so the feed and detail page
 * have a consistent photo layout. Images are automatically cropped to fill it.
 */
@Composable
fun CommunityEvidenceImage(
    @DrawableRes evidenceRes: Int,
    author: String,
    additionalEvidenceRes: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val painter = painterResource(evidenceRes)
    val evidenceResources = listOf(evidenceRes) + additionalEvidenceRes
    var isViewerOpen by rememberSaveable(evidenceResources) { mutableStateOf(false) }
    Image(
        painter = painter,
        contentDescription = "Bukti visual dari $author",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF2F5F7))
            .clickable { isViewerOpen = true },
    )

    if (isViewerOpen) {
        val pagerState = rememberPagerState(pageCount = { evidenceResources.size })
        Dialog(
            onDismissRequest = { isViewerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 12.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Bukti visual dari $author",
                                color = WaspadAIDarkBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (evidenceResources.size > 1) "Foto ${pagerState.currentPage + 1} dari ${evidenceResources.size}" else "Foto bukti",
                                color = WaspadAIMuted,
                                fontSize = 11.sp,
                            )
                        }
                        IconButton(onClick = { isViewerOpen = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tutup foto", tint = WaspadAIDarkBlue)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F4F7)),
                    ) { page ->
                        val pagePainter = painterResource(evidenceResources[page])
                        val intrinsic = pagePainter.intrinsicSize
                        val aspectRatio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
                            (intrinsic.width / intrinsic.height).coerceIn(0.45f, 2.4f)
                        } else {
                            1f
                        }
                        Image(
                            painter = pagePainter,
                            contentDescription = "Foto bukti ${page + 1} dari ${evidenceResources.size}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).heightIn(min = 220.dp, max = 620.dp),
                        )
                    }
                    if (evidenceResources.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(evidenceResources.size) { index ->
                                Box(
                                    modifier = Modifier.padding(horizontal = 4.dp).size(if (index == pagerState.currentPage) 8.dp else 6.dp).background(
                                        if (index == pagerState.currentPage) WaspadAIDarkBlue else WaspadAIDarkBlue.copy(alpha = 0.35f),
                                        RoundedCornerShape(50),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
