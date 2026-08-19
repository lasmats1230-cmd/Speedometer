package com.lasse.speedometer.data.repo

import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.RouteDao
import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.db.TourDao
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.db.TourWithTrips
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripDao
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.db.TripPhotoDao
import com.lasse.speedometer.data.db.TripPhotoEntity
import com.lasse.speedometer.data.db.WaypointDao
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.tracking.TrackPoint
import com.lasse.speedometer.tracking.TripSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A trip plus a decimated version of its track, ready for a list thumbnail. */
data class TripListItem(
    val trip: TripEntity,
    val thumbnail: List<Pair<Double, Double>>,
)

class TripRepository(
    private val tripDao: TripDao,
    private val tourDao: TourDao,
    private val routeDao: RouteDao,
    private val waypointDao: WaypointDao,
    private val photoDao: TripPhotoDao,
) {

    fun observePhotos(tripId: Long): Flow<List<TripPhotoEntity>> = photoDao.observePhotos(tripId)

    suspend fun addPhoto(tripId: Long, uri: String) = withContext(Dispatchers.IO) {
        photoDao.insertPhoto(
            TripPhotoEntity(
                tripId = tripId,
                uri = uri,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deletePhoto(id: Long) = withContext(Dispatchers.IO) { photoDao.deletePhoto(id) }

    suspend fun photosFor(tripId: Long): List<TripPhotoEntity> =
        withContext(Dispatchers.IO) { photoDao.getPhotos(tripId) }

    val waypoints: Flow<List<WaypointEntity>> = waypointDao.observeWaypoints()

    val trips: Flow<List<TripEntity>> = tripDao.observeTrips()

    /**
     * The history list. The sketch each row draws is stored on the trip, so
     * this reads one table and no track points at all.
     */
    val tripListItems: Flow<List<TripListItem>> = tripDao.observeTrips().map { trips ->
        trips.map { trip ->
            TripListItem(trip = trip, thumbnail = TrackSketch.decode(trip.sketch))
        }
    }

    val tours: Flow<List<TourWithTrips>> = tourDao.observeToursWithTrips()

    val routes: Flow<List<RouteEntity>> = routeDao.observeRoutes()

    fun observeTrip(id: Long): Flow<TripEntity?> = tripDao.observeTrip(id)

    fun observePoints(id: Long): Flow<List<TrackPointEntity>> = tripDao.observePoints(id)

    fun observeTour(id: Long): Flow<TourWithTrips?> = tourDao.observeTourWithTrips(id)

    fun observeRoute(id: Long): Flow<RouteEntity?> = routeDao.observeRoute(id)

    suspend fun getTrip(id: Long): TripEntity? = tripDao.getTrip(id)

    suspend fun getPoints(id: Long): List<TrackPointEntity> = tripDao.getPoints(id)

    /**
     * Every track in a tour, thinned the way the list thumbnails are: a tour
     * of twenty rides is a hundred thousand fixes, and a map that has to draw
     * all of them to show the shape of a holiday is a map that stutters.
     */
    suspend fun tourTracks(tripIds: List<Long>): List<List<Pair<Double, Double>>> =
        withContext(Dispatchers.IO) {
            if (tripIds.isEmpty()) return@withContext emptyList()
            tripDao.getThumbnailPointsFor(tripIds)
                .groupBy { it.tripId }
                .values
                .map { points -> points.map { it.latitude to it.longitude } }
        }

    suspend fun saveTrip(
        summary: TripSummary,
        points: List<TrackPoint>,
        activity: ActivityType = ActivityType.RIDE,
        title: String? = null,
    ): Long =
        withContext(Dispatchers.IO) {
            val tripId = tripDao.insertTrip(
                TripEntity(
                    activity = activity.name,
                    title = title,
                    startedAt = summary.startedAt,
                    endedAt = summary.endedAt,
                    durationMs = summary.durationMs,
                    movingTimeMs = summary.movingTimeMs,
                    distanceM = summary.distanceM,
                    avgSpeedMps = summary.avgSpeedMps,
                    maxSpeedMps = summary.maxSpeedMps,
                    ascentM = summary.ascentM,
                    descentM = summary.descentM,
                    minAltitudeM = summary.minAltitudeM,
                    maxAltitudeM = summary.maxAltitudeM,
                )
            )
            // Chunked so a long ride doesn't build one enormous statement.
            points.map { point ->
                TrackPointEntity(
                    tripId = tripId,
                    timestamp = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitudeM = point.altitudeM,
                    speedMps = point.speedMps,
                    accuracyM = point.accuracyM,
                    cumulativeDistanceM = point.cumulativeDistanceM,
                )
            }.chunked(500).forEach { tripDao.insertPoints(it) }
            tripDao.setTripSketch(tripId, sketchOf(points.map { it.latitude to it.longitude }))
            tripId
        }

    /**
     * Fills in thumbnails for trips recorded before they were stored.
     *
     * Done once, in the background, rather than inside the migration: a
     * hundred rides is a hundred track reads, and doing that while the user
     * waits for the app to open after an update is the wrong trade.
     */
    suspend fun backfillSketches() = withContext(Dispatchers.IO) {
        tripDao.tripsWithoutSketch().forEach { tripId ->
            val points = tripDao.getPoints(tripId).map { it.latitude to it.longitude }
            if (points.size >= 2) tripDao.setTripSketch(tripId, sketchOf(points))
        }
    }

    private fun sketchOf(points: List<Pair<Double, Double>>): String =
        if (points.size >= 2) TrackSketch.encode(points) else ""

    /** The row a recording is being written into, if a recording is under way. */
    val inProgressTrip: Flow<TripEntity?> = tripDao.observeInProgress()

    /**
     * Opens a row for a recording that has just started.
     *
     * Any earlier unfinished row is cleared first: two in-progress trips can
     * only mean the previous one was orphaned, and the user is about to be
     * offered it or has already declined.
     */
    suspend fun startRecording(
        startedAt: Long,
        activity: ActivityType,
        title: String?,
    ): Long = withContext(Dispatchers.IO) {
        tripDao.getInProgress().forEach { tripDao.deleteTrip(it.id) }
        tripDao.insertTrip(
            TripEntity(
                startedAt = startedAt,
                endedAt = startedAt,
                durationMs = 0,
                movingTimeMs = 0,
                distanceM = 0.0,
                avgSpeedMps = 0.0,
                maxSpeedMps = 0.0,
                ascentM = 0.0,
                descentM = 0.0,
                minAltitudeM = null,
                maxAltitudeM = null,
                title = title,
                activity = activity.name,
                inProgress = true,
            )
        )
    }

    /**
     * Writes the part of the track that is not stored yet and refreshes the
     * running totals. Called every few seconds while recording, so it only
     * ever appends what is new.
     */
    suspend fun appendRecording(
        tripId: Long,
        summary: TripSummary,
        points: List<TrackPoint>,
    ) = withContext(Dispatchers.IO) {
        val stored = tripDao.countPoints(tripId)
        if (points.size > stored) {
            points.drop(stored).map { point ->
                TrackPointEntity(
                    tripId = tripId,
                    timestamp = point.timestamp,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitudeM = point.altitudeM,
                    speedMps = point.speedMps,
                    accuracyM = point.accuracyM,
                    cumulativeDistanceM = point.cumulativeDistanceM,
                )
            }.chunked(500).forEach { tripDao.insertPoints(it) }
        }
        tripDao.getTrip(tripId)?.let { trip ->
            tripDao.updateTrip(trip.applySummary(summary))
        }
    }

    /** Closes the row: the recording becomes an ordinary trip. */
    suspend fun finishRecording(
        tripId: Long,
        summary: TripSummary,
        points: List<TrackPoint>,
    ) = withContext(Dispatchers.IO) {
        appendRecording(tripId, summary, points)
        tripDao.setTripSketch(tripId, sketchOf(points.map { it.latitude to it.longitude }))
        tripDao.finishTrip(tripId)
    }

    /** Ends a recording that was interrupted, keeping whatever was written. */
    suspend fun recoverRecording(tripId: Long) = withContext(Dispatchers.IO) {
        val points = tripDao.getPoints(tripId).map { it.latitude to it.longitude }
        tripDao.setTripSketch(tripId, sketchOf(points))
        tripDao.finishTrip(tripId)
    }

    suspend fun discardRecording(tripId: Long) =
        withContext(Dispatchers.IO) { tripDao.deleteTrip(tripId) }

    private fun TripEntity.applySummary(summary: TripSummary) = copy(
        endedAt = summary.endedAt,
        durationMs = summary.durationMs,
        movingTimeMs = summary.movingTimeMs,
        distanceM = summary.distanceM,
        avgSpeedMps = summary.avgSpeedMps,
        maxSpeedMps = summary.maxSpeedMps,
        ascentM = summary.ascentM,
        descentM = summary.descentM,
        minAltitudeM = summary.minAltitudeM,
        maxAltitudeM = summary.maxAltitudeM,
    )

    suspend fun deleteTrip(id: Long) = withContext(Dispatchers.IO) { tripDao.deleteTrip(id) }

    suspend fun deleteAllTrips() = withContext(Dispatchers.IO) { tripDao.deleteAllTrips() }

    suspend fun renameTrip(id: Long, title: String?) =
        withContext(Dispatchers.IO) { tripDao.setTripTitle(id, title?.ifBlank { null }) }

    suspend fun setTripNote(id: Long, note: String?) =
        withContext(Dispatchers.IO) { tripDao.setTripNote(id, note?.ifBlank { null }) }

    suspend fun setTripActivity(id: Long, activity: ActivityType) =
        withContext(Dispatchers.IO) { tripDao.setTripActivity(id, activity.name) }

    suspend fun markSynced(id: Long) = withContext(Dispatchers.IO) { tripDao.markSynced(id) }

    suspend fun createTour(name: String): Long = withContext(Dispatchers.IO) {
        tourDao.insertTour(TourEntity(name = name, createdAt = System.currentTimeMillis()))
    }

    suspend fun addTripToTour(tripId: Long, tourId: Long?) =
        withContext(Dispatchers.IO) { tripDao.setTripTour(tripId, tourId) }

    suspend fun deleteTour(id: Long) = withContext(Dispatchers.IO) { tourDao.deleteTour(id) }

    suspend fun renameTour(id: Long, name: String) =
        withContext(Dispatchers.IO) { tourDao.renameTour(id, name) }

    suspend fun tourTrips(tourId: Long): List<TripEntity> =
        withContext(Dispatchers.IO) { tripDao.getTripsForTour(tourId) }

    suspend fun insertRoute(route: RouteEntity): Long =
        withContext(Dispatchers.IO) { routeDao.insertRoute(route) }

    suspend fun renameRoute(id: Long, name: String) =
        withContext(Dispatchers.IO) { routeDao.renameRoute(id, name) }

    suspend fun deleteRoute(id: Long) = withContext(Dispatchers.IO) { routeDao.deleteRoute(id) }

    suspend fun addWaypoint(
        label: String,
        latitude: Double,
        longitude: Double,
        colorArgb: Int = DEFAULT_WAYPOINT_COLOR,
        note: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        waypointDao.insertWaypoint(
            WaypointEntity(
                label = label.ifBlank { "Waypoint" },
                latitude = latitude,
                longitude = longitude,
                colorArgb = colorArgb,
                createdAt = System.currentTimeMillis(),
                note = note?.ifBlank { null },
            )
        )
    }

    suspend fun updateWaypoint(waypoint: WaypointEntity) =
        withContext(Dispatchers.IO) { waypointDao.updateWaypoint(waypoint) }

    suspend fun getWaypoint(id: Long): WaypointEntity? =
        withContext(Dispatchers.IO) { waypointDao.getWaypoint(id) }

    suspend fun deleteWaypoint(id: Long) =
        withContext(Dispatchers.IO) { waypointDao.deleteWaypoint(id) }

    suspend fun deleteAllWaypoints() =
        withContext(Dispatchers.IO) { waypointDao.deleteAllWaypoints() }

    /** Tours list, cheap enough to derive without another query. */
    val tourList: Flow<List<TourEntity>> = tourDao.observeTours()

    val tourSummaries: Flow<List<TourSummary>> = tours.map { list ->
        list.map { entry ->
            TourSummary(
                tour = entry.tour,
                tripCount = entry.trips.size,
                distanceM = entry.trips.sumOf { it.distanceM },
                durationMs = entry.trips.sumOf { it.durationMs },
                ascentM = entry.trips.sumOf { it.ascentM },
                maxSpeedMps = entry.trips.maxOfOrNull { it.maxSpeedMps } ?: 0.0,
            )
        }
    }

    private companion object {
        /**
         * The blue a marker gets when nobody chose one: dropping a waypoint
         * from the notification has no screen to pick a colour on.
         */
        const val DEFAULT_WAYPOINT_COLOR = 0xFF2F7BFF.toInt()
    }
}

data class TourSummary(
    val tour: TourEntity,
    val tripCount: Int,
    val distanceM: Double,
    val durationMs: Long,
    val ascentM: Double,
    val maxSpeedMps: Double,
)
