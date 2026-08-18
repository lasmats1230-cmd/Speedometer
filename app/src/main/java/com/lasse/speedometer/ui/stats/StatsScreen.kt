package com.lasse.speedometer.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.repo.ActivityBreakdown
import com.lasse.speedometer.data.repo.StatsCalculator
import com.lasse.speedometer.data.repo.StatsPeriod
import com.lasse.speedometer.data.repo.Totals
import com.lasse.speedometer.data.repo.TripRecord
import com.lasse.speedometer.ui.components.BarChart
import com.lasse.speedometer.ui.components.BarSample
import com.lasse.speedometer.ui.components.CenteredEmptyState
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.ProgressRing
import com.lasse.speedometer.ui.components.ScreenTitle
import com.lasse.speedometer.ui.components.SectionCard
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.icon
import com.lasse.speedometer.ui.history.formatTripDate
import com.lasse.speedometer.util.Formatters
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * What the recordings add up to.
 *
 * History answers "what did I do on Tuesday"; this screen answers "how much
 * have I been riding lately", which is the question that keeps someone opening
 * the app on a day they did not go out.
 */
@Composable
fun StatsScreen(
    settings: AppSettings,
    onOpenTrip: (Long) -> Unit,
    onStartRecording: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val trips by viewModel.trips.collectAsState()
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }

    val zone = remember { ZoneId.systemDefault() }
    // Fixed at composition rather than read per frame: every figure below is
    // measured against the same instant, and a ticking "now" would recompute
    // the whole screen once a second for no visible change.
    val now = remember(trips) { System.currentTimeMillis() }

    val units = settings.units
    val periodTrips = remember(trips, period, now) {
        StatsCalculator.tripsIn(trips, period, now, zone)
    }
    val totals = remember(periodTrips) { StatsCalculator.totals(periodTrips, zone) }
    val previous = remember(trips, period, now) {
        StatsCalculator.totals(
            StatsCalculator.previousPeriodTrips(trips, period, now, zone),
            zone,
        )
    }
    val breakdown = remember(periodTrips) { StatsCalculator.breakdown(periodTrips, zone) }
    val trend = remember(periodTrips, period, now) {
        StatsCalculator.trend(periodTrips, period, now, zone)
    }
    val lifetime = remember(trips) { StatsCalculator.totals(trips, zone) }
    // The goal is always about this week, whichever period is on screen.
    val weekTotals = remember(trips, now) {
        StatsCalculator.totals(StatsCalculator.tripsIn(trips, StatsPeriod.WEEK, now, zone), zone)
    }
    val dayOfWeek = remember(now) { StatsCalculator.dayOfWeek(now, zone) }
    val streak = remember(trips, now) { StatsCalculator.currentStreak(trips, now, zone) }
    val sinceDays = remember(trips, now) { StatsCalculator.daysSinceFirst(trips, now, zone) }

    val describeTrip: (com.lasse.speedometer.data.db.TripEntity) -> String = { trip ->
        trip.title?.takeIf { it.isNotBlank() } ?: formatTripDate(trip.startedAt)
    }
    val records: List<TripRecord> = remember(trips, units) {
        StatsCalculator.records(
            trips = trips,
            formatDistance = { Formatters.distance(it, units) },
            formatDuration = { Formatters.durationLong(it) },
            formatSpeed = { Formatters.speed(it, units) },
            formatElevation = { Formatters.elevation(it, units) },
            describe = describeTrip,
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = stringResource(R.string.stats))

        SegmentedTabs(
            options = StatsPeriod.entries.map { stringResource(it.labelRes) },
            selectedIndex = StatsPeriod.entries.indexOf(period),
            onSelect = { period = StatsPeriod.entries[it] },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        if (trips.isEmpty()) {
            CenteredEmptyState(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.no_stats_title),
                body = stringResource(R.string.no_stats_body),
                actionLabel = stringResource(R.string.empty_start_recording),
                onAction = onStartRecording,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.Screen,
                end = Dimens.Screen,
                bottom = Dimens.BottomGap,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.Item),
        ) {
            if (settings.weeklyGoalM > 0) {
                item("goal") {
                    GoalCard(
                        goalM = settings.weeklyGoalM,
                        doneM = weekTotals.distanceM,
                        dayOfWeek = dayOfWeek,
                        settings = settings,
                    )
                }
            }

            item("totals") {
                SummaryCard(
                    totals = totals,
                    previous = previous,
                    settings = settings,
                    period = period,
                )
            }

            item("trend") {
                SectionCard(title = stringResource(R.string.stats_trend)) {
                    BarChart(
                        bars = trend.map { BarSample(it.label, it.distanceM, it.current) },
                        formatValue = { Formatters.distance(it, units) },
                        summaryLabel = stringResource(R.string.stats_total),
                    )
                }
            }

            if (breakdown.size > 1) {
                item("activities") {
                    SectionCard(title = stringResource(R.string.stats_by_activity)) {
                        breakdown.forEachIndexed { index, entry ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            ActivityRow(entry = entry, settings = settings)
                        }
                    }
                }
            }

            if (records.isNotEmpty()) {
                item("records") {
                    SectionCard(
                        title = stringResource(R.string.stats_records),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        records.forEach { record ->
                            RecordRow(record = record, onClick = { onOpenTrip(record.tripId) })
                        }
                    }
                }
            }

            item("lifetime") {
                SectionCard(title = stringResource(R.string.stats_lifetime)) {
                    Text(
                        text = Formatters.distance(lifetime.distanceM, units),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.trips_count,
                            lifetime.trips,
                            lifetime.trips,
                        ) + "  ·  " + Formatters.durationLong(lifetime.durationMs) +
                            "  ·  ↑ " + Formatters.elevation(lifetime.ascentM, units),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
                    ) {
                        StatTile(
                            label = stringResource(R.string.stats_streak),
                            value = pluralStringResource(R.plurals.days_count, streak, streak),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatTile(
                            label = stringResource(R.string.stats_active_days),
                            value = lifetime.activeDays.toString(),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        StatTile(
                            label = stringResource(R.string.stats_recording_for),
                            value = pluralStringResource(
                                R.plurals.days_count,
                                sinceDays.toInt(),
                                sinceDays.toInt(),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totals: Totals,
    previous: Totals,
    settings: AppSettings,
    period: StatsPeriod,
) {
    val units = settings.units
    SectionCard(title = stringResource(period.labelRes)) {
        if (totals.isEmpty) {
            Text(
                text = stringResource(R.string.stats_period_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Text(
            text = Formatters.distance(totals.distanceM, units),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = pluralStringResource(R.plurals.trips_count, totals.trips, totals.trips) +
                "  ·  " + pluralStringResource(
                    R.plurals.active_days_count,
                    totals.activeDays,
                    totals.activeDays,
                ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Against the same window a period ago, which is the only comparison
        // that means anything: 40 km is a lot or a little depending on what
        // last week looked like.
        if (!previous.isEmpty) {
            Spacer(Modifier.height(6.dp))
            val change = (totals.distanceM - previous.distanceM) / previous.distanceM
            val percent = (change * 100).roundToInt()
            Text(
                text = stringResource(
                    if (percent >= 0) R.string.stats_up_on_previous else R.string.stats_down_on_previous,
                    kotlin.math.abs(percent),
                    stringResource(period.previousLabelRes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (percent >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        val tiles = listOf(
            stringResource(R.string.stat_time) to Formatters.durationLong(totals.durationMs),
            stringResource(R.string.moving_time) to Formatters.durationLong(totals.movingTimeMs),
            stringResource(R.string.stat_avg) to Formatters.speed(totals.avgSpeedMps, units),
            stringResource(R.string.stat_max) to Formatters.speed(totals.maxSpeedMps, units),
            stringResource(R.string.ascent) to Formatters.elevation(totals.ascentM, units),
            stringResource(R.string.descent) to Formatters.elevation(totals.descentM, units),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Tile)) {
            tiles.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
                ) {
                    row.forEach { (label, value) ->
                        StatTile(
                            label = label,
                            value = value,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityBreakdown, settings: AppSettings) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = entry.activity.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(entry.activity.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            Text(
                text = Formatters.distance(entry.totals.distanceM, settings.units),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(entry.distanceShare.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = pluralStringResource(
                R.plurals.trips_count,
                entry.totals.trips,
                entry.totals.trips,
            ) + "  ·  " + Formatters.durationLong(entry.totals.durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordRow(record: TripRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(record.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = record.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = record.value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The week's goal as a closing ring.
 *
 * Also says whether the week is on track, which is the number that actually
 * changes what someone does today: 40 of 100 km means nothing on its own, and
 * a great deal on a Tuesday.
 */
@Composable
private fun GoalCard(goalM: Double, doneM: Double, dayOfWeek: Int, settings: AppSettings) {
    val units = settings.units
    val fraction = (doneM / goalM).toFloat()
    val expected = goalM * dayOfWeek / 7.0
    val behind = expected - doneM

    SectionCard(title = stringResource(R.string.goal_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = fraction,
                label = "${(fraction * 100).roundToInt()}%",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.goal_progress,
                        Formatters.distance(doneM, units),
                        Formatters.distance(goalM, units),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (doneM >= goalM) {
                        stringResource(
                            R.string.goal_reached,
                            Formatters.distance(doneM - goalM, units),
                        )
                    } else {
                        stringResource(
                            R.string.goal_remaining,
                            Formatters.distance(goalM - doneM, units),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (behind <= 0) {
                        stringResource(R.string.goal_pace_ahead)
                    } else {
                        stringResource(
                            R.string.goal_pace_behind,
                            Formatters.distance(behind, units),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (behind <= 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
