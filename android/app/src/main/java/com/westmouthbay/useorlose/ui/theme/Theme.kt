package com.westmouthbay.useorlose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The fixed blue palette from the Calendar specification mockups: medium blue
 * primary (#1F4FA8), pale blue surfaces (#EAF1FB), near-black navy text.
 * A coordinated dark variant ships alongside the light theme.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FB),
    onPrimaryContainer = Color(0xFF0B1B33),
    secondaryContainer = Color(0xFFEAF1FB),
    onSecondaryContainer = Color(0xFF1F4FA8),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7FC),
    surfaceContainer = Color(0xFFF4F7FC),
    surfaceContainerHigh = Color(0xFFE9EFF8),
    surfaceContainerHighest = Color(0xFFE2E9F4),
    onBackground = Color(0xFF0B1B33),
    onSurface = Color(0xFF0B1B33),
    surfaceVariant = Color(0xFFF4F7FC),
    onSurfaceVariant = Color(0xFF46536B),
    outline = Color(0xFFC5D2E6),
    // Warning surfaces (the unavailable-state card from the mockups).
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF6B4E00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C3F0),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF1D3A66),
    onPrimaryContainer = Color(0xFFEAF1FB),
    secondaryContainer = Color(0xFF1D3A66),
    onSecondaryContainer = Color(0xFFA8C3F0),
    background = Color(0xFF0E1524),
    surface = Color(0xFF0E1524),
    surfaceContainerLowest = Color(0xFF0B111D),
    surfaceContainerLow = Color(0xFF141D2F),
    surfaceContainer = Color(0xFF1A2436),
    surfaceContainerHigh = Color(0xFF212C41),
    surfaceContainerHighest = Color(0xFF273349),
    onBackground = Color(0xFFE6ECF5),
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = Color(0xFF1A2436),
    onSurfaceVariant = Color(0xFFA9B6CC),
    outline = Color(0xFF39455E),
    // Warning surfaces (the unavailable-state card from the mockups).
    tertiaryContainer = Color(0xFF4A3A18),
    onTertiaryContainer = Color(0xFFFFE3A3),
)

@Composable
fun UseOrLoseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
