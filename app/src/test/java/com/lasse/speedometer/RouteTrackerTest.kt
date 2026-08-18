package com.lasse.speedometer

import com.lasse.speedometer.data.io.RoutePoint
import com.lasse.speedometer.data.repo.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {

    /** Eleven points, about 100 m apart, running north. */
    private val route = List(11) { index ->
        RoutePoint(50.0 + index * 0.0008993, 8.0, null)
    }

    @Test
    fun `at the start the whole route is still ahead`() {
        val progress = RouteTracker.progress(route, 50.0, 8.0)!!

        assertEquals(1000.0, progress.remainingM, 15.0)
        assertEquals(0f, progress.fraction, 0.01f)
        assertFalse(progress.offRoute)
    }

    @Test
    fun `halfway along, half the route is left`() {
        val progress = RouteTracker.progress(route, route[5].latitude, 8.0)!!

        assertEquals(500.0, progress.remainingM, 15.0)
        assertEquals(0.5f, progress.fraction, 0.02f)
    }

    @Test
    fun `at the end nothing is left`() {
        val progress = RouteTracker.progress(route, route.last().latitude, 8.0)!!

        assertEquals(0.0, progress.remainingM, 0.001)
        assertEquals(1f, progress.fraction, 0.01f)
    }

    @Test
    fun `a wobble of a few metres is not being off route`() {
        // About 20 m east of the line.
        val progress = RouteTracker.progress(route, route[3].latitude, 8.00028)!!

        assertFalse(progress.offRoute)
        assertTrue(progress.offRouteM < 60.0)
    }

    @Test
    fun `a few hundred metres away is being off route`() {
        val progress = RouteTracker.progress(route, route[3].latitude, 8.005)!!

        assertTrue(progress.offRoute)
        assertEquals(357.0, progress.offRouteM, 40.0)
    }

    @Test
    fun `a route needs at least two points to follow`() {
        assertNull(RouteTracker.progress(emptyList(), 50.0, 8.0))
        assertNull(RouteTracker.progress(listOf(route.first()), 50.0, 8.0))
    }
}
