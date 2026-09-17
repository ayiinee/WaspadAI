package id.waspadai.app.feature.community.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Renders evidence in its original aspect ratio so every screen shows the same
 * complete visual information, regardless of whether the source is portrait or landscape.
 */
@Composable
fun CommunityEvidenceImage(
    @DrawableRes evidenceRes: Int,
    author: String,
    modifier: Modifier = Modifier,
) {
    val painter = painterResource(evidenceRes)
    val intrinsicSize = painter.intrinsicSize
    val imageAspectRatio = if (intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
        intrinsicSize.width / intrinsicSize.height
    } else {
        1f
    }

    Image(
        painter = painter,
        contentDescription = "Bukti visual dari $author",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF2F5F7)),
    )
}
