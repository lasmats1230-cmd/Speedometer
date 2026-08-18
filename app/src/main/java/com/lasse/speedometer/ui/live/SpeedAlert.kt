package com.lasse.speedometer.ui.live

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import com.lasse.speedometer.data.prefs.AppSettings

/**
 * Whether the current speed is over the user's limit, buzzing once as it goes
 * over rather than for as long as it stays there.
 *
 * The alert only clears once the speed has dropped a little way back below the
 * limit: sitting exactly on it would otherwise flash the readout and rattle the
 * phone at every GPS wobble.
 */
@Composable
fun rememberSpeedAlert(speedMps: Double, settings: AppSettings): Boolean {
    val limit = settings.speedAlertMps
    if (limit <= 0f) return false

    val context = LocalContext.current
    var over by remember { mutableStateOf(false) }

    LaunchedEffect(speedMps, limit) {
        val wasOver = over
        over = when {
            speedMps >= limit -> true
            speedMps < limit - HYSTERESIS_MPS -> false
            else -> wasOver
        }
        if (over && !wasOver && settings.speedAlertVibrate) context.buzz()
    }

    return over
}

private fun Context.buzz() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService<Vibrator>()
    } ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

/** Roughly 2 km/h — below the noise the speed reading carries anyway. */
private const val HYSTERESIS_MPS = 0.6f
private const val BUZZ_MS = 250L
