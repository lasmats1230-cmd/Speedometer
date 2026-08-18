package com.lasse.speedometer

import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.io.CsvHeadings
import com.lasse.speedometer.data.io.CsvWriter
import com.lasse.speedometer.data.prefs.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.util.Locale

class CsvWriterTest {

    private val headings = CsvHeadings(
        date = "Started",
        title = "Trip name",
        activity = "Activity",
        distance = "Distance",
        duration = "Time",
        movingTime = "Moving time",
        avgSpeed = "Avg",
        maxSpeed = "Max",
        ascent = "Ascent",
        descent = "Descent",
        note = "Note",
    )

    private fun trip(
        title: String? = "Morning ride",
        note: String? = null,
        activity: ActivityType = ActivityType.RIDE,
    ) = TripEntity(
        id = 1,
        startedAt = 1_700_000_000_000,
        endedAt = 1_700_003_600_000,
        durationMs = 3_600_000,
        movingTimeMs = 3_000_000,
        distanceM = 21_340.0,
        avgSpeedMps = 7.1,
        maxSpeedMps = 11.5,
        ascentM = 312.0,
        descentM = 300.0,
        minAltitudeM = null,
        maxAltitudeM = null,
        title = title,
        activity = activity.name,
        note = note,
    )

    private fun write(
        trips: List<TripEntity>,
        units: UnitSystem = UnitSystem.METRIC,
    ): String = StringWriter().also { writer ->
        CsvWriter.write(writer, trips, units, headings) { it.activityType.name }
    }.toString()

    @Test
    fun `the header names every column with its unit`() {
        val header = write(emptyList()).lines().first()

        assertEquals(
            "Started,Trip name,Activity,Distance (km),Time (s),Moving time (s)," +
                "Avg (km/h),Max (km/h),Ascent (m),Descent (m),Note",
            header,
        )
    }

    @Test
    fun `a trip is one row of bare numbers a spreadsheet can add up`() {
        val row = write(listOf(trip())).lines()[1]

        assertEquals(
            "2023-11-14 22:13,Morning ride,RIDE,21.34,3600,3000,25.56,41.40,312.00,300.00,",
            row,
        )
    }

    @Test
    fun `imperial units convert, and the header says so`() {
        val lines = write(listOf(trip()), UnitSystem.IMPERIAL).lines()

        assertTrue(lines[0].contains("Distance (mi)"))
        assertTrue(lines[0].contains("Avg (mph)"))
        // 21.34 km is 13.26 miles.
        assertTrue("Distance was not converted: ${lines[1]}", lines[1].contains(",13.26,"))
    }

    @Test
    fun `a comma in a title does not become another column`() {
        val row = write(listOf(trip(title = "Ride to Bad Homburg, then back"))).lines()[1]

        assertTrue(row.contains("\"Ride to Bad Homburg, then back\""))
        // Eleven columns still, whatever is in them.
        assertEquals(11, splitCsv(row).size)
    }

    @Test
    fun `quotation marks and newlines survive the trip`() {
        val row = write(listOf(trip(title = "The \"long\" way", note = "Windy\nand cold")))
            .lines()[1]

        assertTrue(row.contains("\"The \"\"long\"\" way\""))
        assertTrue("A newline inside a field would break the row", !row.contains("\n"))
        assertTrue(row.contains("Windy and cold"))
    }

    @Test
    fun `a comma decimal locale still writes dots`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertTrue(write(listOf(trip())).lines()[1].contains(",21.34,"))
        } finally {
            Locale.setDefault(original)
        }
    }

    /** A minimal CSV field splitter, honouring quotes, for the column count. */
    private fun splitCsv(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }

                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    fields += current.toString()
                    current.clear()
                }

                else -> current.append(character)
            }
            index++
        }
        fields += current.toString()
        return fields
    }
}
