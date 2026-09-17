package id.waspadai.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue

@Composable
fun WaspadAIBottomNavigation(
    selectedDestination: String,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .align(Alignment.BottomCenter),
            color = Color.White,
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
                    onClick = { onDestinationSelected("Koneksi") },
                    modifier = Modifier.weight(1f),
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
                .size(72.dp)
                .background(WaspadAIBlue, CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .clickable { onDestinationSelected("Periksa") },
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
) {
    val color = if (selected) WaspadAIBlue else WaspadAILightBlue
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(if (selected) 28.dp else 25.dp),
        )
        Spacer(Modifier.height(2.dp))
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
