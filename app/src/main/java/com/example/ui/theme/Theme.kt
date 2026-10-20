package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ─── xAI Strict Near-Black Dark Color Scheme ────────────────────────────────
private val XAIDarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorOnPrimary,
    primaryContainer = ColorCanvasSoft,
    onPrimaryContainer = ColorInk,
    secondary = ColorBody,
    onSecondary = ColorOnPrimary,
    secondaryContainer = ColorCanvasMid,
    onSecondaryContainer = ColorBody,
    tertiary = ColorAccentTwilight,
    onTertiary = ColorOnPrimary,
    background = ColorCanvas,
    onBackground = ColorInk,
    surface = ColorCanvasCard,
    onSurface = ColorInk,
    surfaceVariant = ColorCanvasSoft,
    onSurfaceVariant = ColorBodyMid,
    outline = ColorHairline,
    outlineVariant = ColorHairline,
    error = ColorStatusError,
    onError = ColorInk,
    errorContainer = Color(0xFF3F1414),
    onErrorContainer = Color(0xFFFF8A8A)
)

// ─── xAI Shapes (sm: 8px for cards/inputs, pill: 9999px for buttons/badges) ─
val XAIShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),   // rounded.sm: 8px
    medium = RoundedCornerShape(8.dp),  // card chrome: 8px
    large = RoundedCornerShape(8.dp),
    extraLarge = CircleShape            // rounded.pill: 9999px
)

@Composable
fun ServerUITheme(
    // xAI Design System is strictly dark-canvas only. No light-mode counterpart per spec.
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = XAIDarkColorScheme,
        typography = Typography,
        shapes = XAIShapes,
        content = content
    )
}
