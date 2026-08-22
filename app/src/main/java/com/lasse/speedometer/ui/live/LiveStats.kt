package com.lasse.speedometer.ui.live

import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.StatType
import com.lasse.speedometer.tracking.TrackingState
import com.lasse.speedometer.util.Formatters
import com.lasse.speedometer.util.LocaleFormats
import kotlin.math.roundToInt

/** Renders a [StatType] from the live tracking state. */
object LiveStats {

    private val CARDINALS =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun value(stat: StatType, state: TrackingState, settings: AppSettings, now: Long): String {
        val units = settings.units
        return when (stat) {
            StatType.MAX_SPEED -> Formatters.speed(state.maxSpeedMps, units)
            StatType.AVG_SPEED -> Formatters.speed(state.avgSpeedMps, units)
            StatType.DISTANCE -> Formatters.distance(state.distanceM, units)
            StatType.DURATION -> Formatters.duration(state.elapsedMs)
            StatType.MOVING_TIME -> Formatters.duration(state.movingTimeMs)
            StatType.PACE -> Formatters.pace(state.speedMps, units)
            StatType.ALTITUDE -> state.altitudeM
                ?.let { Formatters.elevation(it, units) }
                ?: PLACEHOLDER

            StatType.ASCENT -> Formatters.elevation(state.ascentM, units)
            StatType.DESCENT -> Formatters.elevation(state.descentM, units)
            StatType.ACCURACY -> state.accuracyM
                ?.let { Formatters.accuracy(it, units) }
                ?: PLACEHOLDER

            StatType.HEADING -> state.bearingDeg?.let { heading(it) } ?: PLACEHOLDER
            StatType.CLOCK -> LocaleFormats.format("HH:mm", now)
        }
    }

    private fun heading(degrees: Float): String {
        val normalised = ((degrees % 360f) + 360f) % 360f
        val index = ((normalised + 22.5f) / 45f).toInt() % CARDINALS.size
        return "${normalised.roundToInt()}° ${CARDINALS[index]}"
    }

    private const val PLACEHOLDER = "—"
}
