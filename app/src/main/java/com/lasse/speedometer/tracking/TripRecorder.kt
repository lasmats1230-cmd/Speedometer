package com.lasse.speedometer.tracking

import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.util.GeoMath
import kotlin.math.abs
import kotlin.math.max

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

    /**
     * The last fix that was somewhere else, and the clock reading when it was
     * accepted.
     *
     * Not simply the previous fix: a receiver that cannot refresh its
     * solution hands the old position back unchanged, so the previous fix is
     * often this same spot a second later. Anchoring on the last place we
     * were genuinely at means the next real step is divided by the time we
     * were really there — twenty-two seconds parked and then a 148 m step is
     * 20 km/h, not the 134 km/h that dividing by the last repeat produces.
     */
    private var anchorFix: Fix? = null
    private var anchorAcceptedAt: Long = 0L

    /** The last fix handed in at all, to recognise one delivered twice. */
    private var previousDelivery: Fix? = null

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

    /** Last speed the plausibility gate accepted, in metres per second. */
    private var lastSpeedMps = 0f

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
        anchorFix = null
        anchorAcceptedAt = 0L
        previousDelivery = null
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
        lastSpeedMps = 0f
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
        anchorFix = null // don't bridge the gap we sat out
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
        // The same solution handed in twice — a provider replaying a fix it
        // could not refresh, or two providers passing on one underlying
        // reading. Nothing about it is new, and left in it lands as a second
        // point in the same place a second later.
        val replay = previousDelivery?.let {
            it.timestamp == fix.timestamp &&
                it.latitude == fix.latitude &&
                it.longitude == fix.longitude
        } ?: false
        previousDelivery = fix
        if (replay) return false

        val accuracy = fix.accuracyM ?: Float.MAX_VALUE
        if (accuracy > minAccuracyM) {
            // Still worth showing the user that a fix exists, just not recording it.
            state = state.copy(accuracyM = accuracy)
            return false
        }

        tick(now)

        val previous = anchorFix
        val segmentM = previous?.let {
            GeoMath.distanceMeters(it.latitude, it.longitude, fix.latitude, fix.longitude)
        } ?: 0.0
        val segmentMs = if (previous != null) (now - anchorAcceptedAt).coerceAtLeast(0L) else 0L

        // A fix that teleports faster than any vehicle we care about is noise.
        val implausible = previous != null && segmentMs > 0 &&
            segmentM / (segmentMs / 1000.0) > MAX_PLAUSIBLE_MPS

        // How far the fix has to move before the step counts as travel rather
        // than the fix wandering inside its own error circle.
        val minSegment = max(MIN_SEGMENT_M, accuracy * DRIFT_TOLERANCE)
        val movedFarEnough = previous != null && !implausible && segmentM >= minSegment

        // Without a Doppler reading, displacement is the only evidence of
        // motion — and displacement smaller than the error circle is no
        // evidence at all, so the bar is higher here than for distance.
        val computedTrusted = segmentM >= max(MIN_SEGMENT_M, accuracy * COMPUTED_DRIFT_TOLERANCE)
        val computedSpeed = if (segmentMs > 0 && !implausible && computedTrusted) {
            (segmentM / (segmentMs / 1000.0)).toFloat()
        } else {
            0f
        }

        // The chip's Doppler speed is preferred, but only while it says it
        // trusts itself. A poor solution falls back to distance over time.
        val dopplerUsable = fix.speedMps != null &&
            (fix.speedAccuracyMps == null || fix.speedAccuracyMps <= MAX_SPEED_ACCURACY_MPS)

        val rawSpeed = when {
            speedSource == SpeedSource.GNSS && dopplerUsable -> fix.speedMps!!
            else -> computedSpeed
        }
        val floored = if (rawSpeed < NOISE_FLOOR_MPS) 0f else rawSpeed

        // Nothing on a road changes speed this fast. Rejecting the reading
        // rather than clamping it keeps one bad sample out of the maximum,
        // which is otherwise a number the whole trip is remembered by.
        val elapsedSeconds = segmentMs / 1000.0
        val speed = if (
            previous != null &&
            elapsedSeconds > 0 &&
            abs(floored - lastSpeedMps) / elapsedSeconds > MAX_ACCELERATION_MPS2
        ) {
            lastSpeedMps
        } else {
            floored
        }

        if (autoPause) applyAutoPause(speed, now)

        // Distance needs both: a step bigger than the fix's own error, and a
        // speed saying you were actually moving. Displacement alone spent 27
        // parked minutes accumulating 0.78 km, because a stationary fix
        // wanders several metres between samples while the speed reads zero.
        if (!paused && movedFarEnough && speed >= NOISE_FLOOR_MPS) {
            distanceM += segmentM
        }

        val altitude = fix.altitudeM
        if (!paused && altitude != null) accumulateElevation(altitude, accuracy)

        // Only a fix good enough to believe may set a new record.
        if (speed > maxSpeedMps && accuracy <= MAX_SPEED_FIX_ACCURACY_M) {
            maxSpeedMps = speed.toDouble()
        }
        lastSpeedMps = speed

        // The anchor moves only when the position did — a fix repeating a
        // place the receiver could not refresh leaves it where it was, so the
        // step out of it is timed over the stretch it really took. Held past
        // the stale limit it stops being a stuck receiver and becomes a stop,
        // and then the clock has to come forward or setting off again is
        // timed from whenever you arrived.
        val stale = previous != null && now - anchorAcceptedAt > MAX_STALE_MS
        if (previous == null || movedFarEnough || stale) {
            anchorFix = fix
            anchorAcceptedAt = now
        }

        // A position repeated verbatim is not a new place to draw. Runs of
        // them turn a stop into a pile of identical points, and make the step
        // that follows look instantaneous to anything reading the track back.
        val samePlace = _points.lastOrNull()
            ?.let { it.latitude == fix.latitude && it.longitude == fix.longitude }
            ?: false

        val recorded = !paused && !samePlace
        if (recorded) {
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
        return recorded
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

        /** Floor for the distance gate, however good the fix claims to be. */
        const val MIN_SEGMENT_M = 3.0

        /**
         * A step must clear this fraction of the fix's own error before it
         * counts as travel. Half a metre of movement inside a ±20 m fix is
         * indistinguishable from the fix wandering.
         */
        const val DRIFT_TOLERANCE = 0.5

        /**
         * The stricter bar displacement must clear to stand in for a speed
         * reading. Erring toward under-counting here is the right trade: a
         * device with no Doppler solution should lose a little distance rather
         * than invent kilometres while parked.
         */
        const val COMPUTED_DRIFT_TOLERANCE = 1.0

        /** ~1080 km/h — beyond this the fix is a glitch, not a journey. */
        const val MAX_PLAUSIBLE_MPS = 300.0

        /**
         * How long the anchor holds on a position that will not move.
         *
         * A receiver losing its solution for a few seconds while you ride on
         * and a bicycle propped against a wall look the same from here, so
         * the length of the stretch decides between them. Under a minute the
         * ride carried on through it; longer and you had stopped.
         */
        const val MAX_STALE_MS = 60_000L

        /**
         * Roughly 0-100 km/h in three and a half seconds; quicker than any
         * road vehicle sustains, so anything above it is a bad sample.
         */
        const val MAX_ACCELERATION_MPS2 = 8.0f

        /** Beyond this the chip's own speed estimate is not worth having. */
        const val MAX_SPEED_ACCURACY_MPS = 2.0f

        /** A record top speed has to come from a fix worth believing. */
        const val MAX_SPEED_FIX_ACCURACY_M = 25f

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
