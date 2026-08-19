package com.lasse.speedometer.data.io

import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.db.WaypointEntity
import java.io.Writer
import java.util.Locale

/** A trip, its track and the pictures taken on it, as one backup entry. */
data class BackupTrip(
    val trip: TripEntity,
    val points: List<TrackPointEntity>,
    /** Content URIs, which only restore usefully onto the same device. */
    val photoUris: List<String> = emptyList(),
)

/** Everything a backup file holds, once read back. */
data class BackupContents(
    val trips: List<BackupTrip> = emptyList(),
    val tours: List<TourEntity> = emptyList(),
    val routes: List<RouteEntity> = emptyList(),
    val waypoints: List<WaypointEntity> = emptyList(),
)

/**
 * The backup file's shape, with no Android or database machinery in sight.
 *
 * Writing is streamed a trip at a time — a season of riding is hundreds of
 * thousands of fixes, and holding the whole file as a string is how an export
 * runs a phone out of memory. Reading parses in one go, which is fine: a
 * restore happens once, deliberately, on a device that is not also recording.
 */
object BackupFormat {

    const val FORMAT = "speedometer-backup"
    const val VERSION = 1

    fun writeHeader(writer: Writer, exportedAt: Long) {
        writer.append("{\"format\":").append(MiniJson.quote(FORMAT))
        writer.append(",\"version\":").append(VERSION.toString())
        writer.append(",\"exportedAt\":").append(exportedAt.toString())
    }

    fun writeTour(writer: Writer, tour: TourEntity) {
        writer.append("{\"id\":").append(tour.id.toString())
        writer.append(",\"name\":").append(MiniJson.quote(tour.name))
        writer.append(",\"createdAt\":").append(tour.createdAt.toString())
        writer.append('}')
    }

    fun writeTrip(
        writer: Writer,
        trip: TripEntity,
        points: List<TrackPointEntity>,
        photoUris: List<String> = emptyList(),
    ) {
        writer.append("{\"startedAt\":").append(trip.startedAt.toString())
        writer.append(",\"endedAt\":").append(trip.endedAt.toString())
        writer.append(",\"durationMs\":").append(trip.durationMs.toString())
        writer.append(",\"movingTimeMs\":").append(trip.movingTimeMs.toString())
        writer.append(",\"distanceM\":").append(number(trip.distanceM))
        writer.append(",\"avgSpeedMps\":").append(number(trip.avgSpeedMps))
        writer.append(",\"maxSpeedMps\":").append(number(trip.maxSpeedMps))
        writer.append(",\"ascentM\":").append(number(trip.ascentM))
        writer.append(",\"descentM\":").append(number(trip.descentM))
        trip.minAltitudeM?.let { writer.append(",\"minAltitudeM\":").append(number(it)) }
        trip.maxAltitudeM?.let { writer.append(",\"maxAltitudeM\":").append(number(it)) }
        trip.title?.let { writer.append(",\"title\":").append(MiniJson.quote(it)) }
        trip.note?.let { writer.append(",\"note\":").append(MiniJson.quote(it)) }
        trip.tourId?.let { writer.append(",\"tourId\":").append(it.toString()) }
        writer.append(",\"activity\":").append(MiniJson.quote(trip.activity))
        writer.append(",\"syncedToHealth\":").append(trip.syncedToHealth.toString())
        writer.append(",\"sketch\":").append(MiniJson.quote(trip.sketch))
        if (photoUris.isNotEmpty()) {
            writer.append(",\"photos\":[")
            photoUris.forEachIndexed { index, uri ->
                if (index > 0) writer.append(',')
                writer.append(MiniJson.quote(uri))
            }
            writer.append(']')
        }

        // Points are arrays, not objects: repeating seven key names per fix
        // doubles the file for nothing a reader cannot infer from position.
        writer.append(",\"points\":[")
        points.forEachIndexed { index, point ->
            if (index > 0) writer.append(',')
            writer.append('[')
            writer.append(point.timestamp.toString()).append(',')
            writer.append(coordinate(point.latitude)).append(',')
            writer.append(coordinate(point.longitude)).append(',')
            writer.append(point.altitudeM?.let { number(it) } ?: "null").append(',')
            writer.append(number(point.speedMps.toDouble())).append(',')
            writer.append(number(point.accuracyM.toDouble())).append(',')
            writer.append(number(point.cumulativeDistanceM))
            writer.append(']')
        }
        writer.append("]}")
    }

    fun writeRoute(writer: Writer, route: RouteEntity) {
        writer.append("{\"name\":").append(MiniJson.quote(route.name))
        writer.append(",\"importedAt\":").append(route.importedAt.toString())
        writer.append(",\"distanceM\":").append(number(route.distanceM))
        writer.append(",\"ascentM\":").append(number(route.ascentM))
        writer.append(",\"encodedPoints\":").append(MiniJson.quote(route.encodedPoints))
        writer.append('}')
    }

    fun writeWaypoint(writer: Writer, waypoint: WaypointEntity) {
        writer.append("{\"label\":").append(MiniJson.quote(waypoint.label))
        writer.append(",\"latitude\":").append(coordinate(waypoint.latitude))
        writer.append(",\"longitude\":").append(coordinate(waypoint.longitude))
        writer.append(",\"colorArgb\":").append(waypoint.colorArgb.toString())
        writer.append(",\"createdAt\":").append(waypoint.createdAt.toString())
        waypoint.note?.let { writer.append(",\"note\":").append(MiniJson.quote(it)) }
        writer.append('}')
    }

    /**
     * Reads a backup file. Ids are kept as written so the caller can rebuild
     * the trip-to-tour links against whatever ids the database hands out.
     */
    @Suppress("UNCHECKED_CAST")
    fun parse(text: String): BackupContents {
        val root = MiniJson.parse(text) as? Map<String, Any?>
            ?: throw JsonParseException("Backup is not a JSON object")
        if (root.string("format") != FORMAT) {
            throw JsonParseException("Not a Speedometer backup")
        }

        val tours = root.array("tours").filterIsInstance<Map<String, Any?>>().mapNotNull { entry ->
            val name = entry.string("name") ?: return@mapNotNull null
            TourEntity(
                id = entry.long("id") ?: 0L,
                name = name,
                createdAt = entry.long("createdAt") ?: 0L,
            )
        }

        val trips = root.array("trips").filterIsInstance<Map<String, Any?>>().mapNotNull { entry ->
            val startedAt = entry.long("startedAt") ?: return@mapNotNull null
            val trip = TripEntity(
                startedAt = startedAt,
                endedAt = entry.long("endedAt") ?: startedAt,
                durationMs = entry.long("durationMs") ?: 0L,
                movingTimeMs = entry.long("movingTimeMs") ?: 0L,
                distanceM = entry.double("distanceM") ?: 0.0,
                avgSpeedMps = entry.double("avgSpeedMps") ?: 0.0,
                maxSpeedMps = entry.double("maxSpeedMps") ?: 0.0,
                ascentM = entry.double("ascentM") ?: 0.0,
                descentM = entry.double("descentM") ?: 0.0,
                minAltitudeM = entry.double("minAltitudeM"),
                maxAltitudeM = entry.double("maxAltitudeM"),
                title = entry.string("title"),
                tourId = entry.long("tourId"),
                syncedToHealth = entry.bool("syncedToHealth") ?: false,
                activity = entry.string("activity") ?: "RIDE",
                note = entry.string("note"),
                sketch = entry.string("sketch").orEmpty(),
            )
            BackupTrip(
                trip = trip,
                points = entry.array("points").mapNotNull(::parsePoint),
                photoUris = entry.array("photos").filterIsInstance<String>(),
            )
        }

        val routes = root.array("routes").filterIsInstance<Map<String, Any?>>()
            .mapNotNull { entry ->
                val name = entry.string("name") ?: return@mapNotNull null
                val encoded = entry.string("encodedPoints") ?: return@mapNotNull null
                RouteEntity(
                    name = name,
                    importedAt = entry.long("importedAt") ?: 0L,
                    distanceM = entry.double("distanceM") ?: 0.0,
                    ascentM = entry.double("ascentM") ?: 0.0,
                    encodedPoints = encoded,
                )
            }

        val waypoints = root.array("waypoints").filterIsInstance<Map<String, Any?>>()
            .mapNotNull { entry ->
                val latitude = entry.double("latitude") ?: return@mapNotNull null
                val longitude = entry.double("longitude") ?: return@mapNotNull null
                WaypointEntity(
                    label = entry.string("label").orEmpty().ifBlank { "Waypoint" },
                    latitude = latitude,
                    longitude = longitude,
                    colorArgb = entry.long("colorArgb")?.toInt() ?: 0,
                    createdAt = entry.long("createdAt") ?: 0L,
                    note = entry.string("note"),
                )
            }

        return BackupContents(
            trips = trips,
            tours = tours,
            routes = routes,
            waypoints = waypoints,
        )
    }

    private fun parsePoint(row: Any?): TrackPointEntity? {
        val values = row as? List<*> ?: return null
        fun number(index: Int): Double? = values.getOrNull(index) as? Double
        val timestamp = number(0)?.toLong() ?: return null
        val latitude = number(1) ?: return null
        val longitude = number(2) ?: return null
        return TrackPointEntity(
            tripId = 0,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            altitudeM = number(3),
            speedMps = number(4)?.toFloat() ?: 0f,
            accuracyM = number(5)?.toFloat() ?: 0f,
            cumulativeDistanceM = number(6) ?: 0.0,
        )
    }

    /** Always a dot for the decimal point, whatever the phone's locale. */
    private fun number(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun coordinate(value: Double): String = String.format(Locale.US, "%.7f", value)
}
