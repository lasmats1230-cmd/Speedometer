package com.lasse.speedometer

import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.repo.StatsCalculator
import com.lasse.speedometer.data.repo.StatsPeriod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val originalLocale = Locale.getDefault()

    /** Wednesday, 15 April 2026, 12:00 UTC. */
    private val now = at(2026, 4, 15, 12)

    @Before
    fun fixLocale() {
        // The week starts where the locale says it does; Germany pins it to
        // Monday so the day buckets are the same on any machine.
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun trip(
        startedAt: Long,
        distanceM: Double = 10_000.0,
        durationMs: Long = 3_600_000,
        movingTimeMs: Long = 3_600_000,
        maxSpeedMps: Double = 10.0,
        ascentM: Double = 100.0,
        activity: ActivityType = ActivityType.RIDE,
        id: Long = startedAt / 1000,
    ) = TripEntity(
        id = id,
        startedAt = startedAt,
        endedAt = startedAt + durationMs,
        durationMs = durationMs,
        movingTimeMs = movingTimeMs,
        distanceM = distanceM,
        avgSpeedMps = distanceM / (movingTimeMs / 1000.0),
        maxSpeedMps = maxSpeedMps,
        ascentM = ascentM,
        descentM = ascentM,
        minAltitudeM = null,
        maxAltitudeM = null,
        activity = activity.name,
    )

    @Test
    fun `this week starts on Monday and excludes last Sunday`() {
        val monday = at(2026, 4, 13, 6)
        val lastSunday = at(2026, 4, 12, 20)
        val trips = listOf(trip(monday), trip(lastSunday))

        val inWeek = StatsCalculator.tripsIn(trips, StatsPeriod.WEEK, now, zone)

        assertEquals(1, inWeek.size)
        assertEquals(monday, inWeek.single().startedAt)
    }

    @Test
    fun `month and year windows cut at their own boundaries`() {
        val trips = listOf(
            trip(at(2026, 4, 1, 0)),
            trip(at(2026, 3, 31, 23)),
            trip(at(2025, 12, 31, 23)),
        )

        assertEquals(1, StatsCalculator.tripsIn(trips, StatsPeriod.MONTH, now, zone).size)
        assertEquals(2, StatsCalculator.tripsIn(trips, StatsPeriod.YEAR, now, zone).size)
        assertEquals(3, StatsCalculator.tripsIn(trips, StatsPeriod.ALL, now, zone).size)
    }

    @Test
    fun `totals add up and the average is over moving time`() {
        val trips = listOf(
            trip(at(2026, 4, 13, 6), distanceM = 20_000.0, movingTimeMs = 2_000_000),
            trip(at(2026, 4, 14, 6), distanceM = 10_000.0, movingTimeMs = 1_000_000),
        )

        val totals = StatsCalculator.totals(trips, zone)

        assertEquals(2, totals.trips)
        assertEquals(30_000.0, totals.distanceM, 0.001)
        assertEquals(10.0, totals.avgSpeedMps, 0.001)
        assertEquals(2, totals.activeDays)
    }

    @Test
    fun `two trips on one day count as one active day`() {
        val trips = listOf(trip(at(2026, 4, 13, 6)), trip(at(2026, 4, 13, 18)))

        assertEquals(1, StatsCalculator.totals(trips, zone).activeDays)
    }

    @Test
    fun `the breakdown is ordered by distance and shares sum to one`() {
        val trips = listOf(
            trip(at(2026, 4, 13, 6), distanceM = 30_000.0, activity = ActivityType.RIDE),
            trip(at(2026, 4, 14, 6), distanceM = 10_000.0, activity = ActivityType.RUN),
        )

        val breakdown = StatsCalculator.breakdown(trips, zone)

        assertEquals(listOf(ActivityType.RIDE, ActivityType.RUN), breakdown.map { it.activity })
        assertEquals(0.75f, breakdown[0].distanceShare, 0.001f)
        assertEquals(1f, breakdown.sumOf { it.distanceShare.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `a week has seven buckets with the ride on its own day`() {
        val trips = listOf(trip(at(2026, 4, 14, 6), distanceM = 12_000.0))

        val buckets = StatsCalculator.trend(trips, StatsPeriod.WEEK, now, zone)

        assertEquals(7, buckets.size)
        assertEquals(12_000.0, buckets[1].distanceM, 0.001)
        assertEquals(0.0, buckets[0].distanceM, 0.001)
        assertTrue(buckets[2].current)
    }

    @Test
    fun `a year has twelve buckets and all time has one per year`() {
        val trips = listOf(trip(at(2024, 6, 1, 6)), trip(at(2026, 4, 1, 6)))

        assertEquals(12, StatsCalculator.trend(trips, StatsPeriod.YEAR, now, zone).size)
        assertEquals(3, StatsCalculator.trend(trips, StatsPeriod.ALL, now, zone).size)
    }

    @Test
    fun `a month is covered by whole blocks of seven days`() {
        val buckets = StatsCalculator.trend(emptyList(), StatsPeriod.MONTH, now, zone)

        assertEquals(5, buckets.size)
        assertEquals("1–7", buckets.first().label)
        assertEquals("29–30", buckets.last().label)
    }

    @Test
    fun `a streak runs back from today`() {
        val trips = listOf(
            trip(at(2026, 4, 15, 6)),
            trip(at(2026, 4, 14, 6)),
            trip(at(2026, 4, 13, 6)),
            trip(at(2026, 4, 11, 6)),
        )

        assertEquals(3, StatsCalculator.currentStreak(trips, now, zone))
    }

    @Test
    fun `yesterday still counts as a live streak`() {
        val trips = listOf(trip(at(2026, 4, 14, 6)), trip(at(2026, 4, 13, 6)))

        assertEquals(2, StatsCalculator.currentStreak(trips, now, zone))
    }

    @Test
    fun `a gap of two days ends the streak`() {
        val trips = listOf(trip(at(2026, 4, 12, 6)), trip(at(2026, 4, 11, 6)))

        assertEquals(0, StatsCalculator.currentStreak(trips, now, zone))
    }

    @Test
    fun `records point at the trip that set them`() {
        val longest = trip(at(2026, 4, 13, 6), distanceM = 90_000.0, id = 1)
        val fastest = trip(at(2026, 4, 14, 6), maxSpeedMps = 22.0, id = 2)
        val climb = trip(at(2026, 4, 15, 6), ascentM = 1_400.0, id = 3)

        val records = StatsCalculator.records(
            trips = listOf(longest, fastest, climb),
            formatDistance = { "${it.toInt()} m" },
            formatDuration = { "$it ms" },
            formatSpeed = { "${it.toInt()} m/s" },
            formatElevation = { "${it.toInt()} m" },
            describe = { "trip ${it.id}" },
        )

        assertEquals(4, records.size)
        assertEquals(1L, records[0].tripId)
        assertEquals("90000 m", records[0].value)
        assertEquals(2L, records[2].tripId)
        assertEquals(3L, records[3].tripId)
    }

    @Test
    fun `nothing recorded means no records and empty totals`() {
        assertTrue(StatsCalculator.totals(emptyList(), zone).isEmpty)
        assertEquals(0, StatsCalculator.currentStreak(emptyList(), now, zone))
        assertEquals(0L, StatsCalculator.daysSinceFirst(emptyList(), now, zone))
    }

    @Test
    fun `days since the first recording counts calendar days`() {
        val trips = listOf(trip(at(2026, 4, 1, 6)), trip(at(2026, 4, 14, 6)))

        assertEquals(14L, StatsCalculator.daysSinceFirst(trips, now, zone))
    }

    @Test
    fun `the day summary counts today, the streak and the week`() {
        val trips = listOf(
            // Today, twice.
            trip(at(2026, 4, 15, 7), distanceM = 5_000.0, movingTimeMs = 1_200_000),
            trip(at(2026, 4, 15, 18), distanceM = 8_000.0, movingTimeMs = 1_800_000),
            // Yesterday and the day before, so the streak runs to three.
            trip(at(2026, 4, 14, 8), distanceM = 12_000.0),
            trip(at(2026, 4, 13, 8), distanceM = 3_000.0),
            // The Saturday before: same month, previous week.
            trip(at(2026, 4, 11, 8), distanceM = 40_000.0),
        )

        val summary = StatsCalculator.daySummary(trips, today, zone)

        assertEquals(2, summary.totals.trips)
        assertEquals(13_000.0, summary.totals.distanceM, 0.001)
        assertEquals(3_000_000L, summary.totals.movingTimeMs)
        assertEquals(3, summary.streakDays)
        // Monday the 13th onwards; the Saturday before is another week.
        assertEquals(28_000.0, summary.weekDistanceM, 0.001)
    }

    @Test
    fun `a day with nothing on it still reports the streak it sits on`() {
        val trips = listOf(trip(at(2026, 4, 14, 8), distanceM = 12_000.0))

        val summary = StatsCalculator.daySummary(trips, today, zone)

        assertTrue(summary.totals.isEmpty)
        // Yesterday counts: today is not over yet.
        assertEquals(1, summary.streakDays)
        assertEquals(12_000.0, summary.weekDistanceM, 0.001)
        assertTrue(!summary.isEmpty)
    }

    @Test
    fun `no history at all means there is nothing to show`() {
        assertTrue(StatsCalculator.daySummary(emptyList(), today, zone).isEmpty)
    }

    @Test
    fun `a gap of one whole day breaks the streak`() {
        val trips = listOf(
            trip(at(2026, 4, 13, 8)),
            // Nothing on the 14th, and nothing today.
            trip(at(2026, 4, 10, 8)),
        )

        assertEquals(0, StatsCalculator.daySummary(trips, today, zone).streakDays)
    }

    private val today: LocalDate = LocalDate.of(2026, 4, 15)
}
