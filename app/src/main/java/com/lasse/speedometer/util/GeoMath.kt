package com.lasse.speedometer.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /**
     * Great-circle distance in metres.
     *
     * Haversine rather than the ellipsoidal formula: over the tens of metres
     * between consecutive GPS fixes the difference is far below the noise in
     * the fixes themselves, and this keeps the recorder free of Android types.
     */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }
}
