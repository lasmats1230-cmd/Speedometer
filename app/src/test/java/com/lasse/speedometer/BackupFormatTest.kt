package com.lasse.speedometer

import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.data.io.BackupFormat
import com.lasse.speedometer.data.io.JsonParseException
import com.lasse.speedometer.data.io.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.util.Locale

/**
 * A backup is only worth having if it reads back, so the file is written and
 * parsed here rather than trusted.
 */
class BackupFormatTest {

    private val trip = TripEntity(
        id = 7,
        startedAt = 1_700_000_000_000,
        endedAt = 1_700_003_600_000,
        durationMs = 3_600_000,
        movingTimeMs = 3_000_000,
        distanceM = 21_345.678,
        avgSpeedMps = 5.93,
        maxSpeedMps = 12.5,
        ascentM = 312.0,
        descentM = 295.5,
        minAltitudeM = 88.0,
        maxAltitudeM = 401.0,
        title = "Ride to the \"lake\"",
        tourId = 3,
        syncedToHealth = true,
        activity = "RUN",
        note = "Windy\nsecond line",
        sketch = "50.10000,8.20000;50.10500,8.20500",
    )

    private val points = listOf(
        TrackPointEntity(
            id = 1, tripId = 7, timestamp = 1_700_000_000_000, latitude = 50.1234567,
            longitude = 8.7654321, altitudeM = 120.5, speedMps = 4.5f, accuracyM = 6f,
            cumulativeDistanceM = 0.0,
        ),
        TrackPointEntity(
            id = 2, tripId = 7, timestamp = 1_700_000_010_000, latitude = 50.1235567,
            longitude = 8.7655321, altitudeM = null, speedMps = 5.5f, accuracyM = 4f,
            cumulativeDistanceM = 45.25,
        ),
    )

    private fun write(): String {
        val writer = StringWriter()
        BackupFormat.writeHeader(writer, exportedAt = 1_700_000_000_000)
        writer.append(",\"tours\":[")
        BackupFormat.writeTour(writer, TourEntity(id = 3, name = "Alps", createdAt = 1_000))
        writer.append("],\"trips\":[")
        BackupFormat.writeTrip(
            writer = writer,
            trip = trip,
            points = points,
            photoUris = listOf("content://media/1", "content://media/2"),
        )
        writer.append("],\"routes\":[")
        BackupFormat.writeRoute(
            writer,
            RouteEntity(
                id = 2,
                name = "Commute",
                importedAt = 2_000,
                distanceM = 8_400.0,
                ascentM = 40.0,
                encodedPoints = "50.1,8.1,100;50.2,8.2,",
            ),
        )
        writer.append("],\"waypoints\":[")
        BackupFormat.writeWaypoint(
            writer,
            WaypointEntity(
                id = 9,
                label = "Water tap",
                latitude = 50.5,
                longitude = 8.5,
                colorArgb = -16711936,
                createdAt = 3_000,
                note = null,
            ),
        )
        writer.append("]}")
        return writer.toString()
    }

    @Test
    fun `a written backup parses back into the same trip`() {
        val contents = BackupFormat.parse(write())

        assertEquals(1, contents.trips.size)
        val restored = contents.trips.first().trip
        assertEquals(trip.startedAt, restored.startedAt)
        assertEquals(trip.endedAt, restored.endedAt)
        assertEquals(trip.durationMs, restored.durationMs)
        assertEquals(trip.movingTimeMs, restored.movingTimeMs)
        assertEquals(trip.distanceM, restored.distanceM, 0.001)
        assertEquals(trip.maxSpeedMps, restored.maxSpeedMps, 0.001)
        assertEquals(trip.ascentM, restored.ascentM, 0.001)
        assertEquals(trip.title, restored.title)
        assertEquals(trip.note, restored.note)
        assertEquals(trip.activity, restored.activity)
        assertEquals(trip.tourId, restored.tourId)
        assertTrue(restored.syncedToHealth)
    }

    @Test
    fun `track points survive with their altitude gaps intact`() {
        val restored = BackupFormat.parse(write()).trips.first().points

        assertEquals(2, restored.size)
        assertEquals(50.1234567, restored[0].latitude, 0.0000001)
        assertEquals(8.7654321, restored[0].longitude, 0.0000001)
        assertEquals(120.5, restored[0].altitudeM!!, 0.001)
        assertNull(restored[1].altitudeM)
        assertEquals(45.25, restored[1].cumulativeDistanceM, 0.001)
        assertEquals(5.5f, restored[1].speedMps, 0.001f)
    }

    @Test
    fun `photos and the stored sketch come back with their trip`() {
        val restored = BackupFormat.parse(write()).trips.single()

        assertEquals(listOf("content://media/1", "content://media/2"), restored.photoUris)
        assertEquals(trip.sketch, restored.trip.sketch)
    }

    @Test
    fun `tours routes and waypoints come back too`() {
        val contents = BackupFormat.parse(write())

        assertEquals("Alps", contents.tours.single().name)
        assertEquals(3L, contents.tours.single().id)
        assertEquals("Commute", contents.routes.single().name)
        assertEquals("50.1,8.1,100;50.2,8.2,", contents.routes.single().encodedPoints)
        assertEquals("Water tap", contents.waypoints.single().label)
        assertEquals(-16711936, contents.waypoints.single().colorArgb)
    }

    @Test
    fun `a comma decimal locale still writes machine-readable numbers`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val text = write()
            assertTrue(text.contains("21345.678"))
            assertEquals(21_345.678, BackupFormat.parse(text).trips.first().trip.distanceM, 0.001)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test(expected = JsonParseException::class)
    fun `some other json file is refused`() {
        BackupFormat.parse("""{"format":"something-else","trips":[]}""")
    }

    @Test(expected = JsonParseException::class)
    fun `truncated json is refused rather than half-restored`() {
        BackupFormat.parse(write().dropLast(20))
    }

    @Test
    fun `strings with quotes newlines and unicode round-trip`() {
        val awkward = "tab\there \"quoted\" back\\slash ümlaut → ende\nnew line"
        val parsed = MiniJson.parse("""{"value":${MiniJson.quote(awkward)}}""")

        @Suppress("UNCHECKED_CAST")
        assertEquals(awkward, (parsed as Map<String, Any?>)["value"])
    }

    @Test
    fun `the parser handles the shapes json allows`() {
        val parsed = MiniJson.parse(
            """{"a":[1,-2.5,1e3,true,false,null,{"b":[]}],"c":{},"d":"é"}"""
        )

        @Suppress("UNCHECKED_CAST")
        val root = parsed as Map<String, Any?>
        val list = root["a"] as List<*>
        assertEquals(1.0, list[0] as Double, 0.0)
        assertEquals(-2.5, list[1] as Double, 0.0)
        assertEquals(1000.0, list[2] as Double, 0.0)
        assertEquals(true, list[3])
        assertEquals(false, list[4])
        assertNull(list[5])
        assertEquals("é", root["d"])
    }
}
