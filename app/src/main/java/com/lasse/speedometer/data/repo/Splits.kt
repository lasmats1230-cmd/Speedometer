package com.lasse.speedometer.data.repo

import com.lasse.speedometer.data.db.TrackPointEntity

/** One kilometre — or one mile — of a trip. */
data class Split(
    /** 1 for the first kilometre. */
    val index: Int,
    val distanceM: Double,
    val durationMs: Long,
    val ascentM: Double,
    /** Average over the split, which is what makes the numbers comparable. */
    val speedMps: Double,
    /** True for a final split that never reached a full unit. */
    val partial: Boolean,
)

/**
 * Cuts a recorded track into equal distances.
 *
 * Splits are how anyone who runs or rides reads a trip back: not "the average
 * was 21 km/h" but "the third kilometre was the slow one". The boundary
 * timestamp is interpolated between the two fixes either side, so a split does
 * not inherit the error of whichever fix happened to land nearest the mark.
 */
object Splits {

    const val KILOMETRE_M = 1000.0
    const val MILE_M = 1609.344

    /** Below this a "split" is a rounding artefact, not a stretch of road. */
    private const val MIN_PARTIAL_M = 50.0

    /** Ignores the metre-by-metre wobble a barometer reports on the flat. */
    private const val ELEVATION_THRESHOLD_M = 1.0

    fun compute(points: List<TrackPointEntity>, unitM: Double): List<Split> {
        if (points.size < 2 || unitM <= 0) return emptyList()

        val splits = mutableListOf<Split>()
        var boundaryDistance = unitM
        var splitStartTime = points.first().timestamp
        var splitStartDistance = 0.0
        var ascent = 0.0
        var altitudeReference = points.first().altitudeM

        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]

            current.altitudeM?.let { altitude ->
                val reference = altitudeReference
                if (reference == null) {
                    altitudeReference = altitude
                } else {
                    val delta = altitude - reference
                    if (delta >= ELEVATION_THRESHOLD_M) {
                        ascent += delta
                        altitudeReference = altitude
                    } else if (delta <= -ELEVATION_THRESHOLD_M) {
                        altitudeReference = altitude
                    }
                }
            }

            // One fix can cross more than one boundary when the signal drops
            // out for a while, so this keeps cutting until it is back inside.
            while (current.cumulativeDistanceM >= boundaryDistance) {
                val span = current.cumulativeDistanceM - previous.cumulativeDistanceM
                val fraction = if (span > 0) {
                    ((boundaryDistance - previous.cumulativeDistanceM) / span).coerceIn(0.0, 1.0)
                } else {
                    1.0
                }
                val crossedAt = previous.timestamp +
                    ((current.timestamp - previous.timestamp) * fraction).toLong()
                val duration = (crossedAt - splitStartTime).coerceAtLeast(0)
                val distance = boundaryDistance - splitStartDistance

                splits += Split(
                    index = splits.size + 1,
                    distanceM = distance,
                    durationMs = duration,
                    ascentM = ascent,
                    speedMps = if (duration > 0) distance / (duration / 1000.0) else 0.0,
                    partial = false,
                )

                splitStartTime = crossedAt
                splitStartDistance = boundaryDistance
                boundaryDistance += unitM
                ascent = 0.0
            }
        }

        val last = points.last()
        val remainder = last.cumulativeDistanceM - splitStartDistance
        if (remainder >= MIN_PARTIAL_M) {
            val duration = (last.timestamp - splitStartTime).coerceAtLeast(0)
            splits += Split(
                index = splits.size + 1,
                distanceM = remainder,
                durationMs = duration,
                ascentM = ascent,
                speedMps = if (duration > 0) remainder / (duration / 1000.0) else 0.0,
                partial = true,
            )
        }

        return splits
    }
}
