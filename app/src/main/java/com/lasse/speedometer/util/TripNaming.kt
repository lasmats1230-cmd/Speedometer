package com.lasse.speedometer.util

import android.content.Context
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.ActivityType
import java.util.Calendar

/**
 * Names a trip the way you would remember it.
 *
 * "Morning ride" beats "14 Apr 2026, 07:12" in a list you are scanning for
 * the one you mean, and it costs the user nothing to type. The date is still
 * on the card, and any generated name can be replaced by renaming the trip.
 */
object TripNaming {

    fun titleFor(context: Context, startedAt: Long, activity: ActivityType): String {
        val hour = Calendar.getInstance().apply { timeInMillis = startedAt }
            .get(Calendar.HOUR_OF_DAY)
        val template = when (hour) {
            in 5..10 -> R.string.trip_title_morning
            in 11..13 -> R.string.trip_title_midday
            in 14..17 -> R.string.trip_title_afternoon
            in 18..21 -> R.string.trip_title_evening
            else -> R.string.trip_title_night
        }
        return context.getString(template, context.getString(activity.labelRes))
    }
}
