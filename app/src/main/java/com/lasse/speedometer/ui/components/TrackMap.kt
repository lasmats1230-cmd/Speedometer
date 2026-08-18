package com.lasse.speedometer.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lasse.speedometer.data.prefs.MapStyle
import com.lasse.speedometer.ui.theme.TrackColors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

/** A latitude/longitude pair, kept free of map-library types above this layer. */
data class LatLng(val latitude: Double, val longitude: Double)

/** A saved place, as the map needs to draw it. */
data class MapWaypoint(
    val id: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val colorArgb: Int,
)

/**
 * The map, drawn by MapLibre from OpenStreetMap vector tiles, with the
 * recorded track, an optional route to follow, and the current position on top.
 *
 * The track and position live in style sources this composable owns, rather
 * than in an annotation plugin, so a moving recording only pushes new GeoJSON
 * instead of rebuilding overlay objects every second.
 */
@Composable
fun TrackMap(
    modifier: Modifier = Modifier,
    track: List<LatLng> = emptyList(),
    /**
     * Several tracks at once, for a tour: each is drawn as its own line, so
     * the map does not join the end of one ride to the start of the next.
     */
    tracks: List<List<LatLng>> = emptyList(),
    route: List<LatLng> = emptyList(),
    currentPosition: LatLng? = null,
    bearingDeg: Float? = null,
    waypoints: List<MapWaypoint> = emptyList(),
    followPosition: Boolean = true,
    fitTrack: Boolean = false,
    /** Bump this to snap the map back onto [currentPosition] once. */
    recenterSignal: Int = 0,
    zoom: Double = 16.5,
    onMapLongPress: ((LatLng) -> Unit)? = null,
    onWaypointClick: ((Long) -> Unit)? = null,
) {
    // Taken from the scheme actually in use rather than the system setting:
    // with the app forced to light on a dark phone, a dark basemap under a
    // light interface is the sort of mismatch that reads as unfinished.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val style = LocalMapStyle.current
    val density = LocalDensity.current.density
    val trackColor = TrackColors.Track.toArgb()
    val routeColor = Color(0xFF4FA3FF).toArgb()
    val ringColor = if (darkTheme) 0xFF0B0F0D.toInt() else 0xFFFFFFFF.toInt()

    val labelColor = if (darkTheme) 0xFFE6E9E7.toInt() else 0xFF16191A.toInt()
    val labelHalo = if (darkTheme) 0xFF0B0F0D.toInt() else 0xFFFFFFFF.toInt()

    val mapView = rememberMapView()
    val state = remember { TrackMapState() }

    // Held in the state object so the map's listeners, registered once, always
    // reach the callbacks from the newest composition.
    state.onMapLongPress = onMapLongPress
    state.onWaypointClick = onWaypointClick

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = {
            state.pending = TrackMapData(
                track = track,
                tracks = tracks,
                route = route,
                position = currentPosition,
                bearingDeg = bearingDeg,
                waypoints = waypoints,
                labelColor = labelColor,
                labelHalo = labelHalo,
                followPosition = followPosition,
                fitTrack = fitTrack,
                recenterSignal = recenterSignal,
                zoom = zoom,
                styleUri = style.uri(darkTheme),
                trackColor = trackColor,
                routeColor = routeColor,
                ringColor = ringColor,
                density = density,
            )
            state.apply(mapView)
        },
    )
}

/** The snapshot of everything the map should be showing right now. */
private data class TrackMapData(
    val track: List<LatLng>,
    val tracks: List<List<LatLng>>,
    val route: List<LatLng>,
    val position: LatLng?,
    val bearingDeg: Float?,
    val waypoints: List<MapWaypoint>,
    val labelColor: Int,
    val labelHalo: Int,
    val followPosition: Boolean,
    val fitTrack: Boolean,
    val recenterSignal: Int,
    val zoom: Double,
    val styleUri: String,
    val trackColor: Int,
    val routeColor: Int,
    val ringColor: Int,
    val density: Float,
)

/**
 * Bridges Compose's synchronous updates to MapLibre's asynchronous map and
 * style callbacks: the latest data is held here and replayed once each is ready.
 */
private class TrackMapState {

    var pending: TrackMapData? = null
    var onMapLongPress: ((LatLng) -> Unit)? = null
    var onWaypointClick: ((Long) -> Unit)? = null

    private var gesturesRegistered = false
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var loadedStyleUri: String? = null
    private var styleLoading = false

    private var hasCentered = false
    private var lastRecenterSignal: Int? = null

    fun apply(mapView: MapView) {
        val data = pending ?: return
        val currentMap = map
        if (currentMap == null) {
            mapView.getMapAsync { ready ->
                map = ready
                ready.uiSettings.isRotateGesturesEnabled = false
                ready.uiSettings.isTiltGesturesEnabled = false
                registerGestures(ready)
                apply(mapView)
            }
            return
        }
        applyStyle(currentMap, data)
    }

    /**
     * Registered once against the map, not per recomposition, so repeated
     * updates cannot stack duplicate listeners.
     */
    private fun registerGestures(map: MapLibreMap) {
        if (gesturesRegistered) return
        gesturesRegistered = true

        map.addOnMapLongClickListener { point ->
            val callback = onMapLongPress
            callback?.invoke(LatLng(point.latitude, point.longitude))
            callback != null
        }

        map.addOnMapClickListener { point ->
            val callback = onWaypointClick ?: return@addOnMapClickListener false
            val screenPoint = map.projection.toScreenLocation(point)
            // A finger is wider than a marker, so search a box around the tap.
            val touchSlop = TOUCH_SLOP_PX
            val box = android.graphics.RectF(
                screenPoint.x - touchSlop,
                screenPoint.y - touchSlop,
                screenPoint.x + touchSlop,
                screenPoint.y + touchSlop,
            )
            val hit = map.queryRenderedFeatures(box, LAYER_WAYPOINT_DOT)
                .firstOrNull { it.hasProperty(PROPERTY_WAYPOINT_ID) }
                ?.getNumberProperty(PROPERTY_WAYPOINT_ID)
                ?.toLong()
            if (hit != null) callback(hit)
            hit != null
        }
    }

    private fun applyStyle(map: MapLibreMap, data: TrackMapData) {
        if (loadedStyleUri != data.styleUri) {
            if (styleLoading) return
            styleLoading = true
            style = null
            map.setStyle(Style.Builder().fromUri(data.styleUri)) { loaded ->
                styleLoading = false
                loadedStyleUri = data.styleUri
                style = loaded
                // The style replaced every layer, so the app's own go back on.
                installLayers(loaded, pending ?: data)
                pending?.let { applyData(map, loaded, it) }
            }
            return
        }
        val loaded = style ?: return
        applyData(map, loaded, data)
    }

    private fun installLayers(style: Style, data: TrackMapData) {
        style.addSource(GeoJsonSource(SOURCE_ROUTE))
        style.addSource(GeoJsonSource(SOURCE_TRACK))
        style.addSource(GeoJsonSource(SOURCE_WAYPOINTS))
        style.addSource(GeoJsonSource(SOURCE_POSITION))
        style.addImage(ICON_HEADING, headingBitmap(data.density, data.trackColor))

        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(data.routeColor),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        style.addLayer(
            LineLayer(LAYER_TRACK, SOURCE_TRACK).withProperties(
                PropertyFactory.lineColor(data.trackColor),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        // Waypoints sit above the track but below the position marker, so the
        // dot showing where you are is never hidden behind a saved place.
        style.addLayer(
            CircleLayer(LAYER_WAYPOINT_DOT, SOURCE_WAYPOINTS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(Expression.toColor(Expression.get(PROPERTY_COLOR))),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(data.labelHalo),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_WAYPOINT_LABEL, SOURCE_WAYPOINTS).withProperties(
                PropertyFactory.textField(Expression.get(PROPERTY_LABEL)),
                PropertyFactory.textFont(arrayOf(LABEL_FONT)),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(data.labelColor),
                PropertyFactory.textHaloColor(data.labelHalo),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 0.9f)),
                PropertyFactory.textOptional(true),
            )
        )
        style.addLayer(
            CircleLayer(LAYER_POSITION_HALO, SOURCE_POSITION).withProperties(
                PropertyFactory.circleRadius(22f),
                PropertyFactory.circleColor(POSITION_COLOR),
                PropertyFactory.circleOpacity(0.18f),
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_POSITION_HEADING, SOURCE_POSITION).withProperties(
                PropertyFactory.iconImage(ICON_HEADING),
                PropertyFactory.iconRotate(Expression.get(PROPERTY_BEARING)),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ).also { it.setFilter(Expression.has(PROPERTY_BEARING)) }
        )
        style.addLayer(
            CircleLayer(LAYER_POSITION_DOT, SOURCE_POSITION).withProperties(
                PropertyFactory.circleRadius(6.5f),
                PropertyFactory.circleColor(POSITION_COLOR),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(data.ringColor),
            )
        )
    }

    private fun applyData(map: MapLibreMap, style: Style, data: TrackMapData) {
        val lines = if (data.tracks.isNotEmpty()) data.tracks else listOf(data.track)
        style.getSourceAs<GeoJsonSource>(SOURCE_TRACK)?.setGeoJson(multiLineFeatures(lines))
        style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE)?.setGeoJson(lineFeatures(data.route))
        style.getSourceAs<GeoJsonSource>(SOURCE_POSITION)
            ?.setGeoJson(positionFeatures(data.position, data.bearingDeg))
        style.getSourceAs<GeoJsonSource>(SOURCE_WAYPOINTS)
            ?.setGeoJson(waypointFeatures(data.waypoints))

        (style.getLayer(LAYER_POSITION_DOT) as? CircleLayer)
            ?.setProperties(PropertyFactory.circleStrokeColor(data.ringColor))
        (style.getLayer(LAYER_WAYPOINT_LABEL) as? SymbolLayer)?.setProperties(
            PropertyFactory.textColor(data.labelColor),
            PropertyFactory.textHaloColor(data.labelHalo),
        )

        moveCamera(map, data)
    }

    private fun moveCamera(map: MapLibreMap, data: TrackMapData) {
        val recenterRequested = lastRecenterSignal != null &&
            data.recenterSignal != lastRecenterSignal
        lastRecenterSignal = data.recenterSignal

        val fittable = if (data.tracks.isNotEmpty()) data.tracks.flatten() else data.track
        if (data.fitTrack && fittable.size >= 2) {
            val bounds = runCatching {
                LatLngBounds.Builder()
                    .includes(fittable.map { MapLibreLatLng(it.latitude, it.longitude) })
                    .build()
            }.getOrNull() ?: return
            runCatching {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, TRACK_PADDING_PX))
            }
            return
        }

        val position = data.position ?: return
        val target = MapLibreLatLng(position.latitude, position.longitude)
        val firstFix = !hasCentered

        if (!data.followPosition && !recenterRequested && !firstFix) return
        hasCentered = true

        val update = if (firstFix || recenterRequested) {
            CameraUpdateFactory.newLatLngZoom(target, data.zoom)
        } else {
            // Following: keep whatever zoom the rider has chosen.
            CameraUpdateFactory.newLatLng(target)
        }
        map.animateCamera(update, CAMERA_ANIMATION_MS)
    }

    /** One feature per track, so separate rides stay separate lines. */
    private fun multiLineFeatures(lines: List<List<LatLng>>): FeatureCollection {
        val features = lines.filter { it.size >= 2 }.map { points ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    points.map { Point.fromLngLat(it.longitude, it.latitude) }
                )
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun lineFeatures(points: List<LatLng>): FeatureCollection {
        if (points.size < 2) return FeatureCollection.fromFeatures(emptyList())
        val line = LineString.fromLngLats(
            points.map { Point.fromLngLat(it.longitude, it.latitude) }
        )
        return FeatureCollection.fromFeature(Feature.fromGeometry(line))
    }

    private fun waypointFeatures(waypoints: List<MapWaypoint>): FeatureCollection {
        if (waypoints.isEmpty()) return FeatureCollection.fromFeatures(emptyList())
        return FeatureCollection.fromFeatures(
            waypoints.map { waypoint ->
                Feature.fromGeometry(
                    Point.fromLngLat(waypoint.longitude, waypoint.latitude)
                ).apply {
                    addNumberProperty(PROPERTY_WAYPOINT_ID, waypoint.id)
                    addStringProperty(PROPERTY_LABEL, waypoint.label)
                    // MapLibre expressions read colours as CSS strings.
                    addStringProperty(
                        PROPERTY_COLOR,
                        String.format("#%06X", 0xFFFFFF and waypoint.colorArgb),
                    )
                }
            }
        )
    }

    private fun positionFeatures(position: LatLng?, bearingDeg: Float?): FeatureCollection {
        if (position == null) return FeatureCollection.fromFeatures(emptyList())
        val feature = Feature.fromGeometry(
            Point.fromLngLat(position.longitude, position.latitude)
        )
        bearingDeg?.let { feature.addNumberProperty(PROPERTY_BEARING, it) }
        return FeatureCollection.fromFeature(feature)
    }

    /**
     * The heading arrow, drawn onto a square canvas with the position at its
     * centre so rotating the icon swings the arrow around the dot — no offset
     * juggling needed.
     */
    private fun headingBitmap(density: Float, color: Int): Bitmap {
        val size = (44f * density).toInt().coerceAtLeast(16)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(size / 2f, size * 0.06f)
            lineTo(size * 0.36f, size * 0.30f)
            lineTo(size * 0.64f, size * 0.30f)
            close()
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    private companion object {
        const val SOURCE_TRACK = "speedometer-track-source"
        const val SOURCE_ROUTE = "speedometer-route-source"
        const val SOURCE_POSITION = "speedometer-position-source"
        const val SOURCE_WAYPOINTS = "speedometer-waypoint-source"
        const val LAYER_WAYPOINT_DOT = "speedometer-waypoint-dot"
        const val LAYER_WAYPOINT_LABEL = "speedometer-waypoint-label"
        const val PROPERTY_WAYPOINT_ID = "waypointId"
        const val PROPERTY_LABEL = "label"
        const val PROPERTY_COLOR = "color"

        /** The one font stack the basemap styles ship glyphs for. */
        const val LABEL_FONT = "Noto Sans Regular"
        const val TOUCH_SLOP_PX = 28f
        const val LAYER_TRACK = "speedometer-track-layer"
        const val LAYER_ROUTE = "speedometer-route-layer"
        const val LAYER_POSITION_HALO = "speedometer-position-halo"
        const val LAYER_POSITION_HEADING = "speedometer-position-heading"
        const val LAYER_POSITION_DOT = "speedometer-position-dot"
        const val ICON_HEADING = "speedometer-heading-icon"
        const val PROPERTY_BEARING = "bearing"

        const val POSITION_COLOR = 0xFF2F7BFF.toInt()
        const val TRACK_PADDING_PX = 96
        const val CAMERA_ANIMATION_MS = 700
    }
}

/**
 * A [MapView] wired to the composition's lifecycle.
 *
 * MapLibre holds native resources, so every lifecycle callback has to be
 * forwarded or the renderer leaks the surface when the screen goes away.
 */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDestroy() }
    }

    return mapView
}
