package com.lasse.speedometer.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Builds Material-style tonal ramps from a seed colour.
 *
 * Material You derives its palettes in the HCT space, which needs a CAM16
 * implementation. This works in HSL instead: it holds hue and saturation and
 * varies lightness across the same tone stops. The result is not bit-identical
 * to the system's own palettes, but it is coherent, keeps contrast pairings
 * intact, and costs no dependency — and when the device can do the real thing,
 * dynamic colour is used instead of this.
 */
object TonalPalette {

    /** Hue in degrees, saturation and lightness in 0..1. */
    data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

    fun toHsl(color: Color): Hsl {
        val r = color.red
        val g = color.green
        val b = color.blue
        val maxComponent = max(r, max(g, b))
        val minComponent = min(r, min(g, b))
        val delta = maxComponent - minComponent
        val lightness = (maxComponent + minComponent) / 2f

        if (delta == 0f) return Hsl(0f, 0f, lightness)

        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue = when (maxComponent) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return Hsl(hue = (hue + 360f) % 360f, saturation = saturation.coerceIn(0f, 1f), lightness = lightness)
    }

    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs((h % 2f) - 1f))
        val (r1, g1, b1) = when {
            h < 1f -> Triple(c, x, 0f)
            h < 2f -> Triple(x, c, 0f)
            h < 3f -> Triple(0f, c, x)
            h < 4f -> Triple(0f, x, c)
            h < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
        )
    }

    /**
     * One stop on a tonal ramp. [tone] runs 0 (black) to 100 (white), matching
     * Material's tone numbering.
     */
    fun tone(seed: Color, tone: Int, saturationScale: Float = 1f, hueShift: Float = 0f): Color {
        val hsl = toHsl(seed)
        return fromHsl(
            hue = hsl.hue + hueShift,
            saturation = hsl.saturation * saturationScale,
            lightness = tone.coerceIn(0, 100) / 100f,
        )
    }

    /** Chroma multipliers matching Material's accent and neutral roles. */
    const val SECONDARY_SATURATION = 0.36f
    const val TERTIARY_SATURATION = 0.60f
    const val TERTIARY_HUE_SHIFT = 60f
    const val NEUTRAL_SATURATION = 0.05f
    const val NEUTRAL_VARIANT_SATURATION = 0.12f
}
