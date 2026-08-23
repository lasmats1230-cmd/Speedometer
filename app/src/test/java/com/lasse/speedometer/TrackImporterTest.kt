package com.lasse.speedometer

import com.lasse.speedometer.data.io.ImportedPoint
import com.lasse.speedometer.data.io.RouteParser
import com.lasse.speedometer.data.io.TrackImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackImporterTest {

    /** Roughly 100 m apart along a line of longitude. */
    private fun leg(index: Int, second: Long, altitude: Double? = null) = ImportedPoint(
        timestamp = second * 1000,
        latitude = 50.0 + index * 0.0008993,
        longitude = 8.0,
        altitudeM = altitude,
        speedMps = null,
    )

    @Test
    fun `distance and time come out of the track, not the file's claims`() {
        val points = List(11) { index -> leg(index, index * 10L) }

        val imported = TrackImporter.build(points)

        assertNotNull(imported)
        val summary = imported!!.summary
        assertEquals(1000.0, summary.distanceM, 15.0)
        assertEquals(100_000L, summary.durationMs)
        assertEquals(10.0, summary.avgSpeedMps, 0.5)
        assertEquals(11, imported.points.size)
        assertEquals(0.0, imported.points.first().cumulativeDistanceM, 0.001)
        assertEquals(summary.distanceM, imported.points.last().cumulativeDistanceM, 0.001)
    }

    @Test
    fun `a long stop is not counted as moving time`() {
        val points = listOf(
            leg(0, 0),
            leg(1, 10),
            // Twenty minutes parked, then off again.
            leg(1, 1210),
            leg(2, 1220),
        )

        val imported = TrackImporter.build(points)!!

        assertEquals(1_220_000L, imported.summary.durationMs)
        assertEquals(20_000L, imported.summary.movingTimeMs)
    }

    @Test
    fun `an impossible jump is left out of the distance`() {
        val points = listOf(
            leg(0, 0),
            leg(1, 10),
            // A fix on another continent one second later.
            ImportedPoint(11_000, 40.0, -74.0, null, null),
            leg(2, 20),
        )

        val imported = TrackImporter.build(points)!!

        assertTrue(
            "A 6000 km jump must not enter the total",
            imported.summary.distanceM < 500.0,
        )
        assertTrue(imported.summary.maxSpeedMps < 150.0)
    }

    @Test
    fun `climbs are counted past the noise threshold and dips are not`() {
        val points = listOf(
            leg(0, 0, altitude = 100.0),
            leg(1, 10, altitude = 101.0),
            leg(2, 20, altitude = 120.0),
            leg(3, 30, altitude = 105.0),
        )

        val imported = TrackImporter.build(points)!!

        assertEquals(20.0, imported.summary.ascentM, 0.001)
        assertEquals(15.0, imported.summary.descentM, 0.001)
        assertEquals(100.0, imported.summary.minAltitudeM!!, 0.001)
        assertEquals(120.0, imported.summary.maxAltitudeM!!, 0.001)
    }

    @Test
    fun `points out of order are sorted before anything is measured`() {
        val points = listOf(leg(2, 20), leg(0, 0), leg(1, 10))

        val imported = TrackImporter.build(points)!!

        assertEquals(0L, imported.summary.startedAt)
        assertEquals(20_000L, imported.summary.endedAt)
        assertEquals(200.0, imported.summary.distanceM, 5.0)
    }

    /**
     * Straight out of a ride that came back reading 134 km/h on a bicycle.
     *
     * The receiver stuck on one position for twenty-two seconds, writing it
     * out once a second, and then caught up 148 m down the road. Timed from
     * the last repeat that is 37 m/s; timed from when the rider was really
     * there it is the 5.7 m/s it was.
     */
    @Test
    fun `a receiver stuck on one position does not invent a sprint`() {
        val stuckLat = 53.227162
        val stuckLon = 10.386150
        // The seconds the repeats actually landed on, then the fix that moved.
        val repeats = listOf(0L, 1, 2, 3, 6, 7, 11, 12, 16, 17, 21, 22)
        val points = repeats.map { second ->
            ImportedPoint(second * 1000, stuckLat, stuckLon, 66.7, null)
        } + ImportedPoint(26_000, 53.226685, 10.388232, 67.4, null)

        val imported = TrackImporter.build(points)!!

        assertTrue(
            "148 m of road must not read as a sprint: " +
                "${imported.summary.maxSpeedMps * 3.6} km/h",
            imported.summary.maxSpeedMps < 11.0,
        )
        assertEquals(5.7, imported.summary.maxSpeedMps, 0.6)
    }

    @Test
    fun `a position written out again is a standstill, not a step`() {
        val points = listOf(
            leg(0, 0),
            leg(0, 1),
            leg(0, 2),
        )

        val imported = TrackImporter.build(points)!!

        assertEquals(0.0, imported.summary.distanceM, 0.001)
        assertEquals(0.0, imported.summary.maxSpeedMps, 0.001)
        assertEquals(0L, imported.summary.movingTimeMs)
    }

    @Test
    fun `a file with a single fix is not a trip`() {
        assertNull(TrackImporter.build(listOf(leg(0, 0))))
        assertNull(TrackImporter.build(emptyList()))
    }

    @Test
    fun `timestamps are read in each shape these files use`() {
        val plain = RouteParser.parseTimestamp("2026-04-15T09:12:30Z")
        val fractional = RouteParser.parseTimestamp("2026-04-15T09:12:30.500Z")
        val offset = RouteParser.parseTimestamp("2026-04-15T11:12:30+02:00")

        assertNotNull(plain)
        assertEquals(plain, offset)
        assertEquals(plain!! + 500, fractional)
        assertNull(RouteParser.parseTimestamp("not a time"))
    }
}
