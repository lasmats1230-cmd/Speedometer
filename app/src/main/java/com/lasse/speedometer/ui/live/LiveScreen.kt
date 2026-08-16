package com.lasse.speedometer.ui.live

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.data.prefs.MinimapSize
import com.lasse.speedometer.tracking.TrackingController
import com.lasse.speedometer.tracking.TrackingStatus
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.ui.theme.SpeedDisplayStyle
import com.lasse.speedometer.ui.theme.SpeedUnitStyle
import com.lasse.speedometer.ui.theme.TimerStyle
import com.lasse.speedometer.ui.theme.TrackColors
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.delay

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
    val waypoints by viewModel.waypoints.collectAsState()
    val editingWaypoint by viewModel.editingWaypoint.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var showFinishDialog by remember { mutableStateOf(false) }
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
    LaunchedEffect(savedTripId) {
        if (savedTripId != null) {
            snackbarHostState.showSnackbar(savedMessage)
            TrackingController.consumeSavedTrip()
        }
    }

    // Battery saving: extreme dims as soon as recording starts, cycling waits
    // out the configured delay and comes back at a tap.
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dimmed by remember { mutableStateOf(false) }
    val recording = state.status == TrackingStatus.RECORDING ||
        state.status == TrackingStatus.ACQUIRING

    LaunchedEffect(settings.batterySaver, recording, lastInteraction, settings.dimDelaySeconds) {
        dimmed = when {
            !recording -> false
            settings.batterySaver == BatterySaverMode.EXTREME -> true
            settings.batterySaver == BatterySaverMode.CYCLING -> {
                delay(settings.dimDelaySeconds * 1000L)
                true
            }

            else -> false
        }
    }

    if (dimmed) {
        DimDisplay(
            state = state,
            settings = settings,
            lowBrightness = settings.batterySaver == BatterySaverMode.EXTREME,
            onWake = {
                dimmed = false
                lastInteraction = System.currentTimeMillis()
            },
        )
        return
    }

    val track = remember(state.pointCount) {
        state.track.map { LatLng(it.latitude, it.longitude) }
    }
    val now = System.currentTimeMillis()
    val layout = settings.layout

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // With the map hidden there is nothing to stretch, so the column
        // scrolls instead of leaving the controls floating in dead space.
        val scrollable = layout.minimapSize == MinimapSize.HIDDEN

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            if (layout.showStatusChip) {
                StatusChip(
                    status = state.status,
                    accuracyM = state.accuracyM,
                    hasPermission = hasLocationPermission,
                    settings = settings,
                    onRequestPermission = {
                        pendingStart = false
                        permissionLauncher.launch(locationPermissions())
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = Formatters.bigSpeed(state.speedMps, settings.units),
                style = SpeedDisplayStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = Formatters.speedUnit(settings.units),
                style = SpeedUnitStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (layout.stats.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                StatGrid(
                    settings = settings,
                    state = state,
                    now = now,
                )
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
                onStop = { showFinishDialog = true },
            )

            Spacer(Modifier.height(18.dp))
        }

        if (layout.minimapSize != MinimapSize.HIDDEN) {
            val mapModifier = when (layout.minimapSize) {
                MinimapSize.SMALL -> Modifier.height(150.dp)
                MinimapSize.MEDIUM -> Modifier.height(260.dp)
                else -> Modifier.weight(1f)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(mapModifier)
                    .padding(bottom = 12.dp),
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

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.finish_trip_title)) },
            text = { Text(stringResource(R.string.finish_trip_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    TrackingController.stopAndSave(context)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    TrackingController.stopAndDiscard(context)
                }) { Text(stringResource(R.string.discard)) }
            },
        )
    }
}

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { stat ->
                    StatTile(
                        label = stringResource(stat.labelRes),
                        value = LiveStats.value(stat, state, settings, now),
                        modifier = Modifier.weight(1f),
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

@Composable
private fun TransportControls(
    status: TrackingStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AnimatedVisibility(
            visible = status == TrackingStatus.PAUSED,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ControlButton(
                icon = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.stop),
                onClick = onStop,
                size = 56.dp,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
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

        AnimatedVisibility(
            visible = status == TrackingStatus.RECORDING || status == TrackingStatus.ACQUIRING,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ControlButton(
                icon = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.stop),
                onClick = onStop,
                size = 56.dp,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    size: androidx.compose.ui.unit.Dp = 68.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

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
