package id.waspadai.app.feature.community.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAICaution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIHoax
import id.waspadai.app.ui.theme.WaspadAIValid

@Composable
fun CommunityDetailScreen(
    post: CommunityPost,
    onBack: () -> Unit,
    onSupportClick: () -> Unit,
    onVerdictClick: (CommunityVerdict) -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    var assessmentExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    Scaffold(
        containerColor = WaspadAIBackground,
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Koneksi",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DetailHeader(onBack)
            }
            item {
                Column(Modifier.padding(horizontal = 23.dp)) {
                    DetailAuthor(post)
                    Spacer(Modifier.height(14.dp))
                    Text(post.body, color = Color.Black, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    CommunityEvidenceImage(
                        evidenceRes = post.evidenceRes,
                        author = post.author,
                    )
                    Spacer(Modifier.height(12.dp))
                    CommunityInsight(post)
                    Spacer(Modifier.height(14.dp))
                    CommunityAssessmentPanel(
                        post = post,
                        expanded = assessmentExpanded,
                        onToggle = { assessmentExpanded = !assessmentExpanded },
                        onVerdictClick = onVerdictClick,
                        onSubmit = { assessmentExpanded = false },
                    )
                    Spacer(Modifier.height(10.dp))
                    DetailActions(post, onSupportClick)
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(WaspadAIBlue),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
        Text(
            "Detail Kasus",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailAuthor(post: CommunityPost) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(post.avatarRes),
            contentDescription = "Foto ${post.author}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(post.author, color = WaspadAIDarkBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(post.timestamp, color = Color(0xFF71808A), fontSize = 12.sp)
        }
        Text(
            "Terhubung",
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE7F3FC))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = WaspadAIBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CommunityInsight(post: CommunityPost) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Insight komunitas", color = WaspadAIDarkBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightMetric("Hoaks", post.hoaxCount, WaspadAIHoax, Modifier.weight(1f))
                InsightMetric("Waspada", post.cautionCount, WaspadAICaution, Modifier.weight(1f))
                InsightMetric("Valid", post.validCount, WaspadAIValid, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InsightMetric(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailActions(post: CommunityPost, onSupportClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSupportClick) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Dukung kasus", tint = Color.Black)
        }
        Text(post.supportCount.toString(), fontSize = 13.sp)
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Rounded.ChatBubble, contentDescription = "Komentar", modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(post.commentCount.toString(), fontSize = 13.sp)
    }
}
