package com.gasprice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),       // Deep green
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFF57F17),      // Amber accent
    onSecondary = Color.Black,
    background = Color(0xFFF9FBF9),
    surface = Color.White,
    error = Color(0xFFB00020)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E7D32),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679)
)

@Composable
fun GasPriceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
