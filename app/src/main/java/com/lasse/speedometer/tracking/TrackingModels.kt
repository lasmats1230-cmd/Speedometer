package com.lasse.speedometer.tracking

enum class TrackingStatus {
    /** Nothing recorded yet — the play button is showing. */
    IDLE,

    /** Recording started but no fix good enough has arrived. */
    ACQUIRING,

    RECORDING,
    PAUSED,
}

/**
 * A location fix, stripped of Android types.
 *
 * Keeping the recorder's input as plain data means its filtering rules can be
 * tested on the JVM instead of only on a device.
 */
data class Fix(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double? = null,
    /** Doppler speed from the GNSS chip, when it reports one. */
    val speedMps: Float? = null,
    /**
     * The chip's own confidence in [speedMps], in metres per second. A large
     * value means the Doppler solution is guesswork — which is exactly when it
     * invents the spikes that would otherwise become a trip's top speed.
     */
    val speedAccuracyMps: Float? = null,
    val accuracyM: Float? = null,
    val bearingDeg: Float? = null,
)

/** A single recorded fix, held in memory while a trip is in progress. */
data class TrackPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val speedMps: Float,
    val accuracyM: Float,
    val cumulativeDistanceM: Double,
)

/** Everything the live view draws, in SI units. */
data class TrackingState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val speedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val distanceM: Double = 0.0,
    val elapsedMs: Long = 0L,
    val movingTimeMs: Long = 0L,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val altitudeM: Double? = null,
    val accuracyM: Float? = null,
    val bearingDeg: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pointCount: Int = 0,
    val hasFix: Boolean = false,
    /** The track so far, for drawing on the live map. */
    val track: List<TrackPoint> = emptyList(),
) {
    val isActive: Boolean get() = status == TrackingStatus.RECORDING ||
        status == TrackingStatus.PAUSED ||
        status == TrackingStatus.ACQUIRING
}
