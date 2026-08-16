package com.lasse.speedometer.ui.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lasse.speedometer.R
import com.lasse.speedometer.ui.components.EmptyState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Magnetometer compass.
 *
 * The heading is low-pass filtered across the shortest angular path, so it
 * doesn't spin the long way round as the reading crosses north.
 */
@Composable
fun CompassPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    var heading by remember { mutableFloatStateOf(0f) }
    var accuracyLow by remember { mutableStateOf(false) }

    DisposableEffect(rotationSensor) {
        if (rotationSensor == null) return@DisposableEffect onDispose { }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val degrees = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

                val previous = smoothed
                smoothed = if (previous == null) {
                    degrees
                } else {
                    var delta = degrees - previous
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    (previous + delta * SMOOTHING + 360f) % 360f
                }
                heading = smoothed ?: degrees
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                accuracyLow = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    if (rotationSensor == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.ExploreOff,
                title = stringResource(R.string.no_compass),
                body = stringResource(R.string.calibrate_compass),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompassDial(
            heading = heading,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f),
        )

        Text(
            text = "${heading.roundToInt()}°  ${cardinal(heading)}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 28.dp),
        )

        if (accuracyLow) {
            Text(
                text = stringResource(R.string.calibrate_compass),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun CompassDial(heading: Float, modifier: Modifier = Modifier) {
    val dialColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val northColor = MaterialTheme.colorScheme.error
    val needleColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(color = dialColor, radius = radius, center = centre)
        drawCircle(
            color = tickColor.copy(alpha = 0.25f),
            radius = radius * 0.98f,
            center = centre,
            style = Stroke(width = 2f),
        )

        // The dial rotates opposite the phone so north stays north.
        rotate(degrees = -heading, pivot = centre) {
            for (degrees in 0 until 360 step 15) {
                val major = degrees % 45 == 0
                val angle = Math.toRadians(degrees.toDouble() - 90.0)
                val outer = radius * 0.94f
                val inner = radius * if (major) 0.82f else 0.88f
                drawLine(
                    color = if (degrees == 0) northColor else tickColor,
                    start = centre + Offset(
                        (cos(angle) * inner).toFloat(),
                        (sin(angle) * inner).toFloat(),
                    ),
                    end = centre + Offset(
                        (cos(angle) * outer).toFloat(),
                        (sin(angle) * outer).toFloat(),
                    ),
                    strokeWidth = if (major) 4f else 2f,
                )
            }

            listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, degrees) ->
                val angle = Math.toRadians(degrees.toDouble() - 90.0)
                val distance = radius * 0.66f
                drawCompassLabel(
                    textMeasurer = textMeasurer,
                    text = label,
                    centre = centre + Offset(
                        (cos(angle) * distance).toFloat(),
                        (sin(angle) * distance).toFloat(),
                    ),
                    color = if (degrees == 0) northColor else labelColor,
                )
            }
        }

        // A fixed needle at the top marks the direction the phone points.
        val needle = Path().apply {
            moveTo(centre.x, centre.y - radius * 0.5f)
            lineTo(centre.x - radius * 0.075f, centre.y + radius * 0.08f)
            lineTo(centre.x + radius * 0.075f, centre.y + radius * 0.08f)
            close()
        }
        drawPath(needle, needleColor)
        drawCircle(color = needleColor, radius = radius * 0.045f, center = centre)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompassLabel(
    textMeasurer: TextMeasurer,
    text: String,
    centre: Offset,
    color: Color,
) {
    val layout = textMeasurer.measure(
        text = text,
        style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.Medium),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            centre.x - layout.size.width / 2f,
            centre.y - layout.size.height / 2f,
        ),
    )
}

private const val SMOOTHING = 0.15f

private val CARDINALS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun cardinal(heading: Float): String {
    val index = ((heading + 22.5f) / 45f).toInt() % CARDINALS.size
    return CARDINALS[abs(index)]
}
