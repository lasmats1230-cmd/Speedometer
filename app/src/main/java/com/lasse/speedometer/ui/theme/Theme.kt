package com.lasse.speedometer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lasse.speedometer.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Palette.PrimaryLight,
    onPrimary = Palette.OnPrimaryLight,
    primaryContainer = Palette.PrimaryContainerLight,
    onPrimaryContainer = Palette.OnPrimaryContainerLight,
    secondary = Palette.SecondaryLight,
    onSecondary = Palette.OnSecondaryLight,
    secondaryContainer = Palette.SecondaryContainerLight,
    onSecondaryContainer = Palette.OnSecondaryContainerLight,
    tertiary = Palette.TertiaryLight,
    onTertiary = Palette.OnTertiaryLight,
    tertiaryContainer = Palette.TertiaryContainerLight,
    onTertiaryContainer = Palette.OnTertiaryContainerLight,
    error = Palette.ErrorLight,
    onError = Palette.OnErrorLight,
    errorContainer = Palette.ErrorContainerLight,
    onErrorContainer = Palette.OnErrorContainerLight,
    background = Palette.BackgroundLight,
    onBackground = Palette.OnBackgroundLight,
    surface = Palette.SurfaceLight,
    onSurface = Palette.OnSurfaceLight,
    surfaceVariant = Palette.SurfaceVariantLight,
    onSurfaceVariant = Palette.OnSurfaceVariantLight,
    outline = Palette.OutlineLight,
    surfaceContainer = Palette.SurfaceContainerLight,
    surfaceContainerHigh = Palette.SurfaceContainerHighLight,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.PrimaryDark,
    onPrimary = Palette.OnPrimaryDark,
    primaryContainer = Palette.PrimaryContainerDark,
    onPrimaryContainer = Palette.OnPrimaryContainerDark,
    secondary = Palette.SecondaryDark,
    onSecondary = Palette.OnSecondaryDark,
    secondaryContainer = Palette.SecondaryContainerDark,
    onSecondaryContainer = Palette.OnSecondaryContainerDark,
    tertiary = Palette.TertiaryDark,
    onTertiary = Palette.OnTertiaryDark,
    tertiaryContainer = Palette.TertiaryContainerDark,
    onTertiaryContainer = Palette.OnTertiaryContainerDark,
    error = Palette.ErrorDark,
    onError = Palette.OnErrorDark,
    errorContainer = Palette.ErrorContainerDark,
    onErrorContainer = Palette.OnErrorContainerDark,
    background = Palette.BackgroundDark,
    onBackground = Palette.OnBackgroundDark,
    surface = Palette.SurfaceDark,
    onSurface = Palette.OnSurfaceDark,
    surfaceVariant = Palette.SurfaceVariantDark,
    onSurfaceVariant = Palette.OnSurfaceVariantDark,
    outline = Palette.OutlineDark,
    surfaceContainer = Palette.SurfaceContainerDark,
    surfaceContainerHigh = Palette.SurfaceContainerHighDark,
)

@Composable
fun SpeedometerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpeedometerTypography,
        shapes = SpeedometerShapes,
        content = content,
    )
}
