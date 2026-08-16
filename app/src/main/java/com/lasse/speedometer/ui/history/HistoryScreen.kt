package com.lasse.speedometer.ui.history

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.lasse.speedometer.data.repo.TourSummary
import com.lasse.speedometer.data.repo.TripListItem
import com.lasse.speedometer.ui.components.EmptyState
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.ui.components.TrackThumbnail
import com.lasse.speedometer.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onOpenTrip: (Long) -> Unit,
    onOpenTour: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val trips by viewModel.trips.collectAsState()
    val tours by viewModel.tours.collectAsState()
    val tourList by viewModel.tourList.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var tripPendingDelete by remember { mutableStateOf<Long?>(null) }
    var tripForTour by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is HistoryEvent.Share -> context.startActivity(
                    Intent.createChooser(event.intent, null)
                )
            }
        }
    }

    val downloadTemplate = stringResource(R.string.imported_route)
    val exportFailed = stringResource(R.string.export_failed)
    val syncedMessage = stringResource(R.string.synced)
    val healthUnavailable = stringResource(R.string.health_unavailable)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (selectedTab == 0 && trips.isNotEmpty()) {
                IconButton(onClick = { confirmDeleteAll = true }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.delete_all),
                    )
                }
            }
        }

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.tab_trips),
                stringResource(R.string.tab_tours),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            0 -> if (trips.isEmpty()) {
                CenteredEmpty(
                    icon = Icons.AutoMirrored.Outlined.DirectionsBike,
                    title = stringResource(R.string.no_trips_title),
                    body = stringResource(R.string.no_trips_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(trips, key = { it.trip.id }) { item ->
                        TripRow(
                            item = item,
                            settings = settings,
                            healthAvailable = viewModel.healthAvailable,
                            onClick = { onOpenTrip(item.trip.id) },
                            onDownload = {
                                viewModel.downloadGpx(item.trip.id, downloadTemplate, exportFailed)
                            },
                            onShare = { viewModel.shareGpx(item.trip.id, exportFailed) },
                            onSync = {
                                viewModel.syncToHealth(
                                    item.trip.id,
                                    syncedMessage,
                                    healthUnavailable,
                                )
                            },
                            onAddToTour = { tripForTour = item.trip.id },
                            onDelete = { tripPendingDelete = item.trip.id },
                        )
                    }
                }
            }

            else -> if (tours.isEmpty()) {
                CenteredEmpty(
                    icon = Icons.Outlined.Luggage,
                    title = stringResource(R.string.no_tours_title),
                    body = stringResource(R.string.no_tours_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tours, key = { it.tour.id }) { summary ->
                        TourRow(
                            summary = summary,
                            settings = settings,
                            onClick = { onOpenTour(summary.tour.id) },
                            onDelete = { viewModel.deleteTour(summary.tour.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllTrips()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    tripPendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { tripPendingDelete = null },
            title = { Text(stringResource(R.string.delete_trip_title)) },
            text = { Text(stringResource(R.string.delete_trip_message)) },
            confirmButton = {
                TextButton(onClick = {
                    tripPendingDelete = null
                    viewModel.deleteTrip(id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { tripPendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    tripForTour?.let { tripId ->
        AddToTourDialog(
            tours = tourList,
            onDismiss = { tripForTour = null },
            onPickExisting = { tourId ->
                tripForTour = null
                viewModel.addTripToTour(tripId, tourId)
            },
            onCreate = { name ->
                tripForTour = null
                viewModel.createTourWith(tripId, name)
            },
        )
    }
}

@Composable
private fun CenteredEmpty(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(icon = icon, title = title, body = body)
    }
}

@Composable
private fun TripRow(
    item: TripListItem,
    settings: AppSettings,
    healthAvailable: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onSync: () -> Unit,
    onAddToTour: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val trip = item.trip

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TrackThumbnail(
                points = item.thumbnail,
                modifier = Modifier.size(96.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trip.title?.takeIf { it.isNotBlank() } ?: formatTripDate(trip.startedAt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatters.distance(trip.distanceM, settings.units),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.stat_avg)} " +
                        Formatters.speed(trip.avgSpeedMps, settings.units),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${stringResource(R.string.stat_max)} " +
                        Formatters.speed(trip.maxSpeedMps, settings.units),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Duration ${Formatters.durationLong(trip.durationMs)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (trip.ascentM >= 1 || trip.descentM >= 1) {
                    Text(
                        text = "↑ ${Formatters.elevation(trip.ascentM, settings.units)}" +
                            "   ↓ ${Formatters.elevation(trip.descentM, settings.units)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_gpx)) },
                        onClick = { menuOpen = false; onDownload() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_gpx)) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    if (healthAvailable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_health_connect)) },
                            onClick = { menuOpen = false; onSync() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_tour)) },
                        onClick = { menuOpen = false; onAddToTour() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TourRow(
    summary: TourSummary,
    settings: AppSettings,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = summary.tour.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.trips_count, summary.tripCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatters.distance(summary.distanceM, settings.units) +
                        "  ·  " + Formatters.durationLong(summary.durationMs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToTourDialog(
    tours: List<com.lasse.speedometer.data.db.TourEntity>,
    onDismiss: () -> Unit,
    onPickExisting: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(tours.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_tour)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.tour_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    tours.forEach { tour ->
                        Text(
                            text = tour.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickExisting(tour.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.new_tour),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name.trim()) },
                ) { Text(stringResource(R.string.create)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private val tripDateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

internal fun formatTripDate(millis: Long): String = tripDateFormat.format(Date(millis))
