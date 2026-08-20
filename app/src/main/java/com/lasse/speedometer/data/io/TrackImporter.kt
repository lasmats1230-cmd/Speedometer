package com.lasse.speedometer.data.io

import com.lasse.speedometer.tracking.TrackPoint
import com.lasse.speedometer.tracking.TripSummary
import com.lasse.speedometer.util.GeoMath
import kotlin.math.abs

/** A fix as it appears in a GPX or TCX file, before any statistics. */
data class ImportedPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    /** Some exporters carry speed; most leave it to be worked out. */
    val speedMps: Float?,
)

/** A whole trip read out of a file, ready to be stored. */
data class ImportedTrip(
    val summary: TripSummary,
    val points: List<TrackPoint>,
)

/**
 * Turns someone else's recording into one of ours.
 *
 * Anyone arriving from another tracker has years of rides in GPX files, and
 * an app that cannot read them starts their history at zero. The statistics
 * are recomputed here rather than trusted from the file, because exporters
 * disagree about what "average speed" means — and a distance that does not
 * match the track it is drawn from is the sort of thing that quietly makes an
 * app feel wrong.
 *
 * The filters are lighter than [com.lasse.speedometer.tracking.TripRecorder]'s
 * on purpose: a file has already been through whatever cleaning the original
 * app did, and re-applying drift rejection to a cleaned track only loses
 * distance that was really travelled.
 */
object TrackImporter {

    /** Below this a step is noise between two fixes taken while standing. */
    private const val MIN_STEP_M = 1.0

    /** A gap longer than this is a pause, not travel. */
    private const val MAX_GAP_MS = 120_000L

    /** Matches the recorder, so imported climbs compare with recorded ones. */
    private const val ELEVATION_THRESHOLD_M = 3.0

    private const val MOVING_THRESHOLD_MPS = 0.8

    /** Anything faster came from a bad fix, not a bicycle. */
    private const val MAX_PLAUSIBLE_MPS = 150.0

    /**
     * How long a repeated position is read as a receiver that could not
     * refresh rather than as a genuine standstill.
     *
     * The two look identical on paper — the same coordinates written out
     * again — but they want opposite treatment, so the length of the run has
     * to decide. Under a minute is a receiver losing its solution while you
     * carry on riding, and the step out of it took the whole run. Longer than
     * that and you were parked, so the step took only the time since you set
     * off again.
     */
    private const val MAX_STALE_MS = 60_000L

    fun build(points: List<ImportedPoint>): ImportedTrip? {
        val ordered = points
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .sortedBy { it.timestamp }
        if (ordered.size < 2) return null

        val track = mutableListOf<TrackPoint>()
        var distance = 0.0
        var movingTimeMs = 0L
        var maxSpeed = 0.0
        var ascent = 0.0
        var descent = 0.0
        var altitudeReference: Double? = null
        var minAltitude: Double? = null
        var maxAltitude: Double? = null

        /**
         * The last fix that was somewhere else, and the last fix that was
         * still at that same place.
         *
         * A receiver that cannot refresh its solution writes the old position
         * out again, so the point before this one is frequently this same
         * spot sampled a second later. Timing a step from the previous
         * *sample* then divides a whole run of travel by the seconds since
         * the last repeat — twenty-two seconds of repeats followed by a 148 m
         * step reads as 134 km/h on a bicycle ride.
         */
        var anchor: ImportedPoint? = null
        var lastAtAnchor: ImportedPoint? = null

        ordered.forEach { point ->
            val previous = anchor
            var speed = point.speedMps?.toDouble() ?: 0.0

            if (previous == null) {
                anchor = point
                lastAtAnchor = point
            } else {
                val step = GeoMath.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    point.latitude,
                    point.longitude,
                )
                if (step < MIN_STEP_M) {
                    // The same place again: no displacement to credit. Hold
                    // the anchor, but note that we were still here, so a long
                    // stop can be told from a receiver that briefly stuck.
                    lastAtAnchor = point
                    if (point.speedMps == null) speed = 0.0
                } else {
                    val held = point.timestamp - previous.timestamp
                    val gap = if (held <= MAX_STALE_MS) {
                        // Short run of repeats: the ride carried on through
                        // them, so the step took the whole stretch.
                        held
                    } else {
                        // A long one was a stop. The step began when it ended.
                        point.timestamp - (lastAtAnchor ?: previous).timestamp
                    }
                    if (gap in 1..MAX_GAP_MS) {
                        val computed = step / (gap / 1000.0)
                        if (computed <= MAX_PLAUSIBLE_MPS) {
                            distance += step
                            if (point.speedMps == null) speed = computed
                            if (computed >= MOVING_THRESHOLD_MPS) movingTimeMs += gap
                        }
                    }
                    anchor = point
                    lastAtAnchor = point
                }
            }

            if (speed <= MAX_PLAUSIBLE_MPS) maxSpeed = maxOf(maxSpeed, speed)

            point.altitudeM?.let { altitude ->
                minAltitude = minOf(minAltitude ?: altitude, altitude)
                maxAltitude = maxOf(maxAltitude ?: altitude, altitude)
                val reference = altitudeReference
                if (reference == null) {
                    altitudeReference = altitude
                } else if (abs(altitude - reference) >= ELEVATION_THRESHOLD_M) {
                    if (altitude > reference) {
                        ascent += altitude - reference
                    } else {
                        descent += reference - altitude
                    }
                    altitudeReference = altitude
                }
            }

            track += TrackPoint(
                timestamp = point.timestamp,
                latitude = point.latitude,
                longitude = point.longitude,
                altitudeM = point.altitudeM,
                speedMps = speed.toFloat(),
                accuracyM = 0f,
                cumulativeDistanceM = distance,
            )
        }

        val startedAt = ordered.first().timestamp
        val endedAt = ordered.last().timestamp
        val durationMs = (endedAt - startedAt).coerceAtLeast(0)
        val movingSeconds = movingTimeMs / 1000.0

        return ImportedTrip(
            summary = TripSummary(
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = durationMs,
                movingTimeMs = movingTimeMs,
                distanceM = distance,
                // Over moving time, matching what a recorded trip reports.
                avgSpeedMps = if (movingSeconds > 0) distance / movingSeconds else 0.0,
                maxSpeedMps = maxSpeed,
                ascentM = ascent,
                descentM = descent,
                minAltitudeM = minAltitude,
                maxAltitudeM = maxAltitude,
            ),
            points = track,
        )
    }
}
