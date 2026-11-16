package de.meineserien.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF002642),
    primaryContainer = Color(0xFF153E5C),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF81C784),
    background = Color(0xFF0F1216),
    surface = Color(0xFF171C22),
    surfaceVariant = Color(0xFF222933),
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFFAAB4C2)
)

@Composable
fun MeineSerienTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
