package com.lasse.speedometer.data.repo

import com.lasse.speedometer.data.io.RoutePoint
import com.lasse.speedometer.util.GeoMath

/** Where you are along a route you chose to follow. */
data class RouteProgress(
    /** Metres still to cover, measured along the route rather than as the crow flies. */
    val remainingM: Double,
    /** How far off the line you are, in metres. */
    val offRouteM: Double,
    /** How much of the route is behind you, 0..1. */
    val fraction: Float,
) {
    /** Far enough off that it is worth saying so rather than a GPS wobble. */
    val offRoute: Boolean get() = offRouteM > OFF_ROUTE_THRESHOLD_M

    private companion object {
        const val OFF_ROUTE_THRESHOLD_M = 60.0
    }
}

/**
 * Follows a route without navigating it.
 *
 * There is no turn-by-turn here and no route engine — the question this
 * answers is the one you actually ask on a long ride: how much of this is
 * left, and am I still on it. Both come from the nearest point on the line,
 * which is enough for a route you already know the shape of.
 */
object RouteTracker {

    fun progress(route: List<RoutePoint>, latitude: Double, longitude: Double): RouteProgress? {
        if (route.size < 2) return null

        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.forEachIndexed { index, point ->
            val distance = GeoMath.distanceMeters(latitude, longitude, point.latitude, point.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        var remaining = 0.0
        for (index in nearestIndex until route.lastIndex) {
            remaining += GeoMath.distanceMeters(
                route[index].latitude,
                route[index].longitude,
                route[index + 1].latitude,
                route[index + 1].longitude,
            )
        }

        var total = 0.0
        for (index in 0 until route.lastIndex) {
            total += GeoMath.distanceMeters(
                route[index].latitude,
                route[index].longitude,
                route[index + 1].latitude,
                route[index + 1].longitude,
            )
        }

        return RouteProgress(
            remainingM = remaining,
            offRouteM = nearestDistance,
            fraction = if (total > 0) ((total - remaining) / total).toFloat() else 0f,
        )
    }
}
