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

    /**
     * A route with the distance to each of its points worked out once.
     *
     * The live view asks for progress on every fix, about once a second, and
     * it asks during composition — on the main thread. Measuring the whole
     * route each time meant three passes over what can be twenty thousand
     * points for a long GPX. The route does not change between fixes, so
     * neither does any of that.
     */
    class Prepared internal constructor(
        internal val points: List<RoutePoint>,
        /** Metres from the start to each point; the last entry is the total. */
        internal val cumulativeM: DoubleArray,
    ) {
        val totalM: Double get() = cumulativeM.last()
    }

    /** Null for anything too short to be a route. */
    fun prepare(route: List<RoutePoint>): Prepared? {
        if (route.size < 2) return null
        val cumulative = DoubleArray(route.size)
        for (index in 1 until route.size) {
            cumulative[index] = cumulative[index - 1] + GeoMath.distanceMeters(
                route[index - 1].latitude,
                route[index - 1].longitude,
                route[index].latitude,
                route[index].longitude,
            )
        }
        return Prepared(route, cumulative)
    }

    fun progress(route: Prepared, latitude: Double, longitude: Double): RouteProgress {
        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.points.forEachIndexed { index, point ->
            val distance =
                GeoMath.distanceMeters(latitude, longitude, point.latitude, point.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }

        val total = route.totalM
        val remaining = total - route.cumulativeM[nearestIndex]
        return RouteProgress(
            remainingM = remaining,
            offRouteM = nearestDistance,
            fraction = if (total > 0) ((total - remaining) / total).toFloat() else 0f,
        )
    }

    /** The same answer from a raw route, for a one-off question. */
    fun progress(route: List<RoutePoint>, latitude: Double, longitude: Double): RouteProgress? =
        prepare(route)?.let { progress(it, latitude, longitude) }
}
