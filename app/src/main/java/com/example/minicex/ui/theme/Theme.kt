package com.example.minicex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Domain-specific semantic colors (not in Material3's slot system) ───────

data class AppColors(
    val scoreSuperior: Color,
    val scoreSatisfactory: Color,
    val scoreUnsatisfactory: Color,
)

private val DarkAppColors = AppColors(
    scoreSuperior = Color(0xFF34D399),
    scoreSatisfactory = Color(0xFFFBBF24),
    scoreUnsatisfactory = Color(0xFFF87171),
)

private val LightAppColors = AppColors(
    scoreSuperior = Color(0xFF10B981),
    scoreSatisfactory = Color(0xFFF59E0B),
    scoreUnsatisfactory = Color(0xFFEF4444),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

// ── Full Material3 color schemes ──────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF85A9FF),
    onPrimary = Color(0xFF071225),
    primaryContainer = Color(0xFF1C3156),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF71C7EF),
    onSecondary = Color(0xFF071225),
    secondaryContainer = Color(0xFF143D55),
    onSecondaryContainer = Color(0xFFD7F2FF),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF071225),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF121E31),
    onSurface = Color(0xFFF3F6FC),
    surfaceVariant = Color(0xFF19263A),
    onSurfaceVariant = Color(0xFF9AAAC2),
    outline = Color(0xFF2C3E58),
    outlineVariant = Color(0xFF26364D),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A),
    inverseSurface = Color(0xFFDCE7FF),
    inverseOnSurface = Color(0xFF0B1833),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FF),
    onPrimaryContainer = Color(0xFF102B62),
    secondary = Color(0xFF2D8BCB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F3FC),
    onSecondaryContainer = Color(0xFF0C4D70),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF0B1833),
    surface = Color.White,
    onSurface = Color(0xFF0B1833),
    surfaceVariant = Color(0xFFF2F6FC),
    onSurfaceVariant = Color(0xFF60708A),
    outline = Color(0xFFD9E2EF),
    outlineVariant = Color(0xFFE4EAF3),
    error = Color(0xFFEF4444),
    onError = Color.White,
    inverseSurface = Color(0xFF102B62),
    inverseOnSurface = Color(0xFFF3F6FC),
)

@Composable
fun MiniCexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
