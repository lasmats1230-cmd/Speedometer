package com.lasse.speedometer.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.tracking.TrackingController
import com.lasse.speedometer.ui.components.CenteredEmptyState
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.EmptyState
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.ListCard
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.ScreenTitle
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.ui.live.ActiveRoute
import com.lasse.speedometer.ui.live.LiveViewModel
import com.lasse.speedometer.ui.live.WaypointEditorDialog
import com.lasse.speedometer.util.Formatters
import com.lasse.speedometer.util.GeoMath

@Composable
fun ToolsScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onOpenRoute: (Long) -> Unit,
    viewModel: ToolsViewModel = viewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = stringResource(R.string.tools))

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.tab_compass),
                stringResource(R.string.tab_map),
                stringResource(R.string.tab_places),
                stringResource(R.string.tab_routes),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            icons = listOf(
                Icons.Outlined.Explore,
                Icons.Filled.Map,
                Icons.Outlined.Place,
                Icons.Outlined.Route,
            ),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> CompassTab(
                settings = settings,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            1 -> MapPane(Modifier.weight(1f))
            2 -> PlacesPane(
                settings = settings,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )

            else -> RoutesPane(
                settings = settings,
                viewModel = viewModel,
                onOpenRoute = onOpenRoute,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The compass, with an optional place to point it at.
 *
 * A bearing on its own tells you which way you are facing; a bearing towards
 * the water tap you saved last summer tells you which way to go, which is
 * what a compass is for on a walk.
 */
@Composable
private fun CompassTab(
    settings: AppSettings,
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val waypoints by viewModel.waypoints.collectAsState()
    val state by TrackingController.state.collectAsState()
    var targetId by remember { mutableStateOf<Long?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        TrackingController.observeIdleLocation(context)
        onDispose { if (!state.isActive) TrackingController.stopIdleLocation(context) }
    }

    val target = remember(targetId, waypoints, state.latitude, state.longitude) {
        val waypoint = waypoints.firstOrNull { it.id == targetId } ?: return@remember null
        val latitude = state.latitude ?: return@remember null
        val longitude = state.longitude ?: return@remember null
        CompassTarget(
            label = waypoint.label,
            bearingDeg = GeoMath.bearingDegrees(
                latitude,
                longitude,
                waypoint.latitude,
                waypoint.longitude,
            ),
            distanceLabel = Formatters.distance(
                GeoMath.distanceMeters(
                    latitude,
                    longitude,
                    waypoint.latitude,
                    waypoint.longitude,
                ),
                settings.units,
            ),
        )
    }

    CompassPane(
        modifier = modifier,
        target = target,
        picker = {
            if (waypoints.isEmpty()) return@CompassPane
            Box {
                TextButton(onClick = { pickerOpen = true }) {
                    Text(
                        text = waypoints.firstOrNull { it.id == targetId }?.label
                            ?: stringResource(R.string.compass_pick_place),
                    )
                }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.compass_no_place)) },
                        onClick = {
                            pickerOpen = false
                            targetId = null
                        },
                    )
                    waypoints.forEach { waypoint ->
                        DropdownMenuItem(
                            text = { Text(waypoint.label) },
                            onClick = {
                                pickerOpen = false
                                targetId = waypoint.id
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun MapPane(
    modifier: Modifier = Modifier,
    liveViewModel: LiveViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by TrackingController.state.collectAsState()
    val waypoints by liveViewModel.waypoints.collectAsState()
    val editingWaypoint by liveViewModel.editingWaypoint.collectAsState()
    var recenterKey by remember { mutableIntStateOf(0) }
    var newWaypointAt by remember { mutableStateOf<LatLng?>(null) }

    DisposableEffect(Unit) {
        TrackingController.observeIdleLocation(context)
        onDispose {
            if (!state.isActive) TrackingController.stopIdleLocation(context)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(Modifier.weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                TrackMap(
                    modifier = Modifier.fillMaxSize(),
                    track = state.track.map { LatLng(it.latitude, it.longitude) },
                    currentPosition = state.latitude?.let { latitude ->
                        state.longitude?.let { longitude -> LatLng(latitude, longitude) }
                    },
                    bearingDeg = state.bearingDeg,
                    waypoints = waypoints,
                    // Left free to pan; the button below snaps it back.
                    followPosition = false,
                    recenterSignal = recenterKey,
                    onMapLongPress = { newWaypointAt = it },
                    onWaypointClick = liveViewModel::openWaypoint,
                )
            }

            FilledIconButton(
                onClick = { recenterKey++ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.recenter),
                )
            }
        }

        Text(
            text = stringResource(R.string.waypoint_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
    }

    newWaypointAt?.let { position ->
        WaypointEditorDialog(
            existing = null,
            onDismiss = { newWaypointAt = null },
            onSave = { label, note, color ->
                liveViewModel.addWaypoint(
                    position.latitude,
                    position.longitude,
                    label,
                    note,
                    color,
                )
                newWaypointAt = null
            },
        )
    }

    editingWaypoint?.let { waypoint ->
        WaypointEditorDialog(
            existing = waypoint,
            onDismiss = liveViewModel::closeWaypoint,
            onSave = { label, note, color ->
                liveViewModel.updateWaypoint(waypoint, label, note, color)
            },
            onDelete = { liveViewModel.deleteWaypoint(waypoint.id) },
        )
    }
}

/**
 * Saved places as a list rather than pins on a map.
 *
 * A waypoint you dropped last month is impossible to find by panning, and the
 * question you actually have about one — how far away is it — cannot be
 * answered by looking at a marker at all.
 */
@Composable
private fun PlacesPane(
    settings: AppSettings,
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val waypoints by viewModel.waypoints.collectAsState()
    val state by TrackingController.state.collectAsState()
    var editing by remember { mutableStateOf<WaypointEntity?>(null) }

    // A distance needs somewhere to measure from, so the idle fix is kept
    // warm while this list is open — the same subscription the map uses.
    DisposableEffect(Unit) {
        TrackingController.observeIdleLocation(context)
        onDispose { if (!state.isActive) TrackingController.stopIdleLocation(context) }
    }

    val here = state.latitude?.let { latitude ->
        state.longitude?.let { longitude -> latitude to longitude }
    }

    val sorted = remember(waypoints, here) {
        if (here == null) {
            waypoints
        } else {
            waypoints.sortedBy { waypoint ->
                GeoMath.distanceMeters(
                    here.first,
                    here.second,
                    waypoint.latitude,
                    waypoint.longitude,
                )
            }
        }
    }

    if (waypoints.isEmpty()) {
        CenteredEmptyState(
            icon = Icons.Outlined.Place,
            title = stringResource(R.string.no_places_title),
            body = stringResource(R.string.no_places_body),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.Screen,
            end = Dimens.Screen,
            bottom = Dimens.BottomGap,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.Item),
    ) {
        items(sorted, key = { it.id }) { waypoint ->
            val distance = here?.let {
                GeoMath.distanceMeters(it.first, it.second, waypoint.latitude, waypoint.longitude)
            }
            ListCard(onClick = { editing = waypoint }) {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                        end = 4.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(waypoint.colorArgb))
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = waypoint.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        waypoint.note?.takeIf { it.isNotBlank() }?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = distance
                                ?.let {
                                    stringResource(
                                        R.string.place_distance,
                                        Formatters.distance(it, settings.units),
                                    )
                                }
                                ?: stringResource(R.string.place_no_fix),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OverflowMenu(
                        actions = listOf(
                            MenuAction(stringResource(R.string.waypoint_edit)) {
                                editing = waypoint
                            },
                            MenuAction(
                                label = stringResource(R.string.delete),
                                destructive = true,
                                onClick = { viewModel.deleteWaypoint(waypoint.id) },
                            ),
                        ),
                        contentDescription = stringResource(R.string.more),
                    )
                }
            }
        }
    }

    editing?.let { waypoint ->
        WaypointEditorDialog(
            existing = waypoint,
            onDismiss = { editing = null },
            onSave = { label, note, color ->
                viewModel.updateWaypoint(waypoint, label, note, color)
                editing = null
            },
            onDelete = {
                viewModel.deleteWaypoint(waypoint.id)
                editing = null
            },
        )
    }
}

@Composable
private fun RoutesPane(
    settings: AppSettings,
    viewModel: ToolsViewModel,
    onOpenRoute: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routes by viewModel.routes.collectAsState()
    val activeRouteId by ActiveRoute.routeId.collectAsState()

    val importedTemplate = stringResource(R.string.imported_route)
    val importFailed = stringResource(R.string.import_failed)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importRoute(uri, importedTemplate, importFailed)
    }

    Column(modifier.fillMaxSize()) {
        Button(
            onClick = {
                // GPX and TCX have no reliable MIME type across file pickers,
                // so accept anything and let the parser be the judge.
                picker.launch(arrayOf("*/*"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.import_route),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        if (routes.isEmpty()) {
            CenteredEmptyState(
                icon = Icons.Outlined.Route,
                title = stringResource(R.string.no_routes_title),
                body = stringResource(R.string.no_routes_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimens.Screen,
                    end = Dimens.Screen,
                    bottom = Dimens.BottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.Item),
            ) {
                items(routes, key = { it.id }) { route ->
                    val following = activeRouteId == route.id

                    ListCard(
                        selected = following,
                        onClick = { onOpenRoute(route.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 20.dp,
                                top = 16.dp,
                                bottom = 16.dp,
                                end = 4.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = route.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = Formatters.distance(route.distanceM, settings.units) +
                                        "  ·  ↑ " +
                                        Formatters.elevation(route.ascentM, settings.units),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (following) {
                                    Text(
                                        text = stringResource(R.string.following),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            OverflowMenu(
                                actions = listOf(
                                    MenuAction(
                                        label = stringResource(
                                            if (following) {
                                                R.string.stop_following
                                            } else {
                                                R.string.follow_route
                                            }
                                        ),
                                        onClick = { ActiveRoute.toggle(route.id) },
                                    ),
                                    MenuAction(
                                        label = stringResource(R.string.delete),
                                        destructive = true,
                                        onClick = {
                                            if (following) ActiveRoute.clear()
                                            viewModel.deleteRoute(route.id)
                                        },
                                    ),
                                ),
                                contentDescription = stringResource(R.string.more),
                            )
                        }
                    }
                }
            }
        }
    }
}
