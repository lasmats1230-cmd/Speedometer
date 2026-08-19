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

    /** Finished trips only — a recording in progress is not history yet. */
    @Query("SELECT * FROM trips WHERE inProgress = 0 ORDER BY startedAt DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    /** The row a recording is being written into, if there is one. */
    @Query("SELECT * FROM trips WHERE inProgress = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgress(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE inProgress = 1")
    suspend fun getInProgress(): List<TripEntity>

    @Query("UPDATE trips SET inProgress = 0 WHERE id = :tripId")
    suspend fun finishTrip(tripId: Long)

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE tourId = :tourId ORDER BY startedAt ASC")
    suspend fun getTripsForTour(tourId: Long): List<TripEntity>

    /** Every finished trip in one read, for a backup. */
    @Query("SELECT * FROM trips WHERE inProgress = 0 ORDER BY startedAt ASC")
    suspend fun getAllTrips(): List<TripEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET tourId = :tourId WHERE id = :tripId")
    suspend fun setTripTour(tripId: Long, tourId: Long?)

    @Query("UPDATE trips SET title = :title WHERE id = :tripId")
    suspend fun setTripTitle(tripId: Long, title: String?)

    @Query("UPDATE trips SET note = :note WHERE id = :tripId")
    suspend fun setTripNote(tripId: Long, note: String?)

    @Query("UPDATE trips SET activity = :activity WHERE id = :tripId")
    suspend fun setTripActivity(tripId: Long, activity: String)

    @Query("UPDATE trips SET sketch = :sketch WHERE id = :tripId")
    suspend fun setTripSketch(tripId: Long, sketch: String)

    /** The ids of trips saved before sketches existed, to fill in. */
    @Query("SELECT id FROM trips WHERE sketch = '' AND inProgress = 0")
    suspend fun tripsWithoutSketch(): List<Long>

    @Query("UPDATE trips SET syncedToHealth = 1 WHERE id = :tripId")
    suspend fun markSynced(tripId: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("DELETE FROM trips WHERE inProgress = 0")
    suspend fun deleteAllTrips()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPoints(tripId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observePoints(tripId: Long): Flow<List<TrackPointEntity>>

    /** Decimated tracks for a set of trips, for drawing a tour on one map. */
    @Query(
        """
        SELECT tripId, latitude, longitude FROM track_points
        WHERE tripId IN (:tripIds) AND id % 4 = 0
        ORDER BY tripId ASC, timestamp ASC
        """
    )
    suspend fun getThumbnailPointsFor(tripIds: List<Long>): List<TrackPointLite>

    /** How much of a trip is already written, so appending can carry on. */
    @Query("SELECT COUNT(*) FROM track_points WHERE tripId = :tripId")
    suspend fun countPoints(tripId: Long): Int
}

@Dao
interface TripPhotoDao {

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY addedAt ASC")
    fun observePhotos(tripId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos ORDER BY addedAt ASC")
    suspend fun getAllPhotos(): List<TripPhotoEntity>

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY addedAt ASC")
    suspend fun getPhotos(tripId: Long): List<TripPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhoto(photo: TripPhotoEntity): Long

    @Query("DELETE FROM trip_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)
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

    @Query("SELECT * FROM tours ORDER BY createdAt ASC")
    suspend fun getAllTours(): List<TourEntity>

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

    @Query("SELECT * FROM routes ORDER BY importedAt ASC")
    suspend fun getAllRoutes(): List<RouteEntity>

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

    @Query("SELECT * FROM waypoints ORDER BY createdAt ASC")
    suspend fun getAllWaypoints(): List<WaypointEntity>

    @Insert
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Update
    suspend fun updateWaypoint(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypoint(id: Long)

    @Query("DELETE FROM waypoints")
    suspend fun deleteAllWaypoints()
}
