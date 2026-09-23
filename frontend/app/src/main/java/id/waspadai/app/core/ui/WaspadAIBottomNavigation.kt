package id.waspadai.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue

private val BottomNavigationBarHeight = 66.dp
private val CenterCtaDiameter = 72.dp
val WaspadAIBottomNavigationHeight = BottomNavigationBarHeight + CenterCtaDiameter / 2

data class CommunityNotificationState(
    val showBadge: Boolean = false,
    val markOpened: () -> Unit = {},
)

val LocalCommunityNotification = staticCompositionLocalOf { CommunityNotificationState() }

@Composable
fun WaspadAIBottomNavigation(
    selectedDestination: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerNavigationInteraction = remember { MutableInteractionSource() }
    val communityNotification = LocalCommunityNotification.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WaspadAIBottomNavigationHeight),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomNavigationBarHeight)
                .align(Alignment.BottomCenter),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomDestination(
                    label = "Beranda",
                    icon = Icons.Rounded.Home,
                    selected = selectedDestination == "Beranda",
                    onClick = { onDestinationSelected("Beranda") },
                    modifier = Modifier.weight(1f),
                )
                BottomDestination(
                    label = "Pelajari",
                    icon = Icons.Rounded.MenuBook,
                    selected = selectedDestination == "Pelajari",
                    onClick = { onDestinationSelected("Pelajari") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(1f))
                BottomDestination(
                    label = "Koneksi",
                    icon = Icons.Rounded.People,
                    selected = selectedDestination == "Koneksi",
                    onClick = {
                        communityNotification.markOpened()
                        onDestinationSelected("Koneksi")
                    },
                    modifier = Modifier.weight(1f),
                    showBadge = communityNotification.showBadge,
                )
                BottomDestination(
                    label = "Progres",
                    icon = Icons.Rounded.BarChart,
                    selected = selectedDestination == "Progres",
                    onClick = { onDestinationSelected("Progres") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 8.dp)
                .size(CenterCtaDiameter)
                .shadow(8.dp, CircleShape)
                .background(WaspadAIBlue, CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .clickable(
                    interactionSource = centerNavigationInteraction,
                    indication = null,
                    onClick = { onDestinationSelected("Periksa") },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_magnify_expand),
                contentDescription = "Periksa informasi",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun BottomDestination(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showBadge: Boolean = false,
) {
    val color = if (selected) WaspadAIBlue else WaspadAILightBlue
    val navigationInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = navigationInteraction,
                indication = null,
                onClick = onClick,
            )
            .padding(top = 12.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(if (selected) 28.dp else 25.dp),
            )
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .background(Color(0xFFE53935), CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .semantics { contentDescription = "Notifikasi baru Koneksi" },
                )
            }
        }
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}
