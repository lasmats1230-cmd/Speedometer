package com.lasse.speedometer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.MinimapSize
import com.lasse.speedometer.data.prefs.StatType
import com.lasse.speedometer.ui.theme.TrackColors
import com.lasse.speedometer.util.Formatters

/**
 * A scaled-down mock of the live view, so the effect of a setting is visible
 * without leaving the page.
 *
 * Deliberately a separate composable from the real screen rather than a shrunk
 * copy of it: the live view needs tracking state, permissions and a map
 * surface, none of which belong on a settings page. It mirrors the structure
 * and proportions, filled with representative numbers.
 */
@Composable
fun LayoutPreview(settings: AppSettings, modifier: Modifier = Modifier) {
    val layout = settings.layout

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PHONE_ASPECT),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.background,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (layout.showStatusChip) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(TrackColors.Recording)
                        )
                        PreviewText(SAMPLE_STATUS, 7.sp)
                    }
                }
                Spacer(Modifier.height(5.dp))
            }

            PreviewText(
                text = Formatters.bigSpeed(SAMPLE_SPEED_MPS, settings.units),
                size = 40.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PreviewText(Formatters.speedUnit(settings.units), 8.sp)

            if (layout.stats.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                PreviewStatGrid(settings)
            }

            if (layout.showTimer) {
                Spacer(Modifier.height(5.dp))
                PreviewText(
                    text = Formatters.duration(SAMPLE_ELAPSED_MS),
                    size = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            }

            if (layout.minimapSize != MinimapSize.HIDDEN) {
                Spacer(Modifier.height(6.dp))
                // The real view gives the map a fixed height or the remaining
                // space; here the same three sizes become a share of what's left.
                val mapModifier = when (layout.minimapSize) {
                    MinimapSize.SMALL -> Modifier.height(26.dp)
                    MinimapSize.MEDIUM -> Modifier.height(46.dp)
                    else -> Modifier.weight(1f)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(mapModifier),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PreviewText(SAMPLE_MAP, 7.sp)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewStatGrid(settings: AppSettings) {
    val layout = settings.layout
    val columns = layout.statColumns.coerceIn(1, 4)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        layout.stats.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { stat ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PreviewText(
                                text = stringResource(stat.labelRes).uppercase(),
                                size = 5.5.sp,
                            )
                            PreviewText(
                                text = sampleValue(stat, settings),
                                size = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PreviewText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        fontSize = size,
        lineHeight = size * 1.25f,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Plausible mid-ride numbers, so the preview reads like a real screen. */
private fun sampleValue(stat: StatType, settings: AppSettings): String {
    val units = settings.units
    return when (stat) {
        StatType.MAX_SPEED -> Formatters.speed(11.4, units)
        StatType.AVG_SPEED -> Formatters.speed(6.2, units)
        StatType.DISTANCE -> Formatters.distance(14_160.0, units)
        StatType.DURATION -> Formatters.duration(SAMPLE_ELAPSED_MS)
        StatType.MOVING_TIME -> Formatters.duration(SAMPLE_ELAPSED_MS - 240_000)
        StatType.PACE -> Formatters.pace(SAMPLE_SPEED_MPS, units)
        StatType.ALTITUDE -> Formatters.elevation(586.0, units)
        StatType.ASCENT -> Formatters.elevation(56.0, units)
        StatType.DESCENT -> Formatters.elevation(61.0, units)
        StatType.ACCURACY -> Formatters.accuracy(6f, units)
        StatType.HEADING -> "142° SE"
        StatType.CLOCK -> "21:19"
    }
}

private const val PHONE_ASPECT = 0.62f
private const val SAMPLE_SPEED_MPS = 6.9
private const val SAMPLE_ELAPSED_MS = 91L * 60 * 1000 + 29_000
private const val SAMPLE_STATUS = "GPS"
private const val SAMPLE_MAP = "Map"
