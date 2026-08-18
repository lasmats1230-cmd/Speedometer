package com.lasse.speedometer.data.repo

import androidx.annotation.StringRes
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The window the statistics screen is looking at. */
enum class StatsPeriod(@param:StringRes val labelRes: Int) {
    WEEK(R.string.period_week),
    MONTH(R.string.period_month),
    YEAR(R.string.period_year),
    ALL(R.string.period_all),
}

/** Everything totalled over a set of trips. */
data class Totals(
    val trips: Int = 0,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0L,
    val movingTimeMs: Long = 0L,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    /** Distance over moving time — the figure a rider recognises as "my average". */
    val avgSpeedMps: Double = 0.0,
    /** Distinct calendar days with at least one recording. */
    val activeDays: Int = 0,
) {
    val isEmpty: Boolean get() = trips == 0
}

/** One activity's share of a period. */
data class ActivityBreakdown(
    val activity: ActivityType,
    val totals: Totals,
    /** Share of the period's distance, 0..1. */
    val distanceShare: Float,
)

/** One column of the trend chart. */
data class TrendBucket(
    val label: String,
    val distanceM: Double,
    val trips: Int,
    /** True for the bucket the current day falls in. */
    val current: Boolean = false,
)

/** The best a trip has ever been at something. */
data class TripRecord(
    @param:StringRes val labelRes: Int,
    val tripId: Long,
    val value: String,
    val subtitle: String,
)

/**
 * Turns a list of trips into the figures the statistics screen draws.
 *
 * Pure functions over plain entities, with the clock and time zone passed in,
 * so every boundary case — a ride that starts on Sunday night, a year with 53
 * weeks — can be pinned down in a unit test rather than by scrolling back
 * through history on a phone.
 */
object StatsCalculator {

    /** Trips inside [period], newest first, measured against [now]. */
    fun tripsIn(
        trips: List<TripEntity>,
        period: StatsPeriod,
        now: Long,
        zone: ZoneId,
    ): List<TripEntity> {
        val from = periodStart(period, now, zone) ?: return trips
        return trips.filter { it.startedAt >= from }
    }

    /** Epoch millis the period begins at, or null for all time. */
    fun periodStart(period: StatsPeriod, now: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = when (period) {
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
            StatsPeriod.MONTH -> today.withDayOfMonth(1)
            StatsPeriod.YEAR -> today.withDayOfYear(1)
            StatsPeriod.ALL -> return null
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun totals(trips: List<TripEntity>, zone: ZoneId): Totals {
        if (trips.isEmpty()) return Totals()
        val distance = trips.sumOf { it.distanceM }
        val moving = trips.sumOf { it.movingTimeMs }
        return Totals(
            trips = trips.size,
            distanceM = distance,
            durationMs = trips.sumOf { it.durationMs },
            movingTimeMs = moving,
            ascentM = trips.sumOf { it.ascentM },
            descentM = trips.sumOf { it.descentM },
            maxSpeedMps = trips.maxOf { it.maxSpeedMps },
            avgSpeedMps = if (moving > 0) distance / (moving / 1000.0) else 0.0,
            activeDays = trips.map { dateOf(it, zone) }.distinct().size,
        )
    }

    /** Per-activity totals, biggest first, skipping the ones with no trips. */
    fun breakdown(trips: List<TripEntity>, zone: ZoneId): List<ActivityBreakdown> {
        val total = trips.sumOf { it.distanceM }
        return trips.groupBy { it.activityType }
            .map { (activity, group) ->
                val groupTotals = totals(group, zone)
                ActivityBreakdown(
                    activity = activity,
                    totals = groupTotals,
                    distanceShare = if (total > 0) {
                        (groupTotals.distanceM / total).toFloat()
                    } else {
                        0f
                    },
                )
            }
            .sortedByDescending { it.totals.distanceM }
    }

    /**
     * The trend chart's columns: days across a week, weeks across a month,
     * months across a year, years across everything. Empty buckets are kept —
     * a week with two rides and five blanks is the point of the chart.
     */
    fun trend(
        trips: List<TripEntity>,
        period: StatsPeriod,
        now: Long,
        zone: ZoneId,
    ): List<TrendBucket> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (period) {
            StatsPeriod.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
                List(7) { offset ->
                    val day = start.plusDays(offset.toLong())
                    bucket(
                        label = dayInitial(day),
                        trips = trips.filter { dateOf(it, zone) == day },
                        current = day == today,
                    )
                }
            }

            StatsPeriod.MONTH -> {
                val start = today.withDayOfMonth(1)
                val length = today.lengthOfMonth()
                // Calendar weeks would leave a stub at each end; even blocks of
                // seven days read as "week one, week two" without the ragged edge.
                val blocks = (length + 6) / 7
                List(blocks) { index ->
                    val from = start.plusDays(index * 7L)
                    val to = minOf(from.plusDays(6), start.plusDays(length - 1L))
                    bucket(
                        label = "${from.dayOfMonth}–${to.dayOfMonth}",
                        trips = trips.filter { dateOf(it, zone) in from..to },
                        current = today in from..to,
                    )
                }
            }

            StatsPeriod.YEAR -> {
                val start = today.withDayOfYear(1)
                List(12) { index ->
                    val month = start.plusMonths(index.toLong())
                    bucket(
                        label = monthInitial(month),
                        trips = trips.filter {
                            val date = dateOf(it, zone)
                            date.year == month.year && date.monthValue == month.monthValue
                        },
                        current = month.monthValue == today.monthValue &&
                            month.year == today.year,
                    )
                }
            }

            StatsPeriod.ALL -> {
                val years = trips.map { dateOf(it, zone).year }
                val first = years.minOrNull() ?: today.year
                val last = maxOf(years.maxOrNull() ?: today.year, today.year)
                (first..last).map { year ->
                    bucket(
                        label = year.toString(),
                        trips = trips.filter { dateOf(it, zone).year == year },
                        current = year == today.year,
                    )
                }
            }
        }
    }

    /**
     * Consecutive days up to today with a recording. Yesterday still counts as
     * a live streak — the day is not over until it is.
     */
    fun currentStreak(trips: List<TripEntity>, now: Long, zone: ZoneId): Int {
        if (trips.isEmpty()) return 0
        val days = trips.map { dateOf(it, zone) }.toSet()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Longest, fastest, highest — with the trip each belongs to. */
    fun records(
        trips: List<TripEntity>,
        formatDistance: (Double) -> String,
        formatDuration: (Long) -> String,
        formatSpeed: (Double) -> String,
        formatElevation: (Double) -> String,
        describe: (TripEntity) -> String,
    ): List<TripRecord> = buildList {
        trips.filter { it.distanceM > 0 }.maxByOrNull { it.distanceM }?.let {
            add(
                TripRecord(
                    R.string.record_longest,
                    it.id,
                    formatDistance(it.distanceM),
                    describe(it),
                )
            )
        }
        trips.filter { it.durationMs > 0 }.maxByOrNull { it.durationMs }?.let {
            add(
                TripRecord(
                    R.string.record_longest_time,
                    it.id,
                    formatDuration(it.durationMs),
                    describe(it),
                )
            )
        }
        trips.filter { it.maxSpeedMps > 0 }.maxByOrNull { it.maxSpeedMps }?.let {
            add(
                TripRecord(
                    R.string.record_fastest,
                    it.id,
                    formatSpeed(it.maxSpeedMps),
                    describe(it),
                )
            )
        }
        trips.filter { it.ascentM >= 1 }.maxByOrNull { it.ascentM }?.let {
            add(
                TripRecord(
                    R.string.record_climb,
                    it.id,
                    formatElevation(it.ascentM),
                    describe(it),
                )
            )
        }
    }

    /**
     * How far into the week today is, 1 for the first day. Used to say whether
     * a weekly goal is on track rather than only how much of it is left.
     */
    fun dayOfWeek(now: Long, zone: ZoneId): Int {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
        return (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, 7)
    }

    /** Days since the first recording, for the lifetime card. */
    fun daysSinceFirst(trips: List<TripEntity>, now: Long, zone: ZoneId): Long {
        val first = trips.minByOrNull { it.startedAt } ?: return 0
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(dateOf(first, zone), today).coerceAtLeast(0)
    }

    private fun bucket(label: String, trips: List<TripEntity>, current: Boolean) = TrendBucket(
        label = label,
        distanceM = trips.sumOf { it.distanceM },
        trips = trips.size,
        current = current,
    )

    private fun dateOf(trip: TripEntity, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(trip.startedAt).atZone(zone).toLocalDate()

    /**
     * Weeks start where the user's locale puts them, so a German phone reads
     * Monday first and an American one Sunday.
     */
    private fun firstDayOfWeek() =
        java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek

    private fun dayInitial(date: LocalDate): String = date
        .dayOfWeek
        .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())

    private fun monthInitial(date: LocalDate): String = date
        .month
        .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
}
