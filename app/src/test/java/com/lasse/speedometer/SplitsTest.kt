package com.lasse.speedometer

import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.repo.Splits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitsTest {

    /** A point every [stepM] metres at a steady [speedMps]. */
    private fun track(
        count: Int,
        stepM: Double,
        speedMps: Float,
        start: Long = 0L,
        altitudes: List<Double>? = null,
    ): List<TrackPointEntity> = List(count) { index ->
        val distance = index * stepM
        TrackPointEntity(
            id = index.toLong(),
            tripId = 1,
            timestamp = start + ((distance / speedMps) * 1000).toLong(),
            latitude = 50.0 + index * 0.0001,
            longitude = 8.0,
            altitudeM = altitudes?.getOrNull(index),
            speedMps = speedMps,
            accuracyM = 5f,
            cumulativeDistanceM = distance,
        )
    }

    @Test
    fun `a steady ten kilometres splits into ten equal kilometres`() {
        val splits = Splits.compute(track(count = 1001, stepM = 10.0, speedMps = 10f), 1000.0)

        assertEquals(10, splits.size)
        splits.forEach { split ->
            assertEquals(1000.0, split.distanceM, 0.001)
            assertEquals(100_000L, split.durationMs)
            assertEquals(10.0, split.speedMps, 0.001)
            assertFalse(split.partial)
        }
    }

    @Test
    fun `the boundary time is interpolated between the fixes either side`() {
        // Fixes 400 m apart: the kilometre mark falls halfway through the third.
        val splits = Splits.compute(track(count = 6, stepM = 400.0, speedMps = 10f), 1000.0)

        assertEquals(100_000L, splits.first().durationMs)
        assertEquals(1000.0, splits.first().distanceM, 0.001)
    }

    @Test
    fun `a trailing stretch is reported as a partial split`() {
        val splits = Splits.compute(track(count = 151, stepM = 10.0, speedMps = 10f), 1000.0)

        assertEquals(2, splits.size)
        assertFalse(splits[0].partial)
        assertTrue(splits[1].partial)
        assertEquals(500.0, splits[1].distanceM, 0.001)
    }

    @Test
    fun `a few metres past the last mark is not a split of its own`() {
        val splits = Splits.compute(track(count = 103, stepM = 10.0, speedMps = 10f), 1000.0)

        assertEquals(1, splits.size)
    }

    @Test
    fun `one long gap between fixes can close several splits at once`() {
        val points = listOf(
            TrackPointEntity(
                id = 1, tripId = 1, timestamp = 0L, latitude = 50.0, longitude = 8.0,
                altitudeM = null, speedMps = 10f, accuracyM = 5f, cumulativeDistanceM = 0.0,
            ),
            TrackPointEntity(
                id = 2, tripId = 1, timestamp = 300_000L, latitude = 50.03, longitude = 8.0,
                altitudeM = null, speedMps = 10f, accuracyM = 5f, cumulativeDistanceM = 3000.0,
            ),
        )

        val splits = Splits.compute(points, 1000.0)

        assertEquals(3, splits.size)
        assertEquals(100_000L, splits[0].durationMs)
        assertEquals(100_000L, splits[1].durationMs)
        assertEquals(100_000L, splits[2].durationMs)
    }

    @Test
    fun `climbing is attributed to the split it happened in`() {
        val altitudes = List(101) { index -> if (index <= 50) 100.0 + index * 2 else 200.0 }
        val points = track(count = 101, stepM = 20.0, speedMps = 10f, altitudes = altitudes)

        val splits = Splits.compute(points, 1000.0)

        assertEquals(2, splits.size)
        assertEquals(100.0, splits[0].ascentM, 1.0)
        assertEquals(0.0, splits[1].ascentM, 0.001)
    }

    @Test
    fun `a track with nothing in it has no splits`() {
        assertTrue(Splits.compute(emptyList(), 1000.0).isEmpty())
        assertTrue(Splits.compute(track(count = 1, stepM = 10.0, speedMps = 5f), 1000.0).isEmpty())
    }

    @Test
    fun `miles are just a different unit length`() {
        // 3300 m is two full miles and 81 m over.
        val splits = Splits.compute(track(count = 331, stepM = 10.0, speedMps = 10f), Splits.MILE_M)

        assertEquals(3, splits.size)
        assertEquals(Splits.MILE_M, splits[0].distanceM, 0.001)
        assertEquals(Splits.MILE_M, splits[1].distanceM, 0.001)
        assertTrue(splits[2].partial)
        assertEquals(3300.0 - 2 * Splits.MILE_M, splits[2].distanceM, 0.001)
    }
}
