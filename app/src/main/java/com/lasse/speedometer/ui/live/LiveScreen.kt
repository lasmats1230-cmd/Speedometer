package com.lasse.speedometer.ui.live

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.data.prefs.MinimapSize
import com.lasse.speedometer.data.repo.DaySummary
import com.lasse.speedometer.data.repo.RecordKind
import com.lasse.speedometer.data.repo.RouteProgress
import com.lasse.speedometer.data.repo.RouteTracker
import com.lasse.speedometer.data.repo.StatsCalculator
import com.lasse.speedometer.tracking.TrackingController
import com.lasse.speedometer.tracking.TrackingStatus
import com.lasse.speedometer.ui.ImmersiveMode
import com.lasse.speedometer.ui.components.ActivityPicker
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.ui.theme.SpeedDisplayStyle
import com.lasse.speedometer.ui.theme.SpeedUnitStyle
import com.lasse.speedometer.ui.theme.TimerStyle
import com.lasse.speedometer.ui.theme.TrackColors
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun LiveScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    viewModel: LiveViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by TrackingController.state.collectAsState()
    val savedTripId by TrackingController.savedTripId.collectAsState()
    val followedRoute by viewModel.followedRoute.collectAsState()
    val followedRoutePoints by viewModel.followedRoutePoints.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val editingWaypoint by viewModel.editingWaypoint.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var pendingFinish by remember { mutableStateOf<FinishAction?>(null) }
    var pendingStart by remember { mutableStateOf(false) }
    var newWaypointAt by remember { mutableStateOf<LatLng?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission && pendingStart) TrackingController.start(context)
        pendingStart = false
    }

    val idle = state.status == TrackingStatus.IDLE
    DisposableEffect(hasLocationPermission, idle) {
        if (hasLocationPermission && idle) TrackingController.observeIdleLocation(context)
        onDispose { if (idle) TrackingController.stopIdleLocation(context) }
    }

    val savedMessage = stringResource(R.string.saved)
    // Resolved here rather than in the effect: the effect is not a composition
    // and cannot reach a resource, and there are only four of them.
    val bestLabels = RecordKind.entries.associateWith { stringResource(it.beatenRes) }
    LaunchedEffect(savedTripId) {
        val id = savedTripId ?: return@LaunchedEffect
        // Anything the trip just beat is worth saying instead of "Saved" —
        // that is the one moment a personal best means something, and the
        // statistics tab will still be there tomorrow.
        val beaten = viewModel.personalBests(id).mapNotNull(bestLabels::get)
        snackbarHostState.showSnackbar(
            message = if (beaten.isEmpty()) {
                savedMessage
            } else {
                (listOf(savedMessage) + beaten).joinToString(" · ")
            },
            duration = if (beaten.isEmpty()) SnackbarDuration.Short else SnackbarDuration.Long,
        )
        TrackingController.consumeSavedTrip()
    }

    // The always-on readout belongs to cycling mode alone. Extreme mode is
    // built around the screen being off, so it never takes the display over —
    // doing so would leave no way to stop the recording.
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dimmed by remember { mutableStateOf(false) }
    val recording = state.status == TrackingStatus.RECORDING ||
        state.status == TrackingStatus.ACQUIRING
    val cyclingMode = settings.batterySaver == BatterySaverMode.CYCLING
    val extremeMode = settings.batterySaver == BatterySaverMode.EXTREME

    LaunchedEffect(cyclingMode, recording, lastInteraction, settings.dimDelaySeconds) {
        dimmed = if (recording && cyclingMode) {
            delay(settings.dimDelaySeconds * 1000L)
            true
        } else {
            false
        }
    }

    // The dim readout takes the whole panel, including the system bars and the
    // app's own navigation bar.
    DisposableEffect(dimmed) {
        ImmersiveMode.set(dimmed)
        onDispose { ImmersiveMode.set(false) }
    }

    // Over the limit the readout turns red and the phone buzzes once. It is
    // computed before the dim branch so the pared-back display warns too —
    // that is the screen you are actually looking at while moving.
    val overLimit = rememberSpeedAlert(state.speedMps, settings)

    if (dimmed) {
        DimDisplay(
            state = state,
            settings = settings,
            overLimit = overLimit,
            onWake = {
                dimmed = false
                lastInteraction = System.currentTimeMillis()
            },
        )
        return
    }

    // A row left open by a recording the system killed. Offered back only
    // when nothing is recording now — mid-ride this is simply the trip being
    // written.
    val interrupted by viewModel.interruptedTrip.collectAsState()
    // Also required to be stale: a row is still open for a moment after a
    // recording stops, and offering to recover the trip just saved would be a
    // dialog flashing past for no reason. A recording writes every ten
    // seconds, so anything untouched for a minute was interrupted.
    interrupted
        ?.takeIf { idle && System.currentTimeMillis() - it.endedAt > RECOVERY_STALE_MS }
        ?.let { trip ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.recover_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recover_message,
                        Formatters.distance(trip.distanceM, settings.units),
                        Formatters.durationLong(trip.durationMs),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.recoverInterrupted(trip.id) }) {
                    Text(stringResource(R.string.recover_keep))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardInterrupted(trip.id) }) {
                    Text(
                        text = stringResource(R.string.discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }

    // First run: say what the app does before asking for anything.
    if (!settings.onboarded) {
        OnboardingDialog(
            onGrantLocation = {
                viewModel.markOnboarded()
                if (!hasLocationPermission) permissionLauncher.launch(locationPermissions())
            },
            onDismiss = viewModel::markOnboarded,
        )
    }

    val track = remember(state.pointCount) {
        state.track.map { LatLng(it.latitude, it.longitude) }
    }
    val now = System.currentTimeMillis()
    val layout = settings.layout

    // Today's figures, for the idle view. Keyed on the date rather than the
    // clock: this screen recomposes every second while recording, and the
    // answer only changes at midnight or when a trip is saved.
    val savedTrips by viewModel.trips.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now / DAY_CHECK_MS) {
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }
    val daySummary = remember(savedTrips, today) {
        StatsCalculator.daySummary(savedTrips, today, zone)
    }

    // Landscape is the car-mount and handlebar case: the readout and the map
    // sit side by side rather than stacking, so neither is squeezed into a
    // strip. Extreme mode and a hidden minimap have nothing to put beside the
    // numbers, so they keep the single column.
    val landscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val showMap = !extremeMode && layout.minimapSize != MinimapSize.HIDDEN

    // Following a route: how much is left, and whether you are still on it.
    // The route's own measurements are worked out once — this runs on every
    // fix, during composition, and a long GPX is tens of thousands of points.
    val preparedRoute = remember(followedRoutePoints) {
        RouteTracker.prepare(followedRoutePoints)
    }
    val routeProgress = remember(preparedRoute, state.latitude, state.longitude) {
        val latitude = state.latitude
        val longitude = state.longitude
        if (preparedRoute == null || latitude == null || longitude == null) {
            null
        } else {
            RouteTracker.progress(preparedRoute, latitude, longitude)
        }
    }

    val readout: @Composable (Modifier) -> Unit = { modifier ->
        ReadoutPane(
            modifier = modifier,
            routeProgress = routeProgress,
            // At the pace held so far, which is a better guess on a long
            // climb than the speed at this instant.
            routeEtaMs = routeProgress?.let { progress ->
                val speed = state.avgSpeedMps
                if (speed > 0.5) ((progress.remainingM / speed) * 1000).toLong() else null
            },
            state = state,
            settings = settings,
            idle = idle,
            daySummary = daySummary,
            overLimit = overLimit,
            hasLocationPermission = hasLocationPermission,
            now = now,
            onRequestPermission = {
                pendingStart = false
                permissionLauncher.launch(locationPermissions())
            },
            onSelectActivity = viewModel::setActivity,
            onStart = {
                lastInteraction = System.currentTimeMillis()
                if (hasLocationPermission) {
                    TrackingController.start(context)
                } else {
                    pendingStart = true
                    permissionLauncher.launch(locationPermissions())
                }
            },
            onPause = { TrackingController.pause(context) },
            onResume = { TrackingController.resume(context) },
            onSave = { pendingFinish = FinishAction.SAVE },
            onDiscard = { pendingFinish = FinishAction.DISCARD },
        )
    }

    val map: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            TrackMap(
                modifier = Modifier.fillMaxSize(),
                track = track,
                route = followedRoute,
                waypoints = waypoints,
                currentPosition = state.latitude?.let { latitude ->
                    state.longitude?.let { longitude -> LatLng(latitude, longitude) }
                },
                bearingDeg = state.bearingDeg,
                followPosition = true,
                onMapLongPress = { newWaypointAt = it },
                onWaypointClick = viewModel::openWaypoint,
            )
        }
    }

    if (landscape && showMap) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            readout(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            )
            map(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // With the map hidden there is nothing to stretch, so the column
            // scrolls instead of leaving the controls floating in dead space.
            val scrollable = !showMap

            readout(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollable) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
            )

            if (extremeMode) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.battery_extreme_map_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            } else if (showMap) {
                val mapModifier = when (layout.minimapSize) {
                    MinimapSize.SMALL -> Modifier.height(150.dp)
                    MinimapSize.MEDIUM -> Modifier.height(260.dp)
                    else -> Modifier.weight(1f)
                }
                map(
                    Modifier
                        .fillMaxWidth()
                        .then(mapModifier)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }

    newWaypointAt?.let { position ->
        WaypointEditorDialog(
            existing = null,
            onDismiss = { newWaypointAt = null },
            onSave = { label, note, color ->
                viewModel.addWaypoint(position.latitude, position.longitude, label, note, color)
                newWaypointAt = null
            },
        )
    }

    editingWaypoint?.let { waypoint ->
        WaypointEditorDialog(
            existing = waypoint,
            onDismiss = viewModel::closeWaypoint,
            onSave = { label, note, color ->
                viewModel.updateWaypoint(waypoint, label, note, color)
            },
            onDelete = { viewModel.deleteWaypoint(waypoint.id) },
        )
    }

    pendingFinish?.let { action ->
        val saving = action == FinishAction.SAVE
        AlertDialog(
            onDismissRequest = { pendingFinish = null },
            title = {
                Text(
                    stringResource(
                        if (saving) R.string.save_trip_title else R.string.discard_trip_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (saving) R.string.save_trip_message else R.string.discard_trip_message
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingFinish = null
                    if (saving) {
                        TrackingController.stopAndSave(context)
                    } else {
                        TrackingController.stopAndDiscard(context)
                    }
                }) {
                    Text(
                        text = stringResource(if (saving) R.string.save else R.string.discard),
                        color = if (saving) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFinish = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Everything above the map: status, activity, the speed, the tiles, the timer
 * and the controls. Pulled out so portrait can stack it over the map and
 * landscape can stand it beside one.
 */
@Composable
private fun ReadoutPane(
    modifier: Modifier,
    routeProgress: RouteProgress?,
    routeEtaMs: Long?,
    state: com.lasse.speedometer.tracking.TrackingState,
    settings: AppSettings,
    idle: Boolean,
    /** Today's figures, or null while there is nothing worth showing. */
    daySummary: DaySummary?,
    overLimit: Boolean,
    hasLocationPermission: Boolean,
    now: Long,
    onRequestPermission: () -> Unit,
    onSelectActivity: (com.lasse.speedometer.data.db.ActivityType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val layout = settings.layout
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        if (layout.showStatusChip) {
            StatusChip(
                status = state.status,
                accuracyM = state.accuracyM,
                hasPermission = hasLocationPermission,
                settings = settings,
                onRequestPermission = onRequestPermission,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (routeProgress != null) {
            RouteChip(progress = routeProgress, etaMs = routeEtaMs, settings = settings)
            Spacer(Modifier.height(10.dp))
        }

        // Which activity this is only matters before the recording starts;
        // once it is running the row would just be one more thing between the
        // speed and the stop button. It stays changeable afterwards from the
        // trip's own menu.
        if (idle) {
            ActivityPicker(
                selected = settings.activity,
                onSelect = onSelectActivity,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (daySummary != null) {
                TodaySummary(
                    summary = daySummary,
                    settings = settings,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }

        val spokenSpeed = stringResource(
            R.string.speed_spoken,
            Formatters.speed(state.speedMps, settings.units),
        )
        Text(
            text = Formatters.bigSpeed(state.speedMps, settings.units),
            style = SpeedDisplayStyle,
            color = if (overLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            // Head-up display: the phone lies face up on the dashboard and the
            // windscreen does the flipping back.
            modifier = (if (layout.hudMirror) Modifier.mirrored() else Modifier)
                // A screen reader gets the whole reading with its unit; the
                // bare digits on their own would be meaningless aloud.
                .semantics { contentDescription = spokenSpeed },
        )
        Text(
            text = if (overLimit) {
                stringResource(
                    R.string.speed_over_limit,
                    Formatters.speed(settings.speedAlertMps.toDouble(), settings.units),
                )
            } else {
                Formatters.speedUnit(settings.units)
            },
            style = SpeedUnitStyle,
            color = if (overLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (layout.stats.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            StatGrid(settings = settings, state = state, now = now)
        }

        if (layout.showTimer) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = Formatters.duration(state.elapsedMs),
                style = TimerStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(14.dp))

        TransportControls(
            status = state.status,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onSave = onSave,
            onDiscard = onDiscard,
        )

        Spacer(Modifier.height(18.dp))
    }
}

/**
 * Progress along a followed route: what is left, or how far off it you are.
 *
 * The second case matters more than the first — a route you are no longer on
 * has a remaining distance that means nothing.
 */
@Composable
private fun RouteChip(progress: RouteProgress, etaMs: Long?, settings: AppSettings) {
    val off = progress.offRoute
    Surface(
        shape = CircleShape,
        color = if (off) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = if (off) {
                stringResource(
                    R.string.route_off,
                    Formatters.elevation(progress.offRouteM, settings.units),
                )
            } else if (etaMs != null) {
                stringResource(
                    R.string.route_remaining_eta,
                    Formatters.distance(progress.remainingM, settings.units),
                    Formatters.duration(etaMs),
                )
            } else {
                stringResource(
                    R.string.route_remaining,
                    Formatters.distance(progress.remainingM, settings.units),
                )
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (off) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Which of the two irreversible endings the user asked to confirm. */
private enum class FinishAction { SAVE, DISCARD }

/** The user's chosen tiles, wrapped into rows of [LayoutSettings.statColumns]. */
@Composable
private fun StatGrid(
    settings: AppSettings,
    state: com.lasse.speedometer.tracking.TrackingState,
    now: Long,
) {
    val columns = settings.layout.statColumns.coerceIn(1, 4)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        settings.layout.stats.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { stat ->
                    StatTile(
                        label = stringResource(stat.labelRes),
                        value = LiveStats.value(stat, state, settings, now),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                // Keeps a short last row aligned with the rows above it.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: TrackingStatus,
    accuracyM: Float?,
    hasPermission: Boolean,
    settings: AppSettings,
    onRequestPermission: () -> Unit,
) {
    val label = when {
        !hasPermission -> stringResource(R.string.status_no_permission)
        status == TrackingStatus.IDLE -> stringResource(R.string.status_ready)
        status == TrackingStatus.PAUSED -> stringResource(R.string.status_paused)
        status == TrackingStatus.ACQUIRING -> stringResource(R.string.status_waiting)
        else -> stringResource(
            R.string.status_tracking,
            accuracyM?.let { Formatters.accuracy(it, settings.units) } ?: "—",
        )
    }

    val dotColor = when {
        !hasPermission -> MaterialTheme.colorScheme.error
        status == TrackingStatus.RECORDING -> TrackColors.Recording
        status == TrackingStatus.PAUSED -> TrackColors.Paused
        status == TrackingStatus.ACQUIRING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.Transparent
    }

    val transition = rememberInfiniteTransition(label = "recording-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "recording-pulse-alpha",
    )
    val animatedDot by animateColorAsState(dotColor, label = "dot-color")

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(enabled = !hasPermission, onClick = onRequestPermission),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasPermission) {
                Icon(
                    imageVector = Icons.Filled.LocationDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            } else if (dotColor != Color.Transparent) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            animatedDot.copy(
                                alpha = if (status == TrackingStatus.RECORDING) alpha else 1f
                            )
                        )
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Save and discard are deliberately separate buttons rather than two answers
 * to one dialog: they are opposite outcomes, and one of them is irreversible.
 * Both still confirm before acting.
 */
@Composable
private fun TransportControls(
    status: TrackingStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val active = status != TrackingStatus.IDLE

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
            ControlButton(
                icon = Icons.Filled.Check,
                contentDescription = stringResource(R.string.save_trip),
                onClick = onSave,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        when (status) {
            TrackingStatus.IDLE -> ControlButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.start),
                onClick = onStart,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )

            TrackingStatus.PAUSED -> ControlButton(
                icon = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.resume),
                onClick = onResume,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )

            else -> ControlButton(
                icon = Icons.Filled.Pause,
                contentDescription = stringResource(R.string.pause),
                onClick = onPause,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurface,
            )
        }

        AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
            ControlButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.discard_trip),
                onClick = onDiscard,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Every control is the same diameter, and every glyph is drawn into the same
 * box, so no button reads as larger than its neighbour.
 */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.size(CONTROL_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(CONTROL_ICON_SIZE),
            )
        }
    }
}

/** Flips a composable horizontally, for reading a reflection. */
internal fun Modifier.mirrored(): Modifier = graphicsLayer(scaleX = -1f)

/** Long enough that a row still open is not simply one being written. */
private const val RECOVERY_STALE_MS = 60_000L

/**
 * How coarsely the clock is rounded before asking what day it is. A minute is
 * fine enough that midnight is noticed while the app is open, and coarse
 * enough that the answer is not recomputed on every recomposition.
 */
private const val DAY_CHECK_MS = 60_000L

private val CONTROL_SIZE = 68.dp
private val CONTROL_ICON_SIZE = 30.dp

private fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
