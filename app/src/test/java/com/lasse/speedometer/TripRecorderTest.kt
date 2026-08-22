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
        accuracy: Float = 4f,
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
    fun `the same fix handed in twice is only recorded once`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        val arrival = fix(1_000L, speed = 5f)

        assertTrue(recorder.onFix(arrival, 1_000L))
        // A provider replaying what it could not refresh, or a second
        // provider passing on the same underlying solution.
        assertFalse(recorder.onFix(arrival, 2_000L))
        assertEquals(1, recorder.points.size)
    }

    @Test
    fun `a position repeated verbatim is not a second point`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        // Same coordinates, fresh timestamp each time: the shape a receiver
        // makes when it is handing back a solution it cannot update.
        repeat(12) { index ->
            recorder.onFix(fix(index * 1_000L), index * 1_000L)
        }

        assertEquals(1, recorder.points.size)
    }

    /**
     * The sequence that made a bicycle ride report 134 km/h.
     *
     * Twenty-two seconds of one position written out once a second, then the
     * receiver catching up 148 m along. Divided by the four seconds since the
     * last repeat that is 37 m/s; over the twenty-six seconds the rider was
     * really covering it, it is 5.7.
     */
    @Test
    fun `catching up after a stuck receiver is timed over the whole stretch`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        val stuck = Fix(
            timestamp = 0L,
            latitude = 53.227162,
            longitude = 10.386150,
            accuracyM = 6f,
        )
        recorder.onFix(stuck, 0L)
        for (second in 1..22) {
            recorder.onFix(stuck.copy(timestamp = second * 1_000L), second * 1_000L)
        }
        recorder.onFix(
            Fix(
                timestamp = 26_000L,
                latitude = 53.226685,
                longitude = 10.388232,
                accuracyM = 6f,
            ),
            26_000L,
        )

        assertEquals(5.7, recorder.state.maxSpeedMps, 0.6)
        assertTrue(
            "a stuck receiver must not set the trip maximum: " +
                "${recorder.state.maxSpeedMps * 3.6} km/h",
            recorder.state.maxSpeedMps < 11.0,
        )
        // The stop is one point, not twenty-three.
        assertEquals(2, recorder.points.size)
    }

    @Test
    fun `standing still for a long while does not time the next step`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        // Parked for twenty minutes, the receiver wandering a little.
        recorder.onFix(fix(0L, latOffset = 0.0), 0L)
        for (minute in 1..20) {
            val now = minute * 60_000L
            recorder.onFix(fix(now, latOffset = 0.0000018 * (minute % 2)), now)
        }
        // Then away, 100 m in ten seconds.
        val off = 20 * 60_000L + 10_000L
        recorder.onFix(fix(off, latOffset = hundredMetresLat), off)

        // 10 m/s, not 100 m spread across the whole twenty minutes.
        assertEquals(10.0, recorder.state.maxSpeedMps, 1.0)
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
        // Ramped rather than jumped: a 20 m/s step in one second is beyond
        // any real vehicle, and the plausibility gate rejects it by design.
        listOf(8f, 15f, 22f, 28f, 12f).forEachIndexed { index, speed ->
            recorder.onFix(
                fix(
                    timestamp = index * 1000L,
                    latOffset = hundredMetresLat / 5 * index,
                    speed = speed,
                )
            )
        }

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

/**
 * Regressions from a real recording: 27 minutes parked produced 0.78 km of
 * distance and a top speed of 270.9 km/h.
 */
class StationaryDriftTest {

    private fun fix(
        timestamp: Long,
        latOffset: Double = 0.0,
        lonOffset: Double = 0.0,
        speed: Float? = null,
        speedAccuracy: Float? = null,
        accuracy: Float = 12f,
    ) = Fix(
        timestamp = timestamp,
        latitude = 48.0 + latOffset,
        longitude = 11.6 + lonOffset,
        speedMps = speed,
        speedAccuracyMps = speedAccuracy,
        accuracyM = accuracy,
    )

    /** About 4 m of wander, well inside a ±12 m fix. */
    private val drift = 0.000036

    @Test
    fun `parked for half an hour records no distance`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        // 1 Hz for 30 minutes, jittering back and forth like a stationary fix.
        repeat(1800) { index ->
            recorder.onFix(
                fix(
                    timestamp = index * 1000L,
                    latOffset = if (index % 2 == 0) drift else -drift,
                    lonOffset = if (index % 3 == 0) drift else 0.0,
                    speed = 0.2f,
                )
            )
        }

        assertEquals(0.0, recorder.state.distanceM, 0.001)
        assertEquals(0.0, recorder.state.maxSpeedMps, 0.001)
        assertEquals(0L, recorder.state.movingTimeMs)
    }

    @Test
    fun `parked with no doppler reading still records nothing`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        // Some chips report no speed at all, leaving displacement as the only
        // evidence of motion — and it must not be believed inside the error.
        repeat(600) { index ->
            recorder.onFix(
                fix(
                    timestamp = index * 1000L,
                    latOffset = if (index % 2 == 0) drift else -drift,
                    speed = null,
                )
            )
        }

        assertEquals(0.0, recorder.state.distanceM, 0.001)
        assertEquals(0.0, recorder.state.maxSpeedMps, 0.001)
    }

    @Test
    fun `a doppler spike does not become the trip maximum`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        recorder.onFix(fix(0L, speed = 0f))
        recorder.onFix(fix(1_000L, speed = 0f))

        // 75 m/s is 270 km/h, appearing from a standstill in one second.
        recorder.onFix(fix(2_000L, speed = 75f))

        assertEquals(0.0, recorder.state.maxSpeedMps, 0.001)
    }

    @Test
    fun `a doppler reading the chip does not trust is ignored`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        // Claimed 30 m/s, give or take 40 m/s — meaningless.
        recorder.onFix(fix(0L, speed = 30f, speedAccuracy = 40f))

        assertEquals(0.0, recorder.state.speedMps, 0.001)
    }

    @Test
    fun `real acceleration is still recorded`() {
        val recorder = TripRecorder()
        recorder.start(0L)

        // 0 to 30 m/s over ten seconds — brisk, but a car does this.
        (0..10).forEach { index ->
            recorder.onFix(
                fix(
                    timestamp = index * 1000L,
                    latOffset = 0.00027 * index,
                    speed = 3f * index,
                    speedAccuracy = 0.5f,
                    accuracy = 6f,
                )
            )
        }

        assertEquals(30.0, recorder.state.maxSpeedMps, 0.001)
        // The fixture walks 0.00027° of latitude per step, about 30 m.
        assertTrue(
            "expected real travel to accumulate, got ${recorder.state.distanceM}",
            recorder.state.distanceM > 250.0,
        )
    }

    @Test
    fun `a top speed from a poor fix is not trusted`() {
        val recorder = TripRecorder(minAccuracyM = 100f)
        recorder.start(0L)
        // Plausible acceleration, but the fix itself is 40 m wide.
        (0..10).forEach { index ->
            recorder.onFix(
                fix(timestamp = index * 1000L, speed = 5f * index, accuracy = 40f)
            )
        }

        assertEquals(0.0, recorder.state.maxSpeedMps, 0.001)
    }

    @Test
    fun `genuine walking pace still accumulates distance`() {
        val recorder = TripRecorder()
        recorder.start(0L)
        // 1.4 m/s with a good fix: about 14 m between five-second samples.
        (0..20).forEach { index ->
            recorder.onFix(
                fix(
                    timestamp = index * 5_000L,
                    latOffset = 0.000063 * index,
                    speed = 1.4f,
                    speedAccuracy = 0.4f,
                    accuracy = 5f,
                )
            )
        }

        assertTrue(
            "expected roughly 140 m, got ${recorder.state.distanceM}",
            recorder.state.distanceM in 100.0..180.0,
        )
    }
}
