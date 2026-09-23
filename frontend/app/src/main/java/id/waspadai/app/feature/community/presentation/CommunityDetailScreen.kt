package id.waspadai.app.feature.community.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.feature.community.domain.CommunityDetailSnapshot
import id.waspadai.app.feature.community.domain.CommunityResponseItem
import id.waspadai.app.feature.community.domain.CommunityVote
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAICaution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAIMuted
import id.waspadai.app.ui.theme.WaspadAIValid

@Composable
fun CommunityDetailScreen(
    post: CommunityPost,
    accessToken: String,
    onBack: () -> Unit,
    onSupportClick: () -> Unit,
    onVerdictClick: (CommunityVerdict) -> Unit,
    detail: CommunityDetailSnapshot? = null,
    imageBaseUrl: String = "",
    isDetailLoading: Boolean = false,
    isResponseSubmitting: Boolean = false,
    detailError: String? = null,
    onRetryDetail: () -> Unit = {},
    onSubmitResponse: (CommunityVerdict, String, ByteArray?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onDestinationSelected: (String) -> Unit,
) {
    var assessmentExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    val contentGutter = if (LocalConfiguration.current.screenWidthDp < 360) 16.dp else 28.dp
    val displayPost = detail?.let { snapshot ->
        post.copy(
            hoaksCount = snapshot.counts.hoaks,
            waspadaCount = snapshot.counts.waspada,
            validCount = snapshot.counts.valid,
            selectedVerdict = snapshot.userVote?.toPresentation(),
            likeCount = snapshot.likeCount,
            viewCount = snapshot.viewCount,
            commentCount = snapshot.commentCount,
            shareCount = snapshot.shareCount,
            isSupported = snapshot.userLiked,
            media = snapshot.media.ifEmpty { post.media },
            imageUrl = snapshot.media.firstOrNull()?.url ?: post.imageUrl,
        )
    } ?: post
    Scaffold(
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Koneksi",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Header tetap terlihat saat detail kasus digulir.
            CommunityPageHeader(title = "Detail Kasus", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = contentGutter)) {
                    DetailAuthor(post)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = post.title,
                            modifier = Modifier.weight(1f),
                            color = WaspadAIDarkBlue,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(post.body, color = Color.Black, fontSize = 15.sp, lineHeight = 22.sp)
                    if (displayPost.media.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CommunityMediaCarousel(
                            media = displayPost.media,
                            accessToken = accessToken,
                            author = displayPost.author,
                        )
                    } else displayPost.imageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                        Spacer(Modifier.height(12.dp))
                        CommunityEvidenceImage(imageUrl, accessToken, displayPost.author)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isDetailLoading && detail == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = WaspadAIBlue,
                            strokeWidth = 2.dp,
                        )
                    }
                    detailError?.let { error ->
                        Text(
                            text = "$error  Ketuk untuk mencoba lagi.",
                            color = WaspadAIHoax,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onRetryDetail),
                        )
                    }
                    CommunityInsight(displayPost)
                    Spacer(Modifier.height(8.dp))
                    if (!displayPost.isOwner) {
                        CommunityAssessmentPanel(
                            post = displayPost,
                            expanded = assessmentExpanded,
                            onToggle = { assessmentExpanded = !assessmentExpanded },
                            onSubmit = { verdict, reason, bytes, fileName, contentType ->
                                onSubmitResponse(verdict, reason, bytes, fileName, contentType)
                            },
                            isSubmitting = isResponseSubmitting,
                            submitError = detailError,
                        )
                    } else {
                        Text(
                            text = "Anda tidak dapat memberi penilaian pada kasus milik sendiri.",
                            color = WaspadAIMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val responses = detail?.responses.orEmpty()
                    DetailActions(displayPost, responses.size, onSupportClick)
                    Spacer(Modifier.height(14.dp))
                    CommunityResponses(
                        responses = responses,
                        avatarRes = displayPost.avatarRes,
                        caseId = displayPost.id,
                        imageBaseUrl = imageBaseUrl,
                        accessToken = accessToken,
                    )
                    Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAuthor(post: CommunityPost) {
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
        Column(Modifier.weight(1f)) {
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
    }
}

@Composable
private fun CommunityInsight(post: CommunityPost) {
    val segments = listOf(
        PollSegment("Hoaks", post.hoaxCount, Color(0xFF3C5973)),
        PollSegment("Waspada", post.cautionCount, Color(0xFF2D6F9E)),
        PollSegment("Valid", post.validCount, Color(0xFF1F5278)),
    )
    val totalVotes = segments.sumOf(PollSegment::count)
    val leadingSegment = segments.maxByOrNull(PollSegment::count) ?: segments.first()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text("Polling komunitas", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { segment ->
                    val percentage = if (totalVotes == 0) 0 else (segment.count * 100) / totalVotes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = segment.label,
                            modifier = Modifier.width(66.dp),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(segment.color.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(percentage / 100f)
                                    .fillMaxSize()
                                    .background(segment.color),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$percentage%",
                            modifier = Modifier.width(34.dp),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$totalVotes total penilaian • ${leadingSegment.label} paling banyak",
                color = Color.Black,
                fontSize = 12.sp,
            )
            Text(
                text = "Pilih penilaian Anda untuk ikut memperbarui hasil polling.",
                color = Color.Black,
                fontSize = 11.sp,
            )
        }
    }
}

private data class PollSegment(val label: String, val count: Int, val color: Color)

@Composable
private fun DetailActions(post: CommunityPost, responseCount: Int, onSupportClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailActionPill(
            icon = if (post.isSupported) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = post.likeCount.toString(),
            contentDescription = "Total penilaian komunitas",
            tint = if (post.isSupported) Color(0xFFD82A0C) else WaspadAIDarkBlue,
            containerColor = if (post.isSupported) Color(0x22D82A0C) else Color(0xFFE6F0F7),
            onClick = onSupportClick,
        )
        DetailActionPill(
            icon = Icons.Rounded.Visibility,
            label = post.viewCount.toString(),
            contentDescription = "Dilihat ${post.viewCount} kali",
            tint = WaspadAIBlue,
            containerColor = Color(0xFFE7F3FC),
            onClick = {},
        )
        DetailActionPill(
            icon = Icons.Rounded.ChatBubble,
            label = responseCount.toString(),
            contentDescription = "Komentar",
            tint = WaspadAIBlue,
            containerColor = Color(0xFFE7F3FC),
            onClick = {},
        )
    }
}

@Composable
private fun CommunityResponses(
    responses: List<CommunityResponseItem>,
    avatarRes: Int,
    caseId: String,
    imageBaseUrl: String,
    accessToken: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = "Tanggapan komunitas (${responses.size})",
            color = WaspadAIDarkBlue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        responses.forEach { response ->
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(avatarRes),
                        contentDescription = "Foto ${response.author}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            response.author,
                            color = WaspadAIDarkBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(response.createdAt, color = Color(0xFF7B8790), fontSize = 11.sp)
                    }
                    Text(
                        response.vote.toPresentation()?.label ?: "Penilaian",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE7F3FC))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = WaspadAIBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(response.reasoning, color = Color(0xFF202A32), fontSize = 13.sp, lineHeight = 18.sp)
                if (response.hasImage) {
                    CommunityEvidenceImage(
                        imageUrl = "${imageBaseUrl.trimEnd('/')}/api/v1/community/$caseId/responses/${response.responseId}/image",
                        accessToken = accessToken,
                        author = response.author,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFDCE5EB), thickness = 1.dp)
        }
    }
}

fun CommunityPost.statusTextColor(): Color = when (statusLabel) {
    "Evidence terverifikasi" -> Color(0xFF10B981)
    else -> Color(0xFFF59E0B)
}

private fun CommunityVote?.toPresentation(): CommunityVerdict? = when (this) {
    CommunityVote.Hoaks -> CommunityVerdict.Hoaks
    CommunityVote.Waspada -> CommunityVerdict.Waspada
    CommunityVote.Valid -> CommunityVerdict.Valid
    null -> null
}

@Composable
private fun DetailActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = WaspadAIDarkBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
