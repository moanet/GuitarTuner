package com.example.guitartuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Warn,
    onSecondary = TunerBackground,
    background = TunerBackground,
    onBackground = TextPrimary,
    surface = TunerSurface,
    onSurface = TextPrimary,
    error = Danger
)

@Composable
fun GuitarTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
