package com.lasse.speedometer.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Unrestricted background usage — Android's battery-optimisation exemption.
 *
 * A ride recorded with the screen off is precisely the situation Doze exists
 * to interrupt: without the exemption the system is free to defer this app's
 * work, and the track comes back with gaps in it. The foreground service
 * survives, but the location updates behind it thin out on several vendors'
 * builds.
 *
 * Android grants the exemption through a dialog the system draws over the app,
 * so asking costs the user one tap and never sends them off into the device
 * settings to find a switch.
 */
object BackgroundUsage {

    /** Whether the exemption is already held. */
    fun isUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The intent behind the in-place dialog.
     *
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is the one that asks on
     * the spot; its near-namesake
     * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` opens the system's list of
     * every installed app instead, which is the hunt this is meant to avoid.
     * The package uri is what makes the difference — without it the same
     * action falls back to that list.
     */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri(),
    )
}
