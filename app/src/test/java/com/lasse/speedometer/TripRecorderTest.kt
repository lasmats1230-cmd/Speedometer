package com.lasse.speedometer

import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.tracking.Fix
import com.lasse.speedometer.tracking.TrackingStatus
import com.lasse.speedometer.tracking.TripRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    /** Roughly 100 m of latitude, so hand-built fixes have sane spacing. */
    private val hundredMetresLat = 0.0008993

    private fun fix(
        timestamp: Long,
        latOffset: Double = 0.0,
        speed: Float? = null,
        accuracy: Float = 5f,
        altitude: Double? = null,
    ) = Fix(
        timestamp = timestamp,
        latitude = 48.0 + latOffset,
        longitude = 11.6,
        altitudeM = altitude,
        speedMps = speed,
        accuracyM = accuracy,
    )

    @Test
    fun `a fresh recorder reports idle`() {
        val recorder = TripRecorder()
        assertEquals(TrackingStatus.IDLE, recorder.state.status)
        assertEquals(0.0, recorder.state.distanceM, 0.0)
    }

    @Test
    fun `starting moves to acquiring until the first fix lands`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        assertEquals(TrackingStatus.ACQUIRING, recorder.state.status)

        recorder.onFix(fix(0L, speed = 5f))
        assertEquals(TrackingStatus.RECORDING, recorder.state.status)
    }

    @Test
    fun `fixes worse than the accuracy limit are rejected`() {
        val recorder = TripRecorder(minAccuracyM = 20f)
        recorder.start(0L)

        assertFalse(recorder.onFix(fix(0L, accuracy = 90f)))
        assertEquals(0, recorder.points.size)
        // The reading is still surfaced so the status chip can show it.
        assertEquals(90f, recorder.state.accuracyM)
    }

    @Test
    fun `distance accumulates across accepted fixes`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, latOffset = 0.0))
        recorder.onFix(fix(10_000L, latOffset = hundredMetresLat))

        assertEquals(100.0, recorder.state.distanceM, 2.0)
    }

    @Test
    fun `sub-metre jitter does not accrue distance`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L))
        // About 11 cm north — noise, not travel.
        repeat(20) { index ->
            recorder.onFix(fix(1000L * (index + 1), latOffset = 0.000001 * (index % 2)))
        }
        assertEquals(0.0, recorder.state.distanceM, 0.001)
    }

    @Test
    fun `a teleporting fix is discarded from the distance total`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L))
        // Half a degree of latitude in one second is ~55 km — impossible.
        recorder.onFix(fix(1_000L, latOffset = 0.5))

        assertEquals(0.0, recorder.state.distanceM, 0.001)
    }

    @Test
    fun `speed below the noise floor reads as a standstill`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 0.3f))

        assertEquals(0.0, recorder.state.speedMps, 0.0)
    }

    @Test
    fun `max speed keeps the highest reading`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 8f))
        recorder.onFix(fix(1_000L, latOffset = hundredMetresLat / 10, speed = 28f))
        recorder.onFix(fix(2_000L, latOffset = hundredMetresLat / 5, speed = 12f))

        assertEquals(28.0, recorder.state.maxSpeedMps, 0.001)
    }

    @Test
    fun `computed speed source ignores the reported doppler speed`() {
        val recorder = TripRecorder(speedSource = SpeedSource.COMPUTED)
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 99f))
        // 100 m in 10 s is 10 m/s, whatever the chip claims.
        recorder.onFix(fix(10_000L, latOffset = hundredMetresLat, speed = 99f))

        assertEquals(10.0, recorder.state.speedMps, 0.3)
    }

    @Test
    fun `the timer advances only while unpaused`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.tick(5_000L)
        assertEquals(5_000L, recorder.state.elapsedMs)

        recorder.pause(6_000L)
        recorder.tick(20_000L)
        assertEquals(6_000L, recorder.state.elapsedMs)

        recorder.resume(20_000L)
        recorder.tick(23_000L)
        assertEquals(9_000L, recorder.state.elapsedMs)
    }

    @Test
    fun `pausing stops recording points`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 5f))
        recorder.pause(1_000L)

        assertFalse(recorder.onFix(fix(2_000L, latOffset = hundredMetresLat, speed = 5f)))
        assertEquals(1, recorder.points.size)
        assertEquals(0.0, recorder.state.speedMps, 0.0)
    }

    @Test
    fun `distance does not grow while paused`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L))
        recorder.pause(1_000L)
        recorder.onFix(fix(2_000L, latOffset = hundredMetresLat))

        assertEquals(0.0, recorder.state.distanceM, 0.001)
    }

    @Test
    fun `elevation gain ignores wobble below the threshold`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        // A metre of drift either way should not register as a climb.
        listOf(500.0, 501.0, 500.0, 501.0, 500.0).forEachIndexed { index, altitude ->
            recorder.onFix(
                fix(index * 1000L, latOffset = hundredMetresLat * index, altitude = altitude)
            )
        }
        assertEquals(0.0, recorder.state.ascentM, 0.001)
    }

    @Test
    fun `a sustained climb registers as ascent`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        // Smoothing lags the raw values, so feed a long steady climb.
        (0..40).forEach { index ->
            recorder.onFix(
                fix(
                    timestamp = index * 1000L,
                    latOffset = hundredMetresLat * index,
                    altitude = 500.0 + index * 5.0,
                )
            )
        }
        assertTrue(
            "expected a climb of well over 100 m, got ${recorder.state.ascentM}",
            recorder.state.ascentM > 100.0,
        )
        assertEquals(0.0, recorder.state.descentM, 0.001)
    }

    @Test
    fun `a summary carries the totals over to storage`() {
        val recorder = TripRecorder()
        recorder.start(1_000L)
        recorder.onFix(fix(1_000L, speed = 5f))
        recorder.onFix(fix(11_000L, latOffset = hundredMetresLat, speed = 10f))
        recorder.tick(11_000L)

        val summary = recorder.summary(11_000L)
        assertEquals(1_000L, summary.startedAt)
        assertEquals(11_000L, summary.endedAt)
        assertEquals(100.0, summary.distanceM, 2.0)
        assertEquals(10.0, summary.maxSpeedMps, 0.001)
    }

    @Test
    fun `resetting clears every total`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 5f))
        recorder.onFix(fix(10_000L, latOffset = hundredMetresLat, speed = 5f))
        recorder.reset()

        assertEquals(TrackingStatus.IDLE, recorder.state.status)
        assertEquals(0.0, recorder.state.distanceM, 0.0)
        assertEquals(0, recorder.points.size)
    }

    @Test
    fun `auto pause halts the clock after a stretch of standing still`() {
        val recorder = TripRecorder(autoPause = true)
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 5f))

        // Stationary readings for longer than the auto-pause delay.
        recorder.onFix(fix(1_000L, speed = 0f))
        recorder.onFix(fix(12_000L, speed = 0f))

        assertEquals(TrackingStatus.PAUSED, recorder.state.status)

        // Moving again releases it without the user touching anything.
        recorder.onFix(fix(13_000L, latOffset = hundredMetresLat, speed = 6f))
        assertEquals(TrackingStatus.RECORDING, recorder.state.status)
    }

    @Test
    fun `average speed is based on time spent moving`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 10f))
        recorder.tick(10_000L)
        recorder.onFix(fix(10_000L, latOffset = hundredMetresLat, speed = 10f))

        // 100 m covered over 10 s of movement.
        assertEquals(10.0, recorder.state.avgSpeedMps, 0.5)
    }
}
