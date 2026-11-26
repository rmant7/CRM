package com.intelliverse.croman.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
    background = Color(0xFFF6F7F5),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF6F7F5),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDDE5E0),
    onSurfaceVariant = Color(0xFF3F4944),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EDCC4),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFB4F0DC),
    secondary = Color(0xFFD9C3A4),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C3),
)

@Composable
fun CromanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

val LocalLanguage = staticCompositionLocalOf { "en" }
