package com.pport.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7B61FF),       // Purple
    onPrimary = Color.White,

    background = Color(0xFF0A0A0A),    // Deep black
    onBackground = Color.White,

    surface = Color(0xFF121212),
    onSurface = Color.White,

    secondary = Color(0xFF1F1F1F),
    onSecondary = Color.White
)

@Composable
fun PPortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}