package com.lasse.speedometer.ui.components

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lasse.speedometer.ui.theme.TrackColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** A latitude/longitude pair, kept free of osmdroid types above this layer. */
data class LatLng(val latitude: Double, val longitude: Double)

/**
 * OpenStreetMap tiles with the recorded track drawn on top.
 *
 * In dark mode the tiles are colour-inverted, which turns the standard OSM
 * raster into the muted night map the rest of the UI is built around.
 */
@Composable
fun TrackMap(
    modifier: Modifier = Modifier,
    track: List<LatLng> = emptyList(),
    route: List<LatLng> = emptyList(),
    currentPosition: LatLng? = null,
    bearingDeg: Float? = null,
    followPosition: Boolean = true,
    fitTrack: Boolean = false,
    /** Bump this to snap the map back onto [currentPosition] once. */
    recenterSignal: Int = 0,
    zoom: Double = 16.5,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val trackColor = TrackColors.Track
    val routeColor = Color(0xFF4FA3FF)

    val mapView = remember { createMapView(context) }
    val trackPolyline = remember { Polyline().apply { outlinePaint.strokeCap = Paint.Cap.ROUND } }
    val routePolyline = remember { Polyline().apply { outlinePaint.strokeCap = Paint.Cap.ROUND } }
    val positionMarker = remember { PositionOverlay() }
    val lastRecenter = remember { intArrayOf(recenterSignal) }
    val hasCentered = remember { booleanArrayOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.overlays.add(routePolyline)
            mapView.overlays.add(trackPolyline)
            mapView.overlays.add(positionMarker)
            mapView
        },
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(
                if (darkTheme) TilesOverlay.INVERT_COLORS else null
            )

            routePolyline.outlinePaint.apply {
                color = routeColor.toArgb()
                strokeWidth = 10f
            }
            routePolyline.setPoints(route.map { GeoPoint(it.latitude, it.longitude) })

            trackPolyline.outlinePaint.apply {
                color = trackColor.toArgb()
                strokeWidth = 14f
            }
            trackPolyline.setPoints(track.map { GeoPoint(it.latitude, it.longitude) })

            positionMarker.update(currentPosition, bearingDeg, darkTheme)

            val recenterRequested = recenterSignal != lastRecenter[0]
            if (recenterRequested) lastRecenter[0] = recenterSignal

            when {
                fitTrack && track.size >= 2 -> {
                    val box = trackPolyline.bounds
                    map.post { runCatching { map.zoomToBoundingBox(box, false, 96) } }
                }

                // Centre once on the first fix even when not following, so a
                // free-panning map still opens somewhere useful.
                currentPosition != null &&
                    (followPosition || recenterRequested || !hasCentered[0]) -> {
                    val firstFix = !hasCentered[0]
                    hasCentered[0] = true
                    val target = GeoPoint(currentPosition.latitude, currentPosition.longitude)
                    if (firstFix || recenterRequested || map.zoomLevelDouble < 4.0) {
                        map.controller.setZoom(zoom)
                    }
                    map.controller.animateTo(target)
                }
            }
            map.invalidate()
        },
    )
}

private fun createMapView(context: Context) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    isTilesScaledToDpi = true
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    setUseDataConnection(true)
    minZoomLevel = 3.0
    maxZoomLevel = 19.0
    controller.setZoom(16.5)
    isHorizontalMapRepetitionEnabled = false
    isVerticalMapRepetitionEnabled = false
}

/**
 * The blue "you are here" dot.
 *
 * Drawn directly rather than via osmdroid's location overlay so it doesn't
 * open a second subscription to the GPS on top of the tracking service.
 */
private class PositionOverlay : org.osmdroid.views.overlay.Overlay() {

    private var position: GeoPoint? = null
    private var bearing: Float? = null
    private var dark = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val headingPath = android.graphics.Path()

    fun update(latLng: LatLng?, bearingDeg: Float?, darkTheme: Boolean) {
        position = latLng?.let { GeoPoint(it.latitude, it.longitude) }
        bearing = bearingDeg
        dark = darkTheme
    }

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val point = position ?: return
        val screen = mapView.projection.toPixels(point, null)
        val density = mapView.resources.displayMetrics.density
        val x = screen.x.toFloat()
        val y = screen.y.toFloat()

        halo.color = 0x332F7BFF
        ring.color = if (dark) 0xFF0B0F0D.toInt() else 0xFFFFFFFF.toInt()
        fill.color = 0xFF2F7BFF.toInt()
        heading.color = TrackColors.Track.toArgb()

        canvas.drawCircle(x, y, 22f * density, halo)

        // A wedge pointing the way you're travelling, drawn outside the dot.
        bearing?.let { degrees ->
            val saved = canvas.save()
            canvas.rotate(degrees, x, y)
            headingPath.reset()
            headingPath.moveTo(x, y - 19f * density)
            headingPath.lineTo(x - 6f * density, y - 10f * density)
            headingPath.lineTo(x + 6f * density, y - 10f * density)
            headingPath.close()
            canvas.drawPath(headingPath, heading)
            canvas.restoreToCount(saved)
        }

        canvas.drawCircle(x, y, 9f * density, ring)
        canvas.drawCircle(x, y, 6.5f * density, fill)
    }
}
