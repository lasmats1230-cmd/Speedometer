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
enum class StatsPeriod(
    @param:StringRes val labelRes: Int,
    /** How the window before this one is named in a comparison. */
    @param:StringRes val previousLabelRes: Int,
) {
    WEEK(R.string.period_week, R.string.period_last_week),
    MONTH(R.string.period_month, R.string.period_last_month),
    YEAR(R.string.period_year, R.string.period_last_year),
    ALL(R.string.period_all, R.string.period_all),
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

/**
 * What one day already amounts to, and the run-up to it.
 *
 * This is the figure the live view shows before a recording starts: the
 * question there is "have I been out yet, and how is the week going", not the
 * full breakdown the statistics screen exists for.
 */
data class DaySummary(
    val totals: Totals,
    /** Consecutive days ending on this one with at least one recording. */
    val streakDays: Int,
    /** Distance recorded in the week this day falls in. */
    val weekDistanceM: Double,
) {
    /** Nothing today, no streak, nothing this week — better to show nothing. */
    val isEmpty: Boolean
        get() = totals.isEmpty && streakDays == 0 && weekDistanceM <= 0.0
}

/**
 * Something a trip can be the best at.
 *
 * The statistics screen lists these as records; the live view uses them to
 * say so at the moment a trip is saved, which is when it means anything.
 */
enum class RecordKind(@param:StringRes val labelRes: Int, @param:StringRes val beatenRes: Int) {
    DISTANCE(R.string.record_longest, R.string.best_distance),
    DURATION(R.string.record_longest_time, R.string.best_duration),
    SPEED(R.string.record_fastest, R.string.best_speed),
    CLIMB(R.string.record_climb, R.string.best_climb),
}

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

    /**
     * The same window, one period earlier — last week, last month, last year.
     * All time has nothing before it, so it compares with nothing.
     */
    fun previousPeriodTrips(
        trips: List<TripEntity>,
        period: StatsPeriod,
        now: Long,
        zone: ZoneId,
    ): List<TripEntity> {
        if (period == StatsPeriod.ALL) return emptyList()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = when (period) {
            StatsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
            StatsPeriod.MONTH -> today.withDayOfMonth(1)
            StatsPeriod.YEAR -> today.withDayOfYear(1)
            StatsPeriod.ALL -> return emptyList()
        }
        val previousStart = when (period) {
            StatsPeriod.WEEK -> start.minusWeeks(1)
            StatsPeriod.MONTH -> start.minusMonths(1)
            StatsPeriod.YEAR -> start.minusYears(1)
            StatsPeriod.ALL -> return emptyList()
        }
        val from = previousStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = start.atStartOfDay(zone).toInstant().toEpochMilli()
        return trips.filter { it.startedAt in from until until }
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
    fun currentStreak(trips: List<TripEntity>, now: Long, zone: ZoneId): Int =
        streakEndingOn(
            days = trips.map { dateOf(it, zone) }.toSet(),
            today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
        )

    /**
     * Today's totals, the streak they extend, and the week they belong to.
     *
     * Takes the day rather than a clock so a screen that recomposes every
     * second is not recomputing this every second: the date only changes at
     * midnight, and that is what the caller can remember on.
     */
    fun daySummary(trips: List<TripEntity>, today: LocalDate, zone: ZoneId): DaySummary {
        val days = trips.map { dateOf(it, zone) }.toSet()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
        val weekFrom = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        return DaySummary(
            totals = totals(trips.filter { dateOf(it, zone) == today }, zone),
            streakDays = streakEndingOn(days, today),
            // A trip started before the week began but still running into it is
            // not this week's; the start is what files a trip everywhere else.
            weekDistanceM = trips.filter { it.startedAt >= weekFrom }.sumOf { it.distanceM },
        )
    }

    private fun streakEndingOn(days: Set<LocalDate>, today: LocalDate): Int {
        if (days.isEmpty()) return 0
        // Yesterday still counts: a day with no ride yet is not a broken
        // streak until it is over.
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
        fun record(kind: RecordKind, value: (TripEntity) -> String) {
            best(trips, kind)?.let { add(TripRecord(kind.labelRes, it.id, value(it), describe(it))) }
        }
        record(RecordKind.DISTANCE) { formatDistance(it.distanceM) }
        record(RecordKind.DURATION) { formatDuration(it.durationMs) }
        record(RecordKind.SPEED) { formatSpeed(it.maxSpeedMps) }
        record(RecordKind.CLIMB) { formatElevation(it.ascentM) }
    }

    /**
     * Which personal bests [trip] set, measured against everything else
     * recorded.
     *
     * A first trip sets nothing: it is the only one, and telling someone their
     * first ride is their longest, fastest and highest is the sort of praise
     * that stops meaning anything. Ties do not count either — a record has to
     * be beaten, not matched.
     */
    fun personalBests(trip: TripEntity, others: List<TripEntity>): List<RecordKind> {
        if (others.isEmpty()) return emptyList()
        return RecordKind.entries.filter { kind ->
            val value = kind.measure(trip)
            val previous = best(others, kind)?.let { kind.measure(it) }
            kind.counts(value) && previous != null && value > previous
        }
    }

    /** The trip holding the record for [kind], ignoring values too small to count. */
    private fun best(trips: List<TripEntity>, kind: RecordKind): TripEntity? = trips
        .filter { kind.counts(kind.measure(it)) }
        .maxByOrNull { kind.measure(it) }

    /** What the record is measured on, as one comparable number. */
    private fun RecordKind.measure(trip: TripEntity): Double = when (this) {
        RecordKind.DISTANCE -> trip.distanceM
        RecordKind.DURATION -> trip.durationMs.toDouble()
        RecordKind.SPEED -> trip.maxSpeedMps
        RecordKind.CLIMB -> trip.ascentM
    }

    /** Whether a value is a figure at all rather than the noise around zero. */
    private fun RecordKind.counts(value: Double): Boolean = when (this) {
        // A metre of climb is what a stationary GPS reports on its own.
        RecordKind.CLIMB -> value >= 1.0
        else -> value > 0.0
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

    /**
     * How one trip's average speed compares with the others of its kind, as a
     * fraction: 0.12 means twelve per cent faster than usual.
     *
     * Null when there is not enough to compare against — two rides do not make
     * a habit, and "50% faster than your average" off one previous outing is
     * noise dressed up as insight.
     */
    fun comparedWithUsual(trip: TripEntity, trips: List<TripEntity>): Double? {
        val others = trips.filter {
            it.id != trip.id && it.activity == trip.activity && it.avgSpeedMps > 0
        }
        if (others.size < MIN_TRIPS_TO_COMPARE || trip.avgSpeedMps <= 0) return null
        val usual = others.sumOf { it.avgSpeedMps } / others.size
        if (usual <= 0) return null
        return (trip.avgSpeedMps - usual) / usual
    }

    /** Below this the "usual" is one or two outings, not a pattern. */
    private const val MIN_TRIPS_TO_COMPARE = 3

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
