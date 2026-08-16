package com.lasse.speedometer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.ui.theme.TrackColors
import kotlin.math.cos
import kotlin.math.max

/**
 * The little route sketch on each history row.
 *
 * Longitudes are scaled by cos(latitude) so the shape keeps its proportions
 * instead of stretching sideways the further north you ride.
 */
@Composable
fun TrackThumbnail(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    color: Color = TrackColors.Track,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (points.size < 2) return@Box

        Canvas(Modifier.fillMaxSize()) {
            val padding = size.minDimension * 0.14f
            val width = size.width - padding * 2
            val height = size.height - padding * 2
            if (width <= 0f || height <= 0f) return@Canvas

            val latitudes = points.map { it.first }
            val longitudes = points.map { it.second }
            val minLat = latitudes.min()
            val maxLat = latitudes.max()
            val minLon = longitudes.min()
            val maxLon = longitudes.max()

            val latScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
            val spanLat = max(maxLat - minLat, 1e-6)
            val spanLon = max((maxLon - minLon) * latScale, 1e-6)

            // Preserve aspect ratio; the smaller axis gets centred.
            val scale = minOf(width / spanLon, height / spanLat)
            val offsetX = padding + (width - spanLon * scale) / 2f
            val offsetY = padding + (height - spanLat * scale) / 2f

            fun project(latitude: Double, longitude: Double) = Offset(
                x = (offsetX + (longitude - minLon) * latScale * scale).toFloat(),
                // Screen y grows downward, latitude grows upward.
                y = (offsetY + (maxLat - latitude) * scale).toFloat(),
            )

            val path = Path()
            points.forEachIndexed { index, (latitude, longitude) ->
                val offset = project(latitude, longitude)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = size.minDimension * 0.055f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
