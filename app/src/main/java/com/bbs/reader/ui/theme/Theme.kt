package com.bbs.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bbs.reader.data.model.ThemeMode

private val BrandRed = Color(0xFFC62828)
private val BrandDark = Color(0xFF1B1B1B)
private val SoftGray = Color(0xFFF5F5F5)

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    secondary = BrandDark,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = SoftGray,
    onBackground = BrandDark,
    onSurface = BrandDark,
    outline = Color(0xFFE0E0E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEF5350),
    onPrimary = Color.White,
    secondary = Color(0xFFBDBDBD),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC),
    outline = Color(0xFF3A3A3A)
)

@Composable
fun BbsReaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
