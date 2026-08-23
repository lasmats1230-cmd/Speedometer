package com.lasse.speedometer.util

import com.lasse.speedometer.data.prefs.UnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * All UI-facing number formatting. Values are kept in SI internally
 * (metres, metres per second) and converted only at the edge.
 */
object Formatters {

    private const val MPS_TO_KMH = 3.6
    private const val MPS_TO_MPH = 2.2369362920544
    private const val M_TO_MI = 0.000621371192237334
    private const val M_TO_FT = 3.280839895

    fun speedIn(mps: Double, units: UnitSystem): Double = when (units) {
        UnitSystem.METRIC -> mps * MPS_TO_KMH
        UnitSystem.IMPERIAL -> mps * MPS_TO_MPH
    }

    fun distanceIn(metres: Double, units: UnitSystem): Double = when (units) {
        UnitSystem.METRIC -> metres / 1000.0
        UnitSystem.IMPERIAL -> metres * M_TO_MI
    }

    fun elevationIn(metres: Double, units: UnitSystem): Double = when (units) {
        UnitSystem.METRIC -> metres
        UnitSystem.IMPERIAL -> metres * M_TO_FT
    }

    fun speedUnit(units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "km/h"
        UnitSystem.IMPERIAL -> "mph"
    }

    fun distanceUnit(units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "km"
        UnitSystem.IMPERIAL -> "mi"
    }

    fun elevationUnit(units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "m"
        UnitSystem.IMPERIAL -> "ft"
    }

    /** The big readout: a whole number, no unit. */
    fun bigSpeed(mps: Double, units: UnitSystem): String =
        speedIn(mps, units).coerceAtLeast(0.0).roundToInt().toString()

    /** "3,4 km/h" — one decimal, locale-aware separator. */
    fun speed(mps: Double, units: UnitSystem): String =
        String.format(Locale.getDefault(), "%.1f %s", speedIn(mps, units), speedUnit(units))

    /** "14,16 km" — two decimals so short trips still show movement. */
    fun distance(metres: Double, units: UnitSystem): String =
        String.format(Locale.getDefault(), "%.2f %s", distanceIn(metres, units), distanceUnit(units))

    /** "5 km" — a whole number of units, for the spoken-update intervals. */
    fun distanceUnitCount(count: Int, units: UnitSystem): String =
        "$count ${distanceUnit(units)}"

    /** "14,16" — the bare number, for tables that carry the unit in a header. */
    fun distanceValue(metres: Double, units: UnitSystem): String =
        String.format(Locale.getDefault(), "%.2f", distanceIn(metres, units))

    /** "586 m" */
    fun elevation(metres: Double, units: UnitSystem): String =
        String.format(
            Locale.getDefault(),
            "%d %s",
            elevationIn(metres, units).roundToLong(),
            elevationUnit(units),
        )

    /**
     * "4:12 /km" — time to cover one kilometre or mile at the current speed.
     * Standing still has no meaningful pace, so it reads as a dash.
     */
    fun pace(mps: Double, units: UnitSystem): String {
        if (mps < 0.28) return "—"
        val perUnit = when (units) {
            UnitSystem.METRIC -> 1000.0 / mps
            UnitSystem.IMPERIAL -> 1609.344 / mps
        }
        if (perUnit > 3600) return "—"
        // Round the total, not each part: rounding seconds on their own turns
        // 359.99 s into "5:60" instead of "6:00".
        val totalSeconds = perUnit.roundToInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val suffix = if (units == UnitSystem.METRIC) "/km" else "/mi"
        return String.format(Locale.getDefault(), "%d:%02d %s", minutes, seconds, suffix)
    }

    /** "±69 m" — accuracy is always metric-ish; feet for imperial. */
    fun accuracy(metres: Float, units: UnitSystem): String =
        "±" + elevation(metres.toDouble(), units)

    /**
     * "00:03" below an hour, "01:31:29" above it — matching how a stopwatch
     * grows rather than padding every trip out to two colons.
     */
    fun duration(millis: Long): String {
        val totalSeconds = abs(millis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /** Always hh:mm:ss, for the history rows where trips sit side by side. */
    fun durationLong(millis: Long): String {
        val totalSeconds = abs(millis) / 1000
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60,
        )
    }
}
