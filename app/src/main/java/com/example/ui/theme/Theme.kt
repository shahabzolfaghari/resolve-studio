package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ResolveDarkColorScheme = darkColorScheme(
    primary = ResolveAmber,
    onPrimary = Color(0xFF1E1400),
    primaryContainer = Color(0xFF452B00),
    onPrimaryContainer = Color(0xFFFFDFA0),
    secondary = ResolveCyan,
    onSecondary = Color(0xFF001F25),
    secondaryContainer = Color(0xFF004F5C),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = ResolveGreen,
    onTertiary = Color(0xFF00210E),
    tertiaryContainer = Color(0xFF005328),
    onTertiaryContainer = Color(0xFF67FD9A),
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = StudioCardBorder,
    error = ResolveRed,
    onError = Color.White
)

@Composable
fun ResolveStudioTheme(
    content: @Composable () -> Unit
) {
    // Color grading suites universally enforce deep neutral dark palettes for color accuracy
    MaterialTheme(
        colorScheme = ResolveDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ResolveStudioTheme(content = content)
}
