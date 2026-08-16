package com.lasse.speedometer

import androidx.compose.ui.graphics.Color
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.ui.theme.AccentColor
import com.lasse.speedometer.ui.theme.TonalPalette
import com.lasse.speedometer.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonalPaletteTest {

    @Test
    fun `tone 0 is black and tone 100 is white`() {
        val seed = AccentColor.BLUE.seed
        val black = TonalPalette.tone(seed, 0)
        val white = TonalPalette.tone(seed, 100)

        assertEquals(0f, black.red + black.green + black.blue, 0.001f)
        assertEquals(3f, white.red + white.green + white.blue, 0.001f)
    }

    @Test
    fun `lightness rises monotonically across the ramp`() {
        val seed = AccentColor.ORANGE.seed
        val tones = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90)
            .map { TonalPalette.toHsl(TonalPalette.tone(seed, it)).lightness }

        tones.zipWithNext { lower, higher ->
            assertTrue("expected $higher > $lower", higher > lower)
        }
    }

    @Test
    fun `hue survives a round trip through HSL`() {
        AccentColor.entries.forEach { accent ->
            val hsl = TonalPalette.toHsl(accent.seed)
            val rebuilt = TonalPalette.fromHsl(hsl.hue, hsl.saturation, hsl.lightness)
            assertEquals(accent.name, accent.seed.red, rebuilt.red, 0.01f)
            assertEquals(accent.name, accent.seed.green, rebuilt.green, 0.01f)
            assertEquals(accent.name, accent.seed.blue, rebuilt.blue, 0.01f)
        }
    }

    @Test
    fun `a grey seed stays grey at every tone`() {
        val grey = Color(0.5f, 0.5f, 0.5f)
        val toned = TonalPalette.tone(grey, 70)
        assertEquals(toned.red, toned.green, 0.001f)
        assertEquals(toned.green, toned.blue, 0.001f)
    }

    @Test
    fun `neutral roles are far less saturated than the accent`() {
        val seed = AccentColor.PURPLE.seed
        val accent = TonalPalette.toHsl(TonalPalette.tone(seed, 40)).saturation
        val neutral = TonalPalette.toHsl(
            TonalPalette.tone(seed, 40, TonalPalette.NEUTRAL_SATURATION)
        ).saturation

        assertTrue("neutral $neutral should be well under accent $accent", neutral < accent / 4f)
    }
}

class PaceFormatterTest {

    @Test
    fun `ten kilometres per hour is a six minute kilometre`() {
        // 10 km/h is 2.778 m/s.
        assertEquals("6:00 /km", Formatters.pace(2.7778, UnitSystem.METRIC))
    }

    @Test
    fun `standing still has no pace`() {
        assertEquals("—", Formatters.pace(0.0, UnitSystem.METRIC))
    }

    @Test
    fun `a crawl slower than an hour per kilometre has no pace`() {
        assertEquals("—", Formatters.pace(0.2, UnitSystem.METRIC))
    }

    @Test
    fun `imperial pace is quoted per mile`() {
        assertTrue(Formatters.pace(2.7778, UnitSystem.IMPERIAL).endsWith("/mi"))
    }
}
