package org.arogyasathi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryTeal = Color(0xFF0F766E)
val DarkTeal = Color(0xFF005C55)
val EmergencyRed = Color(0xFFB42318)
val HighPriorityOrange = Color(0xFFD97706)
val MediumPriorityAmber = Color(0xFFF59E0B)
val RoutineGreen = Color(0xFF237A57)
val DeepNavy = Color(0xFF0F766E)
val CanvasBackground = Color(0xFFF7F8F6)
val SurfaceCrisp = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF172321)
val TextSecondary = Color(0xFF52615E)
val BorderSubtle = Color(0xFFDCE5E1)
val GlassBackground = Color(0xEBFFFFFF)
val GlassBorder = Color(0x73FFFFFF)
val PaleBackground = Color(0xFFF7F8F6)

// Dark Theme Palette for Night Visits in Rural Huts
val DarkCanvasBackground = Color(0xFF0F1716)
val DarkSurfaceCrisp = Color(0xFF182422)
val DarkTextPrimary = Color(0xFFEFFCF9)
val DarkTextSecondary = Color(0xFF94A3A0)
val DarkBorderSubtle = Color(0xFF293B38)
val DarkGlassBackground = Color(0xEB182422)
val DarkGlassBorder = Color(0x33FFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F4F1),
    onPrimaryContainer = DarkTeal,
    secondary = RoutineGreen,
    onSecondary = Color.White,
    error = EmergencyRed,
    onError = Color.White,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = SurfaceCrisp,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE9F7F3),
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF14B8A6),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF133E3A),
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF064E3B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    background = DarkCanvasBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceCrisp,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF1F2F2C),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorderSubtle
)

@Composable
fun ArogyasathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}

