package com.lasse.speedometer

import android.graphics.Bitmap
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.io.TripCardRenderer
import com.lasse.speedometer.data.io.TripCardText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared card is drawn with plain Android graphics, so it can be drawn
 * here and looked at — a card that comes out blank is the sort of thing that
 * is only noticed by the person you sent it to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TripCardRenderTest {

    private val text = TripCardText(
        headline = "21.34 km",
        subtitle = "Ride  ·  14 Apr 2026, 08:12",
        stats = listOf(
            "Time" to "01:12:33",
            "Avg" to "17.6 km/h",
            "Max" to "41.5 km/h",
            "Ascent" to "312 m",
        ),
        footer = "Recorded with Speedometer",
    )

    private fun track(count: Int) = List(count) { index ->
        TrackPointEntity(
            id = index.toLong(),
            tripId = 1,
            timestamp = index * 1000L,
            latitude = 50.0 + index * 0.001,
            longitude = 8.0 + index * 0.0005,
            altitudeM = null,
            speedMps = 5f,
            accuracyM = 5f,
            cumulativeDistanceM = index * 100.0,
        )
    }

    @Test
    fun `the card is drawn at a size a chat window can show`() {
        val bitmap = TripCardRenderer.render(track(20), text)

        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun `the route is actually drawn on it`() {
        val blank = TripCardRenderer.render(emptyList(), text)
        val drawn = TripCardRenderer.render(track(40), text)

        // Somewhere inside the map panel the two must differ, or the track
        // never reached the canvas.
        var different = 0
        for (x in 100 until 980 step 20) {
            for (y in 150 until 700 step 20) {
                if (blank.getPixel(x, y) != drawn.getPixel(x, y)) different++
            }
        }
        assertTrue("The track left no marks on the card", different > 0)
    }

    @Test
    fun `a trip with no usable track still produces a card`() {
        val bitmap = TripCardRenderer.render(emptyList(), text)

        assertEquals(1080, bitmap.width)
        // The background is painted, so no pixel is left transparent.
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(10, 10)) == 255)
    }
}
