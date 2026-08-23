package com.lasse.speedometer

import androidx.test.core.app.ApplicationProvider
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.io.TripExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A trip's title becomes a file name, and titles are whatever the user typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExportNamingTest {

    private val exporter = TripExporter(ApplicationProvider.getApplicationContext())

    private fun trip(title: String?) = TripEntity(
        startedAt = 1_700_000_000_000,
        endedAt = 1_700_003_600_000,
        durationMs = 3_600_000,
        movingTimeMs = 3_600_000,
        distanceM = 1000.0,
        avgSpeedMps = 5.0,
        maxSpeedMps = 7.0,
        ascentM = 0.0,
        descentM = 0.0,
        minAltitudeM = null,
        maxAltitudeM = null,
        title = title,
    )

    @Test
    fun `an ordinary title keeps its words`() {
        assertEquals("Morning ride.gpx", exporter.fileNameFor(trip("Morning ride")))
    }

    @Test
    fun `punctuation is dropped rather than written into the name`() {
        assertEquals("Ride to the lake.gpx", exporter.fileNameFor(trip("Ride to the \"lake\"!")))
    }

    @Test
    fun `a title that sanitises away falls back to the date`() {
        // "🚴🚴" and "!!!" both leave nothing behind, and a file called
        // ".gpx" is hidden — the user would never find it again.
        listOf("🚴🚴", "!!!", "   ", "").forEach { title ->
            val name = exporter.fileNameFor(trip(title))
            assertTrue("Got \"$name\" for \"$title\"", name.startsWith("Trip_"))
            assertTrue(name.endsWith(".gpx"))
        }
    }

    @Test
    fun `no title at all is named after the date`() {
        assertTrue(exporter.fileNameFor(trip(null)).startsWith("Trip_"))
    }
}
