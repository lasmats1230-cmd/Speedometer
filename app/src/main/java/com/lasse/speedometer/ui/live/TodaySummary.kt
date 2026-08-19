package com.lasse.speedometer.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.repo.DaySummary
import com.lasse.speedometer.util.Formatters

/**
 * What today already amounts to, above the start button.
 *
 * The live view is the screen the app opens on, and before a recording starts
 * it is otherwise a zero: no distance, no time, nothing to say whether you
 * have been out. This answers that without a trip to the statistics tab, and
 * disappears entirely once there is nothing to report — an empty card saying
 * "0.0 km" would be worse than no card at all.
 */
@Composable
fun TodaySummary(
    summary: DaySummary,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    if (summary.isEmpty) return

    val units = settings.units
    val totals = summary.totals

    val headline = if (totals.isEmpty) {
        stringResource(R.string.today_nothing)
    } else {
        listOf(
            Formatters.distance(totals.distanceM, units),
            Formatters.duration(totals.movingTimeMs),
        ).joinToString(SEPARATOR)
    }

    val goalM = settings.weeklyGoalM
    val week = when {
        goalM <= 0 -> stringResource(
            R.string.today_week,
            Formatters.distance(summary.weekDistanceM, units),
        )

        summary.weekDistanceM >= goalM -> stringResource(R.string.today_goal_done)
        else -> stringResource(
            R.string.today_goal_left,
            Formatters.distance(goalM - summary.weekDistanceM, units),
        )
    }
    // One day is not a streak, it is today.
    val streak = summary.streakDays
        .takeIf { it > 1 }
        ?.let { pluralStringResource(R.plurals.today_streak, it, it) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.today_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // At the largest font scale the label is what gives way:
                    // the figures are the point of the card.
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = listOfNotNull(streak, week).joinToString(SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Middle dot with spaces, the separator used between figures throughout. */
private const val SEPARATOR = " · "
