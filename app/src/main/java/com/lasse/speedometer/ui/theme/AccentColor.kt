package com.lasse.speedometer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.lasse.speedometer.R

/** The accent choices offered when Material You is unavailable or switched off. */
enum class AccentColor(@param:StringRes val labelRes: Int, val seed: Color) {
    GREEN(R.string.accent_green, Color(0xFF00A867)),
    BLUE(R.string.accent_blue, Color(0xFF2F7BFF)),
    TEAL(R.string.accent_teal, Color(0xFF009FA8)),
    PURPLE(R.string.accent_purple, Color(0xFF7C5CFF)),
    PINK(R.string.accent_pink, Color(0xFFE0459B)),
    RED(R.string.accent_red, Color(0xFFE0402F)),
    ORANGE(R.string.accent_orange, Color(0xFFF07316)),
    YELLOW(R.string.accent_yellow, Color(0xFFD5A400)),
}

/**
 * Turns an accent seed into a full Material 3 scheme.
 *
 * The tone numbers are Material's own role assignments, so swapping the seed
 * keeps every contrast pairing — text on containers, outlines on surfaces —
 * where the spec puts it.
 */
object AccentSchemes {

    fun light(accent: AccentColor): ColorScheme {
        val seed = accent.seed
        return lightColorScheme(
            primary = tone(seed, 40),
            onPrimary = tone(seed, 100),
            primaryContainer = tone(seed, 90),
            onPrimaryContainer = tone(seed, 10),
            inversePrimary = tone(seed, 80),
            secondary = secondary(seed, 40),
            onSecondary = secondary(seed, 100),
            secondaryContainer = secondary(seed, 90),
            onSecondaryContainer = secondary(seed, 10),
            tertiary = tertiary(seed, 40),
            onTertiary = tertiary(seed, 100),
            tertiaryContainer = tertiary(seed, 90),
            onTertiaryContainer = tertiary(seed, 10),
            background = neutral(seed, 99),
            onBackground = neutral(seed, 10),
            surface = neutral(seed, 99),
            onSurface = neutral(seed, 10),
            surfaceVariant = neutralVariant(seed, 90),
            onSurfaceVariant = neutralVariant(seed, 30),
            surfaceContainerLowest = neutral(seed, 100),
            surfaceContainerLow = neutral(seed, 96),
            surfaceContainer = neutral(seed, 94),
            surfaceContainerHigh = neutral(seed, 92),
            surfaceContainerHighest = neutral(seed, 90),
            inverseSurface = neutral(seed, 20),
            inverseOnSurface = neutral(seed, 95),
            outline = neutralVariant(seed, 50),
            outlineVariant = neutralVariant(seed, 80),
            error = Palette.ErrorLight,
            onError = Palette.OnErrorLight,
            errorContainer = Palette.ErrorContainerLight,
            onErrorContainer = Palette.OnErrorContainerLight,
        )
    }

    fun dark(accent: AccentColor): ColorScheme {
        val seed = accent.seed
        return darkColorScheme(
            primary = tone(seed, 80),
            onPrimary = tone(seed, 20),
            primaryContainer = tone(seed, 30),
            onPrimaryContainer = tone(seed, 90),
            inversePrimary = tone(seed, 40),
            secondary = secondary(seed, 80),
            onSecondary = secondary(seed, 20),
            secondaryContainer = secondary(seed, 30),
            onSecondaryContainer = secondary(seed, 90),
            tertiary = tertiary(seed, 80),
            onTertiary = tertiary(seed, 20),
            tertiaryContainer = tertiary(seed, 30),
            onTertiaryContainer = tertiary(seed, 90),
            background = neutral(seed, 6),
            onBackground = neutral(seed, 90),
            surface = neutral(seed, 6),
            onSurface = neutral(seed, 90),
            surfaceVariant = neutralVariant(seed, 30),
            onSurfaceVariant = neutralVariant(seed, 80),
            surfaceContainerLowest = neutral(seed, 4),
            surfaceContainerLow = neutral(seed, 10),
            surfaceContainer = neutral(seed, 12),
            surfaceContainerHigh = neutral(seed, 17),
            surfaceContainerHighest = neutral(seed, 22),
            inverseSurface = neutral(seed, 90),
            inverseOnSurface = neutral(seed, 20),
            outline = neutralVariant(seed, 60),
            outlineVariant = neutralVariant(seed, 30),
            error = Palette.ErrorDark,
            onError = Palette.OnErrorDark,
            errorContainer = Palette.ErrorContainerDark,
            onErrorContainer = Palette.OnErrorContainerDark,
        )
    }

    private fun tone(seed: Color, tone: Int) = TonalPalette.tone(seed, tone)

    private fun secondary(seed: Color, tone: Int) =
        TonalPalette.tone(seed, tone, TonalPalette.SECONDARY_SATURATION)

    private fun tertiary(seed: Color, tone: Int) = TonalPalette.tone(
        seed = seed,
        tone = tone,
        saturationScale = TonalPalette.TERTIARY_SATURATION,
        hueShift = TonalPalette.TERTIARY_HUE_SHIFT,
    )

    private fun neutral(seed: Color, tone: Int) =
        TonalPalette.tone(seed, tone, TonalPalette.NEUTRAL_SATURATION)

    private fun neutralVariant(seed: Color, tone: Int) =
        TonalPalette.tone(seed, tone, TonalPalette.NEUTRAL_VARIANT_SATURATION)
}
