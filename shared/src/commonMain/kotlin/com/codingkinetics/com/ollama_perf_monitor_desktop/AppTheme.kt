package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ForensicsDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2589BD),
    onPrimary = Color(0xFFE1E6EA),
    primaryContainer = Color(0xFF0B0F14).copy(alpha = 0.62f),
    onPrimaryContainer = Color(0xFFD8ECD0),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF2B1700),
    background = Color(0xFF263238).copy(alpha = 0.75f),
    onBackground = Color(0xFFE1E6EA),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE1E6EA),
    surfaceVariant = Color(0xFFA39B8B),
    onSurfaceVariant = Color(0xFFC2C8CC),
    error = Color(0xFFFF5252),
    onError = Color(0xFF330000),
    tertiary = Color(0xFF66BB6A),
    onTertiary = Color(0xFF003820),
)

private val ForensicsLightColorScheme = lightColorScheme(
    primary = Color(0xFF1D628B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E7FF),
    onPrimaryContainer = Color(0xFF001E30),
    secondary = Color(0xFF8A5100),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE3E7),
    onSurfaceVariant = Color(0xFF43474B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    tertiary = Color(0xFF006E38),
    onTertiary = Color(0xFFFFFFFF)
)

@Composable
fun OllamaForensicsTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ForensicsDarkColorScheme else ForensicsLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}