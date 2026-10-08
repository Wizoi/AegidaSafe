package com.aegida.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette (product brief). No red anywhere: the "error" slot is brown/amber.
private val Teal = Color(0xFF0F6B73)
private val Sunflower = Color(0xFFF4B400)
private val Brown = Color(0xFF6B3E1B)
private val Cream = Color(0xFFFFF4CC)
private val Ink = Color(0xFF1C1B1A)
private val Night = Color(0xFF151413)
private val LightTeal = Color(0xFF7FCBD2)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    secondary = Sunflower, onSecondary = Ink,
    background = Cream, onBackground = Ink,
    surface = Cream, onSurface = Ink,
    error = Brown, onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = LightTeal, onPrimary = Night,
    secondary = Sunflower, onSecondary = Night,
    background = Night, onBackground = Cream,
    surface = Night, onSurface = Cream,
    error = Sunflower, onError = Night,
)

@Composable
fun AegidaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
