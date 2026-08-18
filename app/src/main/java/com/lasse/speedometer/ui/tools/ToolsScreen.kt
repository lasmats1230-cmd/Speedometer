package com.lasse.speedometer.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
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

@Composable
fun ToolsScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    viewModel: ToolsViewModel = viewModel(),
) {
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title = stringResource(R.string.tools))

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.tab_compass),
                stringResource(R.string.tab_map),
                stringResource(R.string.tab_routes),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            icons = listOf(Icons.Outlined.Explore, Icons.Filled.Map, Icons.Outlined.Route),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> CompassPane(Modifier.weight(1f))
            1 -> MapPane(Modifier.weight(1f))
            else -> RoutesPane(
                settings = settings,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
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

@Composable
private fun RoutesPane(
    settings: AppSettings,
    viewModel: ToolsViewModel,
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
                        onClick = { ActiveRoute.toggle(route.id) },
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
