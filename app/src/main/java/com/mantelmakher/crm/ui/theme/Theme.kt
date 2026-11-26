package com.mantelmakher.crm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0DC),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF6D5B45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DEBE),
    onSecondaryContainer = Color(0xFF26190B),
    tertiary = Color(0xFF1E4D78),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF001C38),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7F5),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF6F7F5),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDDE5E0),
    onSurfaceVariant = Color(0xFF3F4944),
    outline = Color(0xFF6F7974),
    outlineVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2EF),
    surfaceContainer = Color(0xFFEAECE9),
    surfaceContainerHigh = Color(0xFFE4E7E3),
    surfaceContainerHighest = Color(0xFFDEE3DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EDCC4),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFB4F0DC),
    secondary = Color(0xFFD9C3A4),
    onSecondary = Color(0xFF3C2E1C),
    secondaryContainer = Color(0xFF544433),
    onSecondaryContainer = Color(0xFFF6DEBE),
    tertiary = Color(0xFFA9C9F5),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF00497D),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4944),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D201F),
    surfaceContainerHigh = Color(0xFF272B29),
    surfaceContainerHighest = Color(0xFF323633),
)

@Composable
fun MantelCrmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
