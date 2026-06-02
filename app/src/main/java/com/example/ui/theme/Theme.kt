package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import com.example.model.WaveTheme

private val DarkNeonPalette = darkColorScheme(
    primary = DarkNeonPrimary,
    onPrimary = Color.Black,
    secondary = DarkNeonSecondary,
    onSecondary = Color.White,
    background = DarkNeonBackground,
    onBackground = Color.White,
    surface = DarkNeonSurface,
    onSurface = Color.White,
    surfaceVariant = DarkNeonAccent
)

private val CyberBluePalette = darkColorScheme(
    primary = CyberBluePrimary,
    onPrimary = Color.Black,
    secondary = CyberBlueSecondary,
    onSecondary = Color.White,
    background = CyberBlueBackground,
    onBackground = Color.White,
    surface = CyberBlueSurface,
    onSurface = Color.White,
    surfaceVariant = CyberBlueAccent
)

private val GoldProPalette = darkColorScheme(
    primary = GoldProPrimary,
    onPrimary = Color.Black,
    secondary = GoldProSecondary,
    onSecondary = Color.White,
    background = GoldProBackground,
    onBackground = Color.White,
    surface = GoldProSurface,
    onSurface = Color.White,
    surfaceVariant = GoldProAccent
)

private val RedStudioPalette = darkColorScheme(
    primary = RedStudioPrimary,
    onPrimary = Color.White,
    secondary = RedStudioSecondary,
    onSecondary = Color.White,
    background = RedStudioBackground,
    onBackground = Color.White,
    surface = RedStudioSurface,
    onSurface = Color.White,
    surfaceVariant = RedStudioAccent
)

private val PurpleGalaxyPalette = darkColorScheme(
    primary = PurpleGalaxyPrimary,
    onPrimary = Color.Black,
    secondary = PurpleGalaxySecondary,
    onSecondary = Color.White,
    background = PurpleGalaxyBackground,
    onBackground = Color.White,
    surface = PurpleGalaxySurface,
    onSurface = Color.White,
    surfaceVariant = PurpleGalaxyAccent
)

private val EmeraldProPalette = darkColorScheme(
    primary = EmeraldProPrimary,
    onPrimary = Color.Black,
    secondary = EmeraldProSecondary,
    onSecondary = Color.White,
    background = EmeraldProBackground,
    onBackground = Color.White,
    surface = EmeraldProSurface,
    onSurface = Color.White,
    surfaceVariant = EmeraldProAccent
)

@Composable
fun StudioTheme(
    theme: WaveTheme = WaveTheme.DARK_NEON,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        WaveTheme.DARK_NEON -> DarkNeonPalette
        WaveTheme.CYBER_BLUE -> CyberBluePalette
        WaveTheme.GOLD_PRO -> GoldProPalette
        WaveTheme.RED_STUDIO -> RedStudioPalette
        WaveTheme.PURPLE_GALAXY -> PurpleGalaxyPalette
        WaveTheme.EMERALD_PRO -> EmeraldProPalette
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.statusBarColor = colorScheme.background.toArgb()
            window?.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Fallback theme for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    StudioTheme(theme = WaveTheme.DARK_NEON, content = content)
}
