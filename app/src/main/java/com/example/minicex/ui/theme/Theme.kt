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
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF0F172A),
    inverseSurface = Color(0xFF1E1B4B),
    inverseOnSurface = Color(0xFFE2E8F0),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFEF4444),
    onError = Color.White,
    inverseSurface = Color(0xFFEEF2FF),
    inverseOnSurface = Color(0xFF0F172A),
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
