package com.lasse.speedometer.ui.live

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.tracking.TrackingState
import com.lasse.speedometer.util.Formatters

/**
 * The pared-back screen the battery savers switch to.
 *
 * Everything expensive is gone: no map surface, no tiles being fetched or
 * rendered, no chrome. On an OLED panel the black background costs almost
 * nothing to light, which is the point.
 */
@Composable
fun DimDisplay(
    state: TrackingState,
    settings: AppSettings,
    /** Extreme mode drops the backlight as far as the system allows. */
    lowBrightness: Boolean,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lowBrightness) DimScreenBrightness()

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onWake,
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = Formatters.bigSpeed(state.speedMps, settings.units),
            fontSize = 150.sp,
            lineHeight = 152.sp,
            letterSpacing = (-6).sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = Formatters.speedUnit(settings.units),
            fontSize = 26.sp,
            color = Color(0xFF9AA0A6),
            textAlign = TextAlign.Center,
        )
        Text(
            text = Formatters.distance(state.distanceM, settings.units),
            fontSize = 44.sp,
            lineHeight = 52.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 36.dp),
        )
        Text(
            text = Formatters.duration(state.elapsedMs),
            fontSize = 22.sp,
            color = Color(0xFF9AA0A6),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.tap_to_wake),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5F6368),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}

/** Holds the backlight low for as long as this composable is on screen. */
@Composable
private fun DimScreenBrightness() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply {
            screenBrightness = LOW_BRIGHTNESS
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

private const val LOW_BRIGHTNESS = 0.02f
