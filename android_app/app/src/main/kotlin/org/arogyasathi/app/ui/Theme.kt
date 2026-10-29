package org.arogyasathi.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryTeal = Color(0xFF0F766E)
val DarkTeal = Color(0xFF005C55)
val EmergencyRed = Color(0xFFB42318)
val HighPriorityOrange = Color(0xFFD97706)
val MediumPriorityAmber = Color(0xFFF59E0B)
val RoutineGreen = Color(0xFF237A57)
val DeepNavy = Color(0xFF0F766E) // Mapped to primary teal for clinical branding
val CanvasBackground = Color(0xFFF7F8F6)
val SurfaceCrisp = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF172321)
val TextSecondary = Color(0xFF52615E)
val BorderSubtle = Color(0xFFDCE5E1)
val GlassBackground = Color(0xEBFFFFFF)
val GlassBorder = Color(0x73FFFFFF)
val PaleBackground = Color(0xFFF7F8F6)

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

@Composable
fun ArogyasathiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}