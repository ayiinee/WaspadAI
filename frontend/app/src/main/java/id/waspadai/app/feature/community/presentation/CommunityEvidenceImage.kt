package id.waspadai.app.feature.community.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** Shows the image belonging to the published case when one is available. */
@Composable
fun CommunityEvidenceImage(
    imageUrl: String?,
    accessToken: String,
    author: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) return

    val containerModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFFF2F5F7))

    val headers = NetworkHeaders.Builder()
        .apply {
            if (accessToken.isNotBlank()) {
                set("Authorization", "Bearer ${accessToken.trim()}")
            }
        }
        .build()
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .httpHeaders(headers)
            .build(),
        contentDescription = "Bukti visual dari $author",
        modifier = containerModifier,
    )
}
