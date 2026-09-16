package id.waspadai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = WaspadAIBlue,
    secondary = WaspadAIDarkBlue,
    tertiary = WaspadAIContribution,
    background = WaspadAIBackground,
    surface = WaspadAIBackground,
    onPrimary = WaspadAIBackground,
    onBackground = WaspadAIText,
    onSurface = WaspadAIText,
)

private val LightColorScheme = lightColorScheme(
    primary = WaspadAIBlue,
    secondary = WaspadAIDarkBlue,
    tertiary = WaspadAIContribution,
    background = WaspadAIBackground,
    surface = WaspadAIBackground,
    onPrimary = WaspadAIBackground,
    onBackground = WaspadAIText,
    onSurface = WaspadAIText,
)

@Composable
fun WaspadAITheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
