package com.lasse.speedometer.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.util.Formatters

/**
 * A trip in a few lines of text, for sharing into a chat rather than handing
 * over a GPX file nobody on the other end can open.
 *
 * Assembled from the labels the rest of the app already uses, so it comes out
 * in the app's language without a second set of translations to keep in step.
 */
@Composable
fun tripSummaryText(trip: TripEntity, settings: AppSettings): String {
    val units = settings.units
    val activity = stringResource(trip.activityType.labelRes)
    val avgLabel = stringResource(R.string.stat_avg)
    val maxLabel = stringResource(R.string.stat_max)
    val footer = stringResource(R.string.share_footer)
    val title = trip.title?.takeIf { it.isNotBlank() }

    return buildString {
        append(activity)
        append("  ·  ")
        append(formatTripDate(trip.startedAt))
        if (title != null) {
            append('\n')
            append(title)
        }
        append('\n')
        append(Formatters.distance(trip.distanceM, units))
        append("  ·  ")
        append(Formatters.durationLong(trip.durationMs))
        append('\n')
        append(avgLabel)
        append(' ')
        append(
            if (trip.activityType.prefersPace) {
                Formatters.pace(trip.avgSpeedMps, units)
            } else {
                Formatters.speed(trip.avgSpeedMps, units)
            }
        )
        append("  ·  ")
        append(maxLabel)
        append(' ')
        append(Formatters.speed(trip.maxSpeedMps, units))
        if (trip.ascentM >= 1) {
            append('\n')
            append("↑ ")
            append(Formatters.elevation(trip.ascentM, units))
            append("   ↓ ")
            append(Formatters.elevation(trip.descentM, units))
        }
        if (!trip.note.isNullOrBlank()) {
            append('\n')
            append(trip.note)
        }
        append("\n\n")
        append(footer)
    }
}
