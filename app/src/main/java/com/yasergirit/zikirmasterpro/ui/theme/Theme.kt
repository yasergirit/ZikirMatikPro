package com.yasergirit.zikirmasterpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = EmeraldLight,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealDark,
    onSecondaryContainer = TealLight,
    tertiary = Gold,
    onTertiary = Color.Black,
    tertiaryContainer = GoldDark,
    onTertiaryContainer = GoldLight,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceDim,
    error = ErrorRed,
    onError = Color.Black,
    outline = DarkOnSurfaceDim.copy(alpha = 0.4f)
)

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = EmeraldLight.copy(alpha = 0.15f),
    onPrimaryContainer = EmeraldDark,
    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = TealLight.copy(alpha = 0.15f),
    onSecondaryContainer = TealDark,
    tertiary = GoldDark,
    onTertiary = Color.White,
    tertiaryContainer = GoldLight.copy(alpha = 0.2f),
    onTertiaryContainer = GoldDark,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceDim,
    error = ErrorRedLight,
    onError = Color.White,
    outline = LightOnSurfaceDim.copy(alpha = 0.3f)
)

@Composable
fun ZikirMasterProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
