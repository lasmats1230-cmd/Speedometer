package com.lasse.speedometer.tracking

import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.util.GeoMath
import kotlin.math.abs

/**
 * Turns a stream of raw fixes into trip statistics.
 *
 * Deliberately free of Android service plumbing so the filtering rules can be
 * reasoned about — and tested — on their own.
 */
class TripRecorder(
    private var minAccuracyM: Float = 50f,
    private var autoPause: Boolean = false,
    private var speedSource: SpeedSource = SpeedSource.GNSS,
) {

    private val _points = mutableListOf<TrackPoint>()
    val points: List<TrackPoint> get() = _points

    var startedAt: Long = 0L
        private set

    /**
     * Whether a recording is under way. An explicit flag rather than checking
     * [startedAt] against zero — the epoch is a legitimate timestamp, and
     * conflating the two silently froze the clocks.
     */
    private var running = false

    private var lastFix: Fix? = null
    private var lastAcceptedAt: Long = 0L

    /** Altitude the ascent/descent totals are measured against. */
    private var altitudeReference: Double? = null
    private var smoothedAltitude: Double? = null

    private var distanceM = 0.0
    private var maxSpeedMps = 0.0
    private var ascentM = 0.0
    private var descentM = 0.0
    private var minAltitudeM: Double? = null
    private var maxAltitudeM: Double? = null

    /** Milliseconds accumulated while not paused. */
    private var elapsedMs = 0L
    private var movingTimeMs = 0L
    private var lastTickAt = 0L

    private var paused = false
    private var stillSinceMs: Long? = null

    var state = TrackingState()
        private set

    fun configure(minAccuracyM: Float, autoPause: Boolean, speedSource: SpeedSource) {
        this.minAccuracyM = minAccuracyM
        this.autoPause = autoPause
        this.speedSource = speedSource
    }

    fun start(now: Long) {
        clear()
        running = true
        startedAt = now
        lastTickAt = now
        state = TrackingState(status = TrackingStatus.ACQUIRING)
    }

    fun reset() {
        clear()
        state = TrackingState()
    }

    private fun clear() {
        _points.clear()
        running = false
        startedAt = 0L
        lastFix = null
        lastAcceptedAt = 0L
        altitudeReference = null
        smoothedAltitude = null
        distanceM = 0.0
        maxSpeedMps = 0.0
        ascentM = 0.0
        descentM = 0.0
        minAltitudeM = null
        maxAltitudeM = null
        elapsedMs = 0L
        movingTimeMs = 0L
        lastTickAt = 0L
        paused = false
        stillSinceMs = null
    }

    fun pause(now: Long) {
        tick(now)
        paused = true
        state = state.copy(status = TrackingStatus.PAUSED, speedMps = 0.0)
    }

    fun resume(now: Long) {
        paused = false
        stillSinceMs = null
        lastTickAt = now
        lastFix = null // don't bridge the gap we sat out
        state = state.copy(
            status = if (state.hasFix) TrackingStatus.RECORDING else TrackingStatus.ACQUIRING,
        )
    }

    /** Advances the clocks. Call about once a second so the timer stays live. */
    fun tick(now: Long) {
        if (!running) return
        val delta = (now - lastTickAt).coerceAtLeast(0L)
        lastTickAt = now
        if (!paused) {
            elapsedMs += delta
            if (state.speedMps >= MOVING_THRESHOLD_MPS) movingTimeMs += delta
        }
        state = state.copy(
            elapsedMs = elapsedMs,
            movingTimeMs = movingTimeMs,
            avgSpeedMps = averageSpeed(),
        )
    }

    /**
     * Feeds a fix in. Returns true when the point was recorded, false when it
     * was rejected as too inaccurate or too close to the previous one.
     */
    fun onFix(fix: Fix, now: Long = fix.timestamp): Boolean {
        val accuracy = fix.accuracyM ?: Float.MAX_VALUE
        if (accuracy > minAccuracyM) {
            // Still worth showing the user that a fix exists, just not recording it.
            state = state.copy(accuracyM = accuracy)
            return false
        }

        tick(now)

        val previous = lastFix
        val segmentM = previous?.let {
            GeoMath.distanceMeters(it.latitude, it.longitude, fix.latitude, fix.longitude)
        } ?: 0.0
        val segmentMs = if (previous != null) (now - lastAcceptedAt).coerceAtLeast(0L) else 0L

        // A fix that teleports faster than any vehicle we care about is noise.
        val implausible = previous != null && segmentMs > 0 &&
            segmentM / (segmentMs / 1000.0) > MAX_PLAUSIBLE_MPS

        val rawSpeed = when {
            speedSource == SpeedSource.GNSS && fix.speedMps != null -> fix.speedMps
            segmentMs > 0 && !implausible -> (segmentM / (segmentMs / 1000.0)).toFloat()
            else -> 0f
        }
        val speed = if (rawSpeed < NOISE_FLOOR_MPS) 0f else rawSpeed

        if (autoPause) applyAutoPause(speed, now)

        if (!paused && previous != null && !implausible && segmentM >= MIN_SEGMENT_M) {
            distanceM += segmentM
        }

        val altitude = fix.altitudeM
        if (!paused && altitude != null) accumulateElevation(altitude, accuracy)

        if (speed > maxSpeedMps) maxSpeedMps = speed.toDouble()

        lastFix = fix
        lastAcceptedAt = now

        if (!paused) {
            _points += TrackPoint(
                timestamp = now,
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitudeM = altitude,
                speedMps = speed,
                accuracyM = accuracy,
                cumulativeDistanceM = distanceM,
            )
        }

        state = state.copy(
            status = if (paused) TrackingStatus.PAUSED else TrackingStatus.RECORDING,
            speedMps = if (paused) 0.0 else speed.toDouble(),
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = averageSpeed(),
            distanceM = distanceM,
            ascentM = ascentM,
            descentM = descentM,
            altitudeM = altitude,
            accuracyM = accuracy,
            bearingDeg = fix.bearingDeg ?: state.bearingDeg,
            latitude = fix.latitude,
            longitude = fix.longitude,
            pointCount = _points.size,
            hasFix = true,
            track = if (paused) state.track else _points.toList(),
        )
        return !paused
    }

    /** Position updates while idle, so the map is already where you are. */
    fun onIdleFix(fix: Fix) {
        state = state.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyM = fix.accuracyM,
            bearingDeg = fix.bearingDeg ?: state.bearingDeg,
            altitudeM = fix.altitudeM,
            speedMps = fix.speedMps
                ?.takeIf { it >= NOISE_FLOOR_MPS }
                ?.toDouble()
                ?: 0.0,
            hasFix = true,
        )
    }

    fun summary(endedAt: Long) = TripSummary(
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = elapsedMs,
        movingTimeMs = movingTimeMs,
        distanceM = distanceM,
        avgSpeedMps = averageSpeed(),
        maxSpeedMps = maxSpeedMps,
        ascentM = ascentM,
        descentM = descentM,
        minAltitudeM = minAltitudeM,
        maxAltitudeM = maxAltitudeM,
    )

    /**
     * Average over moving time when there is any, so a trip that sat at a red
     * light for ten minutes still reports the speed you were actually going.
     */
    private fun averageSpeed(): Double {
        val basis = if (movingTimeMs > 1000L) movingTimeMs else elapsedMs
        if (basis <= 0L) return 0.0
        return distanceM / (basis / 1000.0)
    }

    private fun applyAutoPause(speed: Float, now: Long) {
        if (speed < AUTO_PAUSE_MPS) {
            val since = stillSinceMs ?: now.also { stillSinceMs = it }
            if (!paused && now - since >= AUTO_PAUSE_DELAY_MS) {
                paused = true
            }
        } else {
            stillSinceMs = null
            if (paused) {
                paused = false
                lastTickAt = now
            }
        }
    }

    /**
     * GPS altitude wanders by several metres even standing still, so changes
     * only count once they clear a threshold from the last reference point.
     */
    private fun accumulateElevation(altitude: Double, accuracy: Float) {
        if (accuracy > ELEVATION_MAX_ACCURACY_M) return

        val smoothed = smoothedAltitude
            ?.let { it + (altitude - it) * ELEVATION_SMOOTHING }
            ?: altitude
        smoothedAltitude = smoothed

        minAltitudeM = minOf(minAltitudeM ?: smoothed, smoothed)
        maxAltitudeM = maxOf(maxAltitudeM ?: smoothed, smoothed)

        val reference = altitudeReference
        if (reference == null) {
            altitudeReference = smoothed
            return
        }
        val delta = smoothed - reference
        if (abs(delta) >= ELEVATION_THRESHOLD_M) {
            if (delta > 0) ascentM += delta else descentM += -delta
            altitudeReference = smoothed
        }
    }

    private companion object {
        /** Below this the reading is GPS jitter, not motion. */
        const val NOISE_FLOOR_MPS = 0.6f

        /** Ignore sub-metre wobble so standing still doesn't accrue distance. */
        const val MIN_SEGMENT_M = 1.5

        /** ~1080 km/h — beyond this the fix is a glitch, not a journey. */
        const val MAX_PLAUSIBLE_MPS = 300.0

        const val MOVING_THRESHOLD_MPS = 0.8
        const val AUTO_PAUSE_MPS = 0.7f
        const val AUTO_PAUSE_DELAY_MS = 8_000L

        const val ELEVATION_THRESHOLD_M = 3.0
        const val ELEVATION_SMOOTHING = 0.25
        const val ELEVATION_MAX_ACCURACY_M = 25f
    }
}

data class TripSummary(
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val movingTimeMs: Long,
    val distanceM: Double,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val ascentM: Double,
    val descentM: Double,
    val minAltitudeM: Double?,
    val maxAltitudeM: Double?,
)
