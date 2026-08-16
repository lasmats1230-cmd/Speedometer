package com.lasse.speedometer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.ui.theme.TrackColors
import kotlin.math.max

/**
 * A filled line chart for the elevation and speed profiles.
 *
 * Values are sampled down to at most one point per pixel column, so a
 * two-hour ride draws as fast as a two-minute one.
 */
@Composable
fun ProfileChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = TrackColors.Track,
    startLabel: String? = null,
    endLabel: String? = null,
    /** Forces the baseline to zero — right for speed, wrong for elevation. */
    zeroBased: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            if (values.size < 2 || size.width <= 0f) return@Canvas

            val columns = size.width.toInt().coerceAtLeast(2)
            val sampled = if (values.size <= columns) {
                values
            } else {
                val step = values.size.toFloat() / columns
                List(columns) { index -> values[(index * step).toInt().coerceIn(values.indices)] }
            }

            val minValue = if (zeroBased) 0f else sampled.min()
            val maxValue = sampled.max()
            // A flat profile would otherwise divide by zero and vanish.
            val span = max(maxValue - minValue, 0.001f)

            val stepX = size.width / (sampled.size - 1).toFloat()
            fun yFor(value: Float) =
                size.height - ((value - minValue) / span) * (size.height * 0.92f) - size.height * 0.04f

            val line = Path()
            sampled.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }

            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.04f)),
                ),
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        if (startLabel != null || endLabel != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = startLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = endLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
