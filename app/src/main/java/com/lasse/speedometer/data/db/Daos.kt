package com.lasse.speedometer.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TourWithTrips(
    @Embedded val tour: TourEntity,
    @Relation(parentColumn = "id", entityColumn = "tourId")
    val trips: List<TripEntity>,
)

/** Just enough of a track to draw a thumbnail without loading every point. */
data class TrackPointLite(
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
)

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE tourId = :tourId ORDER BY startedAt ASC")
    suspend fun getTripsForTour(tourId: Long): List<TripEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET tourId = :tourId WHERE id = :tripId")
    suspend fun setTripTour(tripId: Long, tourId: Long?)

    @Query("UPDATE trips SET title = :title WHERE id = :tripId")
    suspend fun setTripTitle(tripId: Long, title: String?)

    @Query("UPDATE trips SET syncedToHealth = 1 WHERE id = :tripId")
    suspend fun markSynced(tripId: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPoints(tripId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observePoints(tripId: Long): Flow<List<TrackPointEntity>>

    /**
     * Every fourth point of every trip — enough resolution for the list
     * thumbnails, a fraction of the rows.
     */
    @Query(
        """
        SELECT tripId, latitude, longitude FROM track_points
        WHERE id % 4 = 0
        ORDER BY tripId ASC, timestamp ASC
        """
    )
    fun observeThumbnailPoints(): Flow<List<TrackPointLite>>
}

@Dao
interface TourDao {

    @Query("SELECT * FROM tours ORDER BY createdAt DESC")
    fun observeTours(): Flow<List<TourEntity>>

    @Transaction
    @Query("SELECT * FROM tours ORDER BY createdAt DESC")
    fun observeToursWithTrips(): Flow<List<TourWithTrips>>

    @Transaction
    @Query("SELECT * FROM tours WHERE id = :id")
    fun observeTourWithTrips(id: Long): Flow<TourWithTrips?>

    @Insert
    suspend fun insertTour(tour: TourEntity): Long

    @Query("UPDATE tours SET name = :name WHERE id = :id")
    suspend fun renameTour(id: Long, name: String)

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun deleteTour(id: Long)
}

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    fun observeRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getRoute(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observeRoute(id: Long): Flow<RouteEntity?>

    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteRoute(id: Long)
}

@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    fun observeWaypoints(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getWaypoint(id: Long): WaypointEntity?

    @Insert
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Update
    suspend fun updateWaypoint(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypoint(id: Long)

    @Query("DELETE FROM waypoints")
    suspend fun deleteAllWaypoints()
}
