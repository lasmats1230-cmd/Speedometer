package com.lasse.speedometer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = TourEntity::class,
            parentColumns = ["id"],
            childColumns = ["tourId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("startedAt"), Index("tourId")],
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    /** Wall-clock duration in milliseconds, excluding paused stretches. */
    val durationMs: Long,
    /** Time spent above the moving threshold, in milliseconds. */
    val movingTimeMs: Long,
    /** Metres. */
    val distanceM: Double,
    /** Metres per second. */
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    /** Metres. */
    val ascentM: Double,
    val descentM: Double,
    val minAltitudeM: Double?,
    val maxAltitudeM: Double?,
    val title: String? = null,
    val tourId: Long? = null,
    @ColumnInfo(defaultValue = "0") val syncedToHealth: Boolean = false,
    /**
     * Stored as the enum's name rather than the enum itself: a column written
     * by a build that knew about an activity this one does not must not stop
     * the trip from loading.
     */
    @ColumnInfo(defaultValue = "RIDE") val activity: String = ActivityType.RIDE.name,
    val note: String? = null,
) {
    val activityType: ActivityType get() = ActivityType.fromName(activity)
}

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("tripId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    /** Metres per second. */
    val speedMps: Float,
    /** Horizontal accuracy in metres. */
    val accuracyM: Float,
    /** Cumulative distance from the start of the trip, in metres. */
    val cumulativeDistanceM: Double,
)

@Entity(tableName = "tours")
data class TourEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val importedAt: Long,
    val distanceM: Double,
    val ascentM: Double,
    /** Encoded as "lat,lon,ele" triples joined by ';'. */
    val encodedPoints: String,
)

/**
 * A place worth remembering, dropped by long-pressing the map.
 *
 * Waypoints are not tied to a trip: a water tap or a locked gate is just as
 * useful on the next ride as on the one it was noted during.
 */
@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    /** Packed ARGB, so the colour survives a theme change unchanged. */
    val colorArgb: Int,
    val createdAt: Long,
    val note: String? = null,
)
