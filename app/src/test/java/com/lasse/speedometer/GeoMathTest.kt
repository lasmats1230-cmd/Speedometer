package com.lasse.speedometer

import com.lasse.speedometer.data.io.RouteParser
import com.lasse.speedometer.data.io.RoutePoint
import com.lasse.speedometer.util.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `the same point is zero metres away`() {
        assertEquals(0.0, GeoMath.distanceMeters(48.0, 11.6, 48.0, 11.6), 0.0001)
    }

    @Test
    fun `one degree of latitude is about 111 kilometres`() {
        val distance = GeoMath.distanceMeters(48.0, 11.6, 49.0, 11.6)
        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun `longitude spacing narrows away from the equator`() {
        val atEquator = GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0)
        val atMunich = GeoMath.distanceMeters(48.0, 11.0, 48.0, 12.0)
        assertTrue(atMunich < atEquator)
        // cos(48°) is about 0.669.
        assertEquals(atEquator * 0.669, atMunich, 1_000.0)
    }

    @Test
    fun `distance is symmetric`() {
        val there = GeoMath.distanceMeters(48.1, 11.5, 48.2, 11.7)
        val back = GeoMath.distanceMeters(48.2, 11.7, 48.1, 11.5)
        assertEquals(there, back, 0.0001)
    }
    @Test
    fun `bearings read clockwise from north`() {
        // Due north, east, south and west of a point near the equator.
        assertEquals(0f, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.5f)
        assertEquals(90f, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.5f)
        assertEquals(180f, GeoMath.bearingDegrees(1.0, 0.0, 0.0, 0.0), 0.5f)
        assertEquals(270f, GeoMath.bearingDegrees(0.0, 1.0, 0.0, 0.0), 0.5f)
    }

    @Test
    fun `a bearing is never negative`() {
        val bearing = GeoMath.bearingDegrees(50.0, 8.0, 49.9, 7.9)

        assertTrue(bearing >= 0f && bearing < 360f)
        // South-west of the start, as the coordinates say.
        assertEquals(212.8f, bearing, 0.5f)
    }
}

class RouteEncodingTest {

    @Test
    fun `points survive a round trip through the encoding`() {
        val points = listOf(
            RoutePoint(48.1, 11.5, 520.0),
            RoutePoint(48.2, 11.6, null),
        )
        val decoded = RouteParser.decode(RouteParser.encode(points))

        assertEquals(2, decoded.size)
        assertEquals(48.1, decoded[0].latitude, 1e-9)
        assertEquals(11.5, decoded[0].longitude, 1e-9)
        assertEquals(520.0, decoded[0].altitudeM!!, 1e-9)
        assertNull(decoded[1].altitudeM)
    }

    @Test
    fun `an empty string decodes to no points`() {
        assertTrue(RouteParser.decode("").isEmpty())
    }

    @Test
    fun `malformed chunks are skipped rather than crashing`() {
        val decoded = RouteParser.decode("48.1,11.5,500;garbage;48.2,11.6,")
        assertEquals(2, decoded.size)
    }
}
