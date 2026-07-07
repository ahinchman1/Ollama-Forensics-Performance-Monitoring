package com.codingkinetics.com.ollama_perf_monitor_desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun OllamaForensicsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ForensicsDarkColorScheme,
        content = content,
    )
}