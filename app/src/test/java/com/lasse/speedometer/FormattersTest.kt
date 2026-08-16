package com.lasse.speedometer

import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `metres per second convert to kilometres per hour`() {
        assertEquals(36.0, Formatters.speedIn(10.0, UnitSystem.METRIC), 0.001)
    }

    @Test
    fun `metres per second convert to miles per hour`() {
        assertEquals(22.369, Formatters.speedIn(10.0, UnitSystem.IMPERIAL), 0.001)
    }

    @Test
    fun `big speed rounds to a whole number`() {
        // 0.95 m/s is 3.42 km/h, which the live readout shows as "3".
        assertEquals("3", Formatters.bigSpeed(0.95, UnitSystem.METRIC))
    }

    @Test
    fun `negative speed never reaches the readout`() {
        assertEquals("0", Formatters.bigSpeed(-4.0, UnitSystem.METRIC))
    }

    @Test
    fun `duration omits hours below one hour`() {
        assertEquals("00:03", Formatters.duration(3_000L))
        assertEquals("40:19", Formatters.duration((40 * 60 + 19) * 1000L))
    }

    @Test
    fun `duration includes hours past the hour mark`() {
        val ninetyOneMinutes = (91 * 60 + 29) * 1000L
        assertEquals("01:31:29", Formatters.duration(ninetyOneMinutes))
    }

    @Test
    fun `long duration always shows hours`() {
        assertEquals("00:00:03", Formatters.durationLong(3_000L))
    }
}
