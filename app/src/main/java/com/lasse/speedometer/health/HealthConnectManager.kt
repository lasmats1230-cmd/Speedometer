package com.lasse.speedometer.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.lasse.speedometer.data.repo.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Wraps Health Connect so the rest of the app never has to know if it exists. */
class HealthConnectManager(
    private val context: Context,
    private val repository: TripRepository,
) {

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(SpeedRecord::class),
        HealthPermission.getWritePermission(ElevationGainedRecord::class),
    )

    val availability: Int
        get() = runCatching { HealthConnectClient.getSdkStatus(context) }
            .getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    val isAvailable: Boolean get() = availability == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient?
        get() = if (isAvailable) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    suspend fun hasPermissions(): Boolean = withContext(Dispatchers.IO) {
        val granted = runCatching {
            client?.permissionController?.getGrantedPermissions()
        }.getOrNull().orEmpty()
        permissions.all { it in granted }
    }

    /**
     * Writes the trip as a biking session with its route, distance, speed
     * samples and elevation gain.
     */
    suspend fun writeTrip(tripId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val healthClient = client ?: error("Health Connect unavailable")
            val trip = repository.getTrip(tripId) ?: error("Trip $tripId not found")
            val points = repository.getPoints(tripId)

            val start = Instant.ofEpochMilli(trip.startedAt)
            val end = Instant.ofEpochMilli(trip.endedAt.coerceAtLeast(trip.startedAt + 1000))
            val offset = ZoneId.systemDefault().rules.getOffset(start)

            val route = points
                .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                .map { point ->
                    ExerciseRoute.Location(
                        time = Instant.ofEpochMilli(point.timestamp),
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitudeM?.let { Length.meters(it) },
                        horizontalAccuracy = Length.meters(point.accuracyM.toDouble()),
                    )
                }

            val records = buildList {
                add(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                        title = trip.title,
                        exerciseRoute = if (route.size >= 2) ExerciseRoute(route) else null,
                    )
                )
                add(
                    DistanceRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        distance = Length.meters(trip.distanceM),
                    )
                )
                if (points.isNotEmpty()) {
                    add(
                        SpeedRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = end,
                            endZoneOffset = offset,
                            samples = points.map { point ->
                                SpeedRecord.Sample(
                                    time = Instant.ofEpochMilli(point.timestamp),
                                    speed = Velocity.metersPerSecond(point.speedMps.toDouble()),
                                )
                            },
                        )
                    )
                }
                if (trip.ascentM > 0) {
                    add(
                        ElevationGainedRecord(
                            startTime = start,
                            startZoneOffset = offset,
                            endTime = end,
                            endZoneOffset = offset,
                            elevation = Length.meters(trip.ascentM),
                        )
                    )
                }
            }

            healthClient.insertRecords(records)
            repository.markSynced(tripId)
        }
    }
}
