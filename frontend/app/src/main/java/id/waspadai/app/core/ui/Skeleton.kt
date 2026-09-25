package id.waspadai.app.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics

private val SkeletonBase = Color(0xFFE7EFF4)
private val SkeletonHighlight = Color(0xFFF6FAFC)

@Composable
fun rememberSkeletonBrush(): Brush {
    if (LocalInspectionMode.current) {
        return Brush.linearGradient(listOf(SkeletonBase, SkeletonHighlight, SkeletonBase))
    }
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val offset by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-shimmer-offset",
    )
    return Brush.linearGradient(
        colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
        start = Offset(offset, 0f),
        end = Offset(offset + 600f, 600f),
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape,
    brush: Brush,
) {
    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .background(brush, shape),
    )
}
