package com.lasse.speedometer.data.io

import android.location.Location
import android.util.Xml
import com.lasse.speedometer.data.db.RouteEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    /**
     * When the fix was taken, where the file says. Null for a planned route,
     * which is the difference between a route to follow and a trip that
     * happened.
     */
    val timeMs: Long? = null,
    val speedMps: Float? = null,
)

/**
 * Reads GPX and TCX into a common list of points.
 *
 * Both formats nest coordinates differently but neither needs a full XML
 * object model, so this walks the pull parser and picks out what it needs.
 */
object RouteParser {

    private const val ELEVATION_THRESHOLD_M = 3.0

    /** Element names that carry a latitude/longitude pair in either format. */
    private val GPX_POINT_TAGS = setOf("trkpt", "rtept", "wpt")

    fun parse(input: InputStream, fileName: String): RouteEntity {
        val points = readPoints(input)
        require(points.size >= 2) { "Route has too few points" }

        var distance = 0.0
        val results = FloatArray(1)
        for (i in 1 until points.size) {
            Location.distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
                results,
            )
            distance += results[0]
        }

        var ascent = 0.0
        var reference: Double? = null
        points.forEach { point ->
            val elevation = point.altitudeM ?: return@forEach
            val previous = reference
            if (previous == null) {
                reference = elevation
                return@forEach
            }
            val delta = elevation - previous
            if (abs(delta) >= ELEVATION_THRESHOLD_M) {
                if (delta > 0) ascent += delta
                reference = elevation
            }
        }

        return RouteEntity(
            name = fileName.substringBeforeLast('.').ifBlank { "Route" },
            importedAt = System.currentTimeMillis(),
            distanceM = distance,
            ascentM = ascent,
            encodedPoints = encode(points),
        )
    }

    /**
     * The same walk, kept as fixes with their timestamps, for importing a
     * recording somebody else made rather than a route to follow.
     */
    fun readTrack(input: InputStream): List<ImportedPoint> =
        readPoints(input).mapNotNull { point ->
            val time = point.timeMs ?: return@mapNotNull null
            ImportedPoint(
                timestamp = time,
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeM = point.altitudeM,
                speedMps = point.speedMps,
            )
        }

    /**
     * Parses one of the several shapes a timestamp takes in these files:
     * a plain UTC instant, one with fractional seconds, or one with an offset.
     */
    internal fun parseTimestamp(text: String): Long? = runCatching {
        Instant.parse(text).toEpochMilli()
    }.recoverCatching {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    }.getOrNull()

    private fun readPoints(input: InputStream): List<RoutePoint> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val points = mutableListOf<RoutePoint>()

        // TCX splits the pair across child elements, so they are staged here.
        var tcxLat: Double? = null
        var tcxLon: Double? = null
        var pendingElevation: Double? = null
        var pendingTime: Long? = null
        var pendingSpeed: Float? = null
        var currentTag: String? = null
        var inTcxPosition = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfterLast(':')
                    currentTag = tag
                    when {
                        tag in GPX_POINT_TAGS -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                points += RoutePoint(lat, lon, null)
                            }
                        }
                        tag == "Position" -> {
                            inTcxPosition = true
                            tcxLat = null
                            tcxLon = null
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag) {
                            "LatitudeDegrees" -> if (inTcxPosition) tcxLat = text.toDoubleOrNull()
                            "LongitudeDegrees" -> if (inTcxPosition) tcxLon = text.toDoubleOrNull()
                            "ele", "AltitudeMeters" -> pendingElevation = text.toDoubleOrNull()
                            "time", "Time" -> pendingTime = parseTimestamp(text)
                            // Garmin's TrackPointExtension and TCX both carry
                            // speed, under different names.
                            "speed", "Speed" -> pendingSpeed = text.toFloatOrNull()
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfterLast(':')
                    when {
                        tag == "Position" -> {
                            inTcxPosition = false
                            val lat = tcxLat
                            val lon = tcxLon
                            if (lat != null && lon != null) points += RoutePoint(lat, lon, null)
                        }
                        tag in GPX_POINT_TAGS || tag == "Trackpoint" -> {
                            if (points.isNotEmpty()) {
                                points[points.lastIndex] = points.last().copy(
                                    altitudeM = pendingElevation ?: points.last().altitudeM,
                                    timeMs = pendingTime ?: points.last().timeMs,
                                    speedMps = pendingSpeed ?: points.last().speedMps,
                                )
                            }
                            pendingElevation = null
                            pendingTime = null
                            pendingSpeed = null
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return points
    }

    fun encode(points: List<RoutePoint>): String = points.joinToString(";") { point ->
        "${point.latitude},${point.longitude},${point.altitudeM ?: ""}"
    }

    fun decode(encoded: String): List<RoutePoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { chunk ->
            val parts = chunk.split(',')
            if (parts.size < 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            RoutePoint(lat, lon, parts.getOrNull(2)?.toDoubleOrNull())
        }
    }
}
