package com.lasse.speedometer.data.io

import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes GPX 1.1 with the Garmin TrackPointExtension, which is what Strava,
 * Komoot and friends expect for speed data.
 */
object GpxWriter {

    private val timestampFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun write(writer: Writer, trip: TripEntity, points: List<TrackPointEntity>, name: String) {
        val stamp = timestampFormat
        writer.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer.appendLine(
            """<gpx version="1.1" creator="Speedometer" """ +
                """xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">"""
        )
        writer.appendLine("  <metadata>")
        writer.appendLine("    <name>${escape(name)}</name>")
        writer.appendLine("    <time>${stamp.format(Date(trip.startedAt))}</time>")
        writer.appendLine("  </metadata>")
        writer.appendLine("  <trk>")
        writer.appendLine("    <name>${escape(name)}</name>")
        writer.appendLine("    <trkseg>")

        points.forEach { point ->
            writer.append("      <trkpt lat=\"")
            writer.append(coord(point.latitude))
            writer.append("\" lon=\"")
            writer.append(coord(point.longitude))
            writer.appendLine("\">")
            point.altitudeM?.let {
                writer.appendLine("        <ele>${String.format(Locale.US, "%.1f", it)}</ele>")
            }
            writer.appendLine("        <time>${stamp.format(Date(point.timestamp))}</time>")
            writer.appendLine("        <extensions>")
            writer.appendLine("          <gpxtpx:TrackPointExtension>")
            writer.appendLine(
                "            <gpxtpx:speed>${String.format(Locale.US, "%.2f", point.speedMps)}</gpxtpx:speed>"
            )
            writer.appendLine("          </gpxtpx:TrackPointExtension>")
            writer.appendLine("        </extensions>")
            writer.appendLine("      </trkpt>")
        }

        writer.appendLine("    </trkseg>")
        writer.appendLine("  </trk>")
        writer.appendLine("</gpx>")
        writer.flush()
    }

    private fun coord(value: Double) = String.format(Locale.US, "%.7f", value)

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
