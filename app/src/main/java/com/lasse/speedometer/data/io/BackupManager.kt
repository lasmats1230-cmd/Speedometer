package com.lasse.speedometer.data.io

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lasse.speedometer.data.db.SpeedometerDatabase
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.db.TripPhotoEntity
import com.lasse.speedometer.data.repo.TrackSketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Writer

/** What a restore put back, for the message that follows it. */
data class RestoreResult(
    val trips: Int,
    val tours: Int,
    val routes: Int,
    val waypoints: Int,
    /** Trips already in the database, left alone. */
    val skipped: Int,
)

/**
 * Everything this app knows, in one file you can copy off the phone.
 *
 * Trips are stored only on the device and uploaded nowhere, which is the
 * privacy promise — and also means a lost phone is a lost history unless there
 * is a way to get the data out. That is what this is: a plain JSON file,
 * written in full, that restores into a fresh install.
 *
 * Restoring merges rather than replaces. A trip whose start time is already
 * present is skipped, so importing the same backup twice does not double every
 * ride.
 */
class BackupManager(
    private val context: Context,
    private val database: SpeedometerDatabase,
) {

    /** Writes a backup into the public Downloads folder, returning its name. */
    suspend fun export(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = "Speedometer_backup_${Downloads.stamp(System.currentTimeMillis())}.json"
            Downloads.write(context, name, MIME_JSON) { write(it) }
            name
        }
    }

    /** Reads a backup the user picked and merges it into the database. */
    suspend fun restore(uri: Uri): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.reader().readText()
            } ?: error("Could not open $uri")
            // All of it or none of it. A restore that failed halfway used to
            // leave the database half merged, and the "skip what is already
            // here" rule then made a second attempt skip exactly the trips the
            // first one had managed to write.
            database.withTransaction { merge(BackupFormat.parse(text)) }
        }
    }

    /**
     * Streams the whole database out a trip at a time, so the file is never
     * held in memory all at once.
     */
    private suspend fun write(writer: Writer) {
        val tripDao = database.tripDao()
        BackupFormat.writeHeader(writer, System.currentTimeMillis())

        writer.append(",\"tours\":[")
        database.tourDao().getAllTours().forEachIndexed { index, tour ->
            if (index > 0) writer.append(',')
            BackupFormat.writeTour(writer, tour)
        }
        writer.append(']')

        writer.append(",\"trips\":[")
        val photoDao = database.tripPhotoDao()
        tripDao.getAllTrips().forEachIndexed { index, trip ->
            if (index > 0) writer.append(',')
            BackupFormat.writeTrip(
                writer = writer,
                trip = trip,
                points = tripDao.getPoints(trip.id),
                photoUris = photoDao.getPhotos(trip.id).map { it.uri },
            )
        }
        writer.append(']')

        writer.append(",\"routes\":[")
        database.routeDao().getAllRoutes().forEachIndexed { index, route ->
            if (index > 0) writer.append(',')
            BackupFormat.writeRoute(writer, route)
        }
        writer.append(']')

        writer.append(",\"waypoints\":[")
        database.waypointDao().getAllWaypoints().forEachIndexed { index, waypoint ->
            if (index > 0) writer.append(',')
            BackupFormat.writeWaypoint(writer, waypoint)
        }
        writer.append(']')

        writer.append('}')
        writer.flush()
    }

    private suspend fun merge(contents: BackupContents): RestoreResult {
        val tripDao = database.tripDao()
        val tourDao = database.tourDao()
        val routeDao = database.routeDao()
        val waypointDao = database.waypointDao()
        val photoDao = database.tripPhotoDao()

        // Ids belong to the database that issued them, so every tour gets a
        // new one and its trips are rewired rather than pointing at whatever
        // happens to hold the old id here.
        val tourIds = mutableMapOf<Long, Long>()
        contents.tours.forEach { tour ->
            val newId = tourDao.insertTour(
                TourEntity(
                    name = tour.name,
                    createdAt = tour.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                )
            )
            tourIds[tour.id] = newId
        }

        val existingStarts = tripDao.getAllTrips().map { it.startedAt }.toHashSet()
        var restored = 0
        var skipped = 0
        contents.trips.forEach { entry ->
            if (!existingStarts.add(entry.trip.startedAt)) {
                skipped++
                return@forEach
            }
            val tripId = tripDao.insertTrip(
                entry.trip.copy(
                    id = 0,
                    tourId = entry.trip.tourId?.let { tourIds[it] },
                    // Backups predating the stored thumbnail carry none, so it
                    // is derived from the track that came with them.
                    sketch = entry.trip.sketch.ifBlank {
                        TrackSketch.encode(entry.points.map { it.latitude to it.longitude })
                    },
                )
            )
            entry.points
                .map { it.copy(id = 0, tripId = tripId) }
                .chunked(POINT_CHUNK)
                .forEach { tripDao.insertPoints(it) }
            // Photos restore as references. On the phone they came from they
            // light up again; on another one they show as missing rather than
            // silently disappearing, which is the honest outcome for a backup
            // that never contained the pictures themselves.
            entry.photoUris.forEach { uri ->
                photoDao.insertPhoto(
                    TripPhotoEntity(
                        tripId = tripId,
                        uri = uri,
                        addedAt = entry.trip.startedAt,
                    )
                )
            }
            restored++
        }

        contents.routes.forEach { routeDao.insertRoute(it.copy(id = 0)) }

        val existingWaypoints = waypointDao.getAllWaypoints()
            .map { it.latitude to it.longitude }
            .toHashSet()
        var waypoints = 0
        contents.waypoints.forEach { waypoint ->
            if (!existingWaypoints.add(waypoint.latitude to waypoint.longitude)) return@forEach
            waypointDao.insertWaypoint(waypoint.copy(id = 0))
            waypoints++
        }

        return RestoreResult(
            trips = restored,
            tours = contents.tours.size,
            routes = contents.routes.size,
            waypoints = waypoints,
            skipped = skipped,
        )
    }

    private companion object {
        const val MIME_JSON = "application/json"
        const val POINT_CHUNK = 500
    }
}
