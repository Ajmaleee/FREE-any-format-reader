package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light Palette (Warm, restrained light theme)
private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightAccentSubtle,
    onPrimaryContainer = LightAccentPressed,
    secondary = LightTextSecondary,
    onSecondary = Color.White,
    background = LightBackground, // #FAF9F6
    onBackground = LightTextPrimary, // #20201E
    surface = LightSurface, // #FFFFFF
    onSurface = LightTextPrimary, // #20201E
    surfaceVariant = LightAccentSubtle, // #EAF0F3
    onSurfaceVariant = LightTextSecondary, // #777670
    outline = LightSeparator, // #E8E6E0
    outlineVariant = LightSeparator,
    error = ErrorCrimson,
    onError = Color.White
)

// Dark Palette (Carefully designed dark theme, non-harsh background)
private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent, // Restrained, accessible blue
    onPrimary = DarkBackground,
    primaryContainer = DarkAccentSubtle, // #1E2830
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkTextSecondary, // #AAA9A2
    onSecondary = Color.White,
    background = DarkBackground, // #151513
    onBackground = DarkTextPrimary, // #F0EFEA
    surface = DarkSurface, // #1D1D1A
    onSurface = DarkTextPrimary, // #F0EFEA
    surfaceVariant = DarkAccentSubtle,
    onSurfaceVariant = DarkTextSecondary, // #AAA9A2
    outline = DarkSeparator, // #33332F
    outlineVariant = DarkSeparator,
    error = ErrorCrimson,
    onError = Color.White
)

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun UniversalReaderTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
