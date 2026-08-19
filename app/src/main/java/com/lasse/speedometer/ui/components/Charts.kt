package com.lasse.speedometer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lasse.speedometer.R
import com.lasse.speedometer.ui.theme.TrackColors
import kotlin.math.max

/** One reading, positioned along the chart's own horizontal axis. */
data class ChartSample(val x: Float, val y: Float)

/**
 * A line chart with labelled axes that can be scrubbed with a finger.
 *
 * Both axes are labelled in the caller's own units — the elevation profile
 * runs against distance, the speed profile against time — because a curve
 * without a scale only says "it went up a bit somewhere".
 *
 * Touching the plot picks the nearest reading and reports both of its values,
 * which is the question these charts actually get asked: not "what was the
 * highest point" but "how steep was it *there*".
 */
@Composable
fun ProfileChart(
    samples: List<ChartSample>,
    formatX: (Float) -> String,
    formatY: (Float) -> String,
    modifier: Modifier = Modifier,
    lineColor: Color = TrackColors.Track,
    /** Speed belongs on a zero baseline; elevation does not. */
    zeroBased: Boolean = false,
    /**
     * What to show either side of the chart when nothing is being scrubbed.
     * Defaults to the bare extremes; pass wording where a label reads better.
     */
    describeLow: ((Float) -> String)? = null,
    describeHigh: ((Float) -> String)? = null,
    height: Dp = 160.dp,
) {
    if (samples.size < 2) return

    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // One reading per pixel column is all the chart can show; a two-hour ride
    // then draws as fast as a two-minute one, and scrubbing stays smooth.
    val plotted = remember(samples) { samples.decimate(MAX_SAMPLES) }

    val minY = remember(plotted, zeroBased) {
        if (zeroBased) 0f else plotted.minOf { it.y }
    }
    val maxY = remember(plotted) { plotted.maxOf { it.y } }
    val minX = remember(plotted) { plotted.first().x }
    val maxX = remember(plotted) { plotted.last().x }

    var selected by remember(plotted) { mutableStateOf<Int?>(null) }

    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    Column(modifier) {
        ScrubReadout(
            sample = selected?.let(plotted::getOrNull),
            formatX = formatX,
            formatY = formatY,
            fallbackLow = describeLow?.invoke(minY) ?: formatY(minY),
            fallbackHigh = describeHigh?.invoke(maxY) ?: formatY(maxY),
        )

        // Scrubbing is a finger on a canvas, which is nothing at all to a
        // screen reader. The shape of the curve will not survive being read
        // out, but its range and length will, and that is most of the answer.
        val spoken = stringResource(
            R.string.chart_spoken,
            formatY(minY),
            formatY(maxY),
            formatX(maxX),
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = spoken }
                .pointerInput(plotted) {
                    // Press and drag both scrub; releasing clears the marker.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val plotLeft = Y_AXIS_GUTTER.toPx()
                        val plotWidth = size.width - plotLeft - RIGHT_PADDING.toPx()

                        fun indexFor(x: Float): Int {
                            if (plotWidth <= 0f) return 0
                            val fraction = ((x - plotLeft) / plotWidth).coerceIn(0f, 1f)
                            return (fraction * (plotted.size - 1)).toInt()
                                .coerceIn(plotted.indices)
                        }

                        selected = indexFor(down.position.x)
                        down.consume()

                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            selected = indexFor(change.position.x)
                            change.consume()
                            pressed = change.pressed
                        }
                        selected = null
                    }
                }
        ) {
            val plotLeft = Y_AXIS_GUTTER.toPx()
            val plotBottom = size.height - X_AXIS_GUTTER.toPx()
            val plotWidth = size.width - plotLeft - RIGHT_PADDING.toPx()
            val plotHeight = plotBottom - TOP_PADDING.toPx()
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            // A flat profile would otherwise divide by zero and collapse.
            val spanY = max(maxY - minY, 0.001f)
            val spanX = max(maxX - minX, 0.001f)

            fun xFor(value: Float) = plotLeft + ((value - minX) / spanX) * plotWidth
            fun yFor(value: Float) = plotBottom - ((value - minY) / spanY) * plotHeight

            // Horizontal gridlines, labelled on the left.
            listOf(1f, 0.5f, 0f).forEach { fraction ->
                val value = minY + spanY * fraction
                val y = yFor(value)
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotLeft + plotWidth, y),
                    strokeWidth = 1f,
                )
                val layout = textMeasurer.measure(formatY(value), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = plotLeft - layout.size.width - 6.dp.toPx(),
                        y = (y - layout.size.height / 2f).coerceIn(
                            0f,
                            size.height - layout.size.height,
                        ),
                    ),
                )
            }

            // Ticks along the bottom: start, middle, end.
            listOf(0f, 0.5f, 1f).forEachIndexed { index, fraction ->
                val value = minX + spanX * fraction
                val x = xFor(value)
                val layout = textMeasurer.measure(formatX(value), labelStyle)
                val left = when (index) {
                    0 -> x
                    1 -> x - layout.size.width / 2f
                    else -> x - layout.size.width
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = left.coerceIn(0f, size.width - layout.size.width),
                        y = plotBottom + 4.dp.toPx(),
                    ),
                )
            }

            val line = Path()
            plotted.forEachIndexed { index, sample ->
                val x = xFor(sample.x)
                val y = yFor(sample.y)
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }

            val fill = Path().apply {
                addPath(line)
                lineTo(xFor(maxX), plotBottom)
                lineTo(xFor(minX), plotBottom)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.03f)),
                    startY = TOP_PADDING.toPx(),
                    endY = plotBottom,
                ),
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            selected?.let { index ->
                val sample = plotted.getOrNull(index) ?: return@let
                val x = xFor(sample.x)
                val y = yFor(sample.y)
                drawLine(
                    color = highlightColor.copy(alpha = 0.5f),
                    start = Offset(x, TOP_PADDING.toPx()),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

/**
 * Shows the reading under the finger while scrubbing, and the range the chart
 * covers the rest of the time.
 */
@Composable
private fun ScrubReadout(
    sample: ChartSample?,
    formatX: (Float) -> String,
    formatY: (Float) -> String,
    fallbackLow: String,
    fallbackHigh: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (sample != null) {
            Text(
                text = formatX(sample.x),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatY(sample.y),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = fallbackLow,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = fallbackHigh,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Thins the series to at most [limit] readings, keeping the first and last so
 * the axes still span the whole trip.
 */
private fun List<ChartSample>.decimate(limit: Int): List<ChartSample> {
    if (size <= limit) return this
    val step = (size - 1).toFloat() / (limit - 1)
    return List(limit) { index ->
        this[(index * step).toInt().coerceIn(indices)]
    }
}

private val Y_AXIS_GUTTER = 52.dp
private val X_AXIS_GUTTER = 18.dp
private val TOP_PADDING = 6.dp
private val RIGHT_PADDING = 8.dp
private const val MAX_SAMPLES = 500
