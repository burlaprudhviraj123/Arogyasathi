package org.arogyasathi.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryTeal = Color(0xFF0F766E)
val DarkTeal = Color(0xFF0D5E56)
val EmergencyRed = Color(0xFFDC2626)
val HighPriorityOrange = Color(0xFFD97706)
val MediumPriorityAmber = Color(0xFFF59E0B)
val RoutineGreen = Color(0xFF059669)
val DeepNavy = Color(0xFF0F172A)
val CanvasBackground = Color(0xFFF8FAFC)
val SurfaceCrisp = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475569)
val BorderSubtle = Color(0xFFE2E8F0)
val GlassBackground = Color(0xF2FFFFFF)
val GlassBorder = Color(0x66E2E8F0)
val DarkGlassBackground = Color(0xF20F172A)
val DarkGlassBorder = Color(0x33FFFFFF)
val PaleBackground = Color(0xFFF8FAFC)

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