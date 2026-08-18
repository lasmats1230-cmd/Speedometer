package com.lasse.speedometer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One column of a [BarChart]. */
data class BarSample(
    val label: String,
    val value: Double,
    /** Marks the bar the present falls into — this week, this month, today. */
    val current: Boolean = false,
)

/**
 * A column chart for totals over time.
 *
 * Tapping a column names its value, which is what the chart is usually asked:
 * not "which was biggest" — the tallest bar already says that — but "how far
 * was Wednesday". With nothing selected the readout falls back to the total,
 * so the space is never empty.
 */
@Composable
fun BarChart(
    bars: List<BarSample>,
    formatValue: (Double) -> String,
    summaryLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val max = remember(bars) { bars.maxOf { it.value } }
    val total = remember(bars) { bars.sumOf { it.value } }

    Column(modifier.fillMaxWidth()) {
        val chosen = selected?.let(bars::getOrNull)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = chosen?.label ?: summaryLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatValue(chosen?.value ?: total),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, bar ->
                val isSelected = index == selected
                val fraction = if (max > 0) (bar.value / max).toFloat() else 0f
                val animated by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(320),
                    label = "bar-height",
                )
                val interaction = remember { MutableInteractionSource() }

                val spoken = "${'$'}{bar.label}: ${'$'}{formatValue(bar.value)}"
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { selected = if (isSelected) null else index }
                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // An empty day still draws a stub, so the axis reads as
                        // a row of days rather than a gap in the chart.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .barHeight(animated)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        bar.current -> MaterialTheme.colorScheme.primary
                                        bar.value > 0 ->
                                            MaterialTheme.colorScheme.primaryContainer

                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected || bar.current) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Scales a bar to a fraction of the space it is given, with a visible floor so
 * a very short ride is still a mark on the chart rather than nothing at all.
 */
private fun Modifier.barHeight(fraction: Float): Modifier = layout { measurable, constraints ->
    val available = constraints.maxHeight
    val target = if (fraction <= 0f) {
        MIN_BAR_PX
    } else {
        (available * fraction).toInt().coerceAtLeast(MIN_VALUE_BAR_PX)
    }
    val placeable = measurable.measure(
        constraints.copy(minHeight = target, maxHeight = target)
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private const val MIN_BAR_PX = 6
private const val MIN_VALUE_BAR_PX = 12
