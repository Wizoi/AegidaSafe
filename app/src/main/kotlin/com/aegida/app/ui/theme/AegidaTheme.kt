package com.aegida.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette (product brief). No red anywhere: the "error" slot is brown/amber.
val Teal = Color(0xFF0F6B73)
val Sunflower = Color(0xFFF4B400)
val Brown = Color(0xFF6B3E1B)
val Cream = Color(0xFFFFF4CC)
private val Ink = Color(0xFF1C1B1A)
private val Night = Color(0xFF151413)
private val LightTeal = Color(0xFF7FCBD2)

/** Handled things are green; things that still need her are amber. Never red. */
val GoodGreen = Color(0xFF2E7D32)
val GoodGreenOnDark = Color(0xFF7FD18B)
val Amber = Color(0xFFB57F00)
val AmberOnDark = Color(0xFFF4B400)

private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    secondary = Sunflower, onSecondary = Ink,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEDE6C8), onSurfaceVariant = Color(0xFF3E4C59),
    outline = Teal,
    error = Brown, onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = LightTeal, onPrimary = Night,
    secondary = Sunflower, onSecondary = Night,
    background = Night, onBackground = Cream,
    surface = Color(0xFF211F1D), onSurface = Cream,
    surfaceVariant = Color(0xFF2E2B27), onSurfaceVariant = Color(0xFFD9D2B4),
    outline = LightTeal,
    error = Sunflower, onError = Night,
)

/** Large and calm for an 80+ reader. The phone's own font size setting scales these further. */
private val AegidaTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 19.sp, lineHeight = 27.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun AegidaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AegidaTypography, content = content)
}

@Composable
fun goodColor(): Color = if (MaterialTheme.colorScheme.background == Night) GoodGreenOnDark else GoodGreen

@Composable
fun amberColor(): Color = if (MaterialTheme.colorScheme.background == Night) AmberOnDark else Amber
