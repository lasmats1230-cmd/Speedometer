package com.lasse.speedometer.data.db

import androidx.annotation.StringRes
import com.lasse.speedometer.R

/**
 * What a trip actually was.
 *
 * The one thing a GPS trace cannot tell you: 12 km/h is a brisk cycle, a hard
 * run or a crawling car, and the same numbers mean different things in each
 * case. Stored on the trip so history, statistics and Health Connect can all
 * read it back.
 */
enum class ActivityType(@param:StringRes val labelRes: Int) {
    RIDE(R.string.activity_ride),
    RUN(R.string.activity_run),
    WALK(R.string.activity_walk),
    HIKE(R.string.activity_hike),
    DRIVE(R.string.activity_drive),
    OTHER(R.string.activity_other),
    ;

    /**
     * Whether minutes per kilometre reads better than kilometres per hour.
     * Runners and walkers think in pace; nobody paces a car.
     */
    val prefersPace: Boolean get() = this == RUN || this == WALK || this == HIKE

    companion object {
        /** Reads a stored name, falling back rather than crashing on junk. */
        fun fromName(name: String?): ActivityType =
            entries.firstOrNull { it.name == name } ?: RIDE
    }
}
