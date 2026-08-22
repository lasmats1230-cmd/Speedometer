package com.lasse.speedometer

import com.lasse.speedometer.data.repo.TrackSketch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TrackSketchTest {

    private fun track(count: Int) = List(count) { index ->
        (50.0 + index * 0.0005) to (8.0 + index * 0.0003)
    }

    @Test
    fun `a short track survives a round trip`() {
        val points = track(10)

        val decoded = TrackSketch.decode(TrackSketch.encode(points))

        assertEquals(points.size, decoded.size)
        // Five decimal places is about a metre; a thumbnail cannot show more.
        decoded.forEachIndexed { index, (latitude, longitude) ->
            assertEquals(points[index].first, latitude, 0.00001)
            assertEquals(points[index].second, longitude, 0.00001)
        }
    }

    @Test
    fun `a long track is thinned but keeps its ends`() {
        val points = track(5_000)

        val decoded = TrackSketch.decode(TrackSketch.encode(points))

        assertEquals(TrackSketch.MAX_POINTS, decoded.size)
        assertEquals(points.first().first, decoded.first().first, 0.00001)
        assertEquals(points.last().first, decoded.last().first, 0.00001)
    }

    @Test
    fun `the stored text stays small whatever the ride`() {
        val encoded = TrackSketch.encode(track(20_000))

        // A three-hour ride's sketch is under a kilobyte, which is the point:
        // it rides along in the trip row instead of being read back out of
        // twenty thousand track points.
        assertTrue("Sketch was ${encoded.length} characters", encoded.length < 1024)
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals("", TrackSketch.encode(emptyList()))
        assertTrue(TrackSketch.decode("").isEmpty())
    }

    @Test
    fun `junk decodes to whatever was readable`() {
        val decoded = TrackSketch.decode("50.1,8.2;nonsense;bad,pair,extra;50.3,8.4")

        assertEquals(2, decoded.size)
    }

    @Test
    fun `a limit too small to interpolate does not divide by zero`() {
        // (size - 1) / (limit - 1) is infinity at a limit of one, and every
        // index then lands on the same point.
        assertEquals(1, TrackSketch.decimate(track(100), limit = 1).size)
        assertTrue(TrackSketch.decimate(track(100), limit = 0).isEmpty())
    }

    @Test
    fun `a comma decimal locale still writes dots`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val encoded = TrackSketch.encode(track(3))
            assertTrue("Encoded as: $encoded", encoded.startsWith("50.00000,8.00000"))
            assertEquals(3, TrackSketch.decode(encoded).size)
        } finally {
            Locale.setDefault(original)
        }
    }
}
