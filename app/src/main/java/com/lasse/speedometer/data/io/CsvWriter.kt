package com.lasse.speedometer.data.io

import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.util.Formatters
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The column headings, resolved by the caller so this file holds no strings. */
data class CsvHeadings(
    val date: String,
    val title: String,
    val activity: String,
    val distance: String,
    val duration: String,
    val movingTime: String,
    val avgSpeed: String,
    val maxSpeed: String,
    val ascent: String,
    val descent: String,
    val note: String,
)

/**
 * History as one spreadsheet.
 *
 * GPX is for other tracking apps and the JSON backup is for this one; neither
 * opens in a spreadsheet, which is where someone goes to ask a question this
 * app does not answer — how far did I ride in each of the last five Marches,
 * what did the commute cost me in hours.
 *
 * Numbers are written bare, in the user's own units, with the unit named in
 * the heading: a cell reading "21.34 km" is text to a spreadsheet and cannot
 * be summed.
 */
object CsvWriter {

    private const val SEPARATOR = ","

    fun write(
        writer: Writer,
        trips: List<TripEntity>,
        units: UnitSystem,
        headings: CsvHeadings,
        activityName: (TripEntity) -> String,
    ) {
        val distanceUnit = Formatters.distanceUnit(units)
        val speedUnit = Formatters.speedUnit(units)
        val elevationUnit = Formatters.elevationUnit(units)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        writer.appendLine(
            listOf(
                headings.date,
                headings.title,
                headings.activity,
                "${headings.distance} ($distanceUnit)",
                "${headings.duration} (s)",
                "${headings.movingTime} (s)",
                "${headings.avgSpeed} ($speedUnit)",
                "${headings.maxSpeed} ($speedUnit)",
                "${headings.ascent} ($elevationUnit)",
                "${headings.descent} ($elevationUnit)",
                headings.note,
            ).joinToString(SEPARATOR) { escape(it) }
        )

        trips.forEach { trip ->
            writer.appendLine(
                listOf(
                    stamp.format(Date(trip.startedAt)),
                    trip.title.orEmpty(),
                    activityName(trip),
                    number(Formatters.distanceIn(trip.distanceM, units)),
                    (trip.durationMs / 1000).toString(),
                    (trip.movingTimeMs / 1000).toString(),
                    number(Formatters.speedIn(trip.avgSpeedMps, units)),
                    number(Formatters.speedIn(trip.maxSpeedMps, units)),
                    number(Formatters.elevationIn(trip.ascentM, units)),
                    number(Formatters.elevationIn(trip.descentM, units)),
                    trip.note.orEmpty(),
                ).joinToString(SEPARATOR) { escape(it) }
            )
        }
        writer.flush()
    }

    /** A dot for the decimal point whatever the phone's locale, so it parses. */
    private fun number(value: Double): String = String.format(Locale.US, "%.2f", value)

    /**
     * Quotes a field only where it has to be: a title with a comma in it would
     * otherwise become two columns, and one with a quotation mark in it would
     * break the row after that.
     */
    private fun escape(value: String): String {
        val cleaned = value.replace("\n", " ").replace("\r", " ")
        return if (cleaned.any { it == ',' || it == '"' }) {
            "\"" + cleaned.replace("\"", "\"\"") + "\""
        } else {
            cleaned
        }
    }
}
