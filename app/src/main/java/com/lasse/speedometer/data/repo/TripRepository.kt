package com.lasse.speedometer.data.repo

import com.lasse.speedometer.data.db.RouteDao
import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.db.TourDao
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.db.TourWithTrips
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripDao
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.db.WaypointDao
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.tracking.TrackPoint
import com.lasse.speedometer.tracking.TripSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
) {

    val waypoints: Flow<List<WaypointEntity>> = waypointDao.observeWaypoints()

    val trips: Flow<List<TripEntity>> = tripDao.observeTrips()

    val tripListItems: Flow<List<TripListItem>> =
        combine(tripDao.observeTrips(), tripDao.observeThumbnailPoints()) { trips, points ->
            val byTrip = points.groupBy { it.tripId }
            trips.map { trip ->
                TripListItem(
                    trip = trip,
                    thumbnail = byTrip[trip.id].orEmpty().map { it.latitude to it.longitude },
                )
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

    suspend fun saveTrip(summary: TripSummary, points: List<TrackPoint>): Long =
        withContext(Dispatchers.IO) {
            val tripId = tripDao.insertTrip(
                TripEntity(
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
            tripId
        }

    suspend fun deleteTrip(id: Long) = withContext(Dispatchers.IO) { tripDao.deleteTrip(id) }

    suspend fun deleteAllTrips() = withContext(Dispatchers.IO) { tripDao.deleteAllTrips() }

    suspend fun renameTrip(id: Long, title: String?) =
        withContext(Dispatchers.IO) { tripDao.setTripTitle(id, title?.ifBlank { null }) }

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

    suspend fun deleteRoute(id: Long) = withContext(Dispatchers.IO) { routeDao.deleteRoute(id) }

    suspend fun addWaypoint(
        label: String,
        latitude: Double,
        longitude: Double,
        colorArgb: Int,
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
}

data class TourSummary(
    val tour: TourEntity,
    val tripCount: Int,
    val distanceM: Double,
    val durationMs: Long,
    val ascentM: Double,
    val maxSpeedMps: Double,
)
