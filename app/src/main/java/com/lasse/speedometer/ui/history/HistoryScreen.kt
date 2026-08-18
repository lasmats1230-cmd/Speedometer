package com.lasse.speedometer.ui.history

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.repo.TourSummary
import com.lasse.speedometer.data.repo.TripListItem
import com.lasse.speedometer.ui.components.CenteredEmptyState
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.ListCard
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.ScreenTitle
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.ui.components.TrackThumbnail
import com.lasse.speedometer.ui.components.icon
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
    val hasAnyTrips by viewModel.hasAnyTrips.collectAsState()
    val tours by viewModel.tours.collectAsState()
    val tourList by viewModel.tourList.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val activityFilter by viewModel.activityFilter.collectAsState()
    val presentActivities by viewModel.presentActivities.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var tripPendingDelete by remember { mutableStateOf<Long?>(null) }
    var tripForTour by remember { mutableStateOf<Long?>(null) }
    var tripBeingRenamed by remember { mutableStateOf<TripEntity?>(null) }
    var tripChangingActivity by remember { mutableStateOf<TripEntity?>(null) }
    var tourBeingRenamed by remember { mutableStateOf<TourSummary?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

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
        ScreenTitle(
            title = stringResource(R.string.history),
            actions = {
                if (selectedTab == 0 && hasAnyTrips) {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_by),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            TripSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = option == sort,
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        sortMenuOpen = false
                                        viewModel.setSort(option)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { confirmDeleteAll = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.delete_all),
                        )
                    }
                }
            },
        )

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
            0 -> {
                if (hasAnyTrips) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_trips)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.clear),
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.Screen),
                    )

                    // Only worth offering when there is more than one kind of
                    // trip to tell apart.
                    if (presentActivities.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = Dimens.Screen),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = activityFilter == null,
                                onClick = { viewModel.setActivityFilter(null) },
                                label = { Text(stringResource(R.string.filter_all)) },
                            )
                            presentActivities.forEach { activity ->
                                FilterChip(
                                    selected = activityFilter == activity,
                                    onClick = {
                                        viewModel.setActivityFilter(
                                            if (activityFilter == activity) null else activity
                                        )
                                    },
                                    label = { Text(stringResource(activity.labelRes)) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when {
                    !hasAnyTrips -> CenteredEmptyState(
                        icon = Icons.AutoMirrored.Outlined.DirectionsBike,
                        title = stringResource(R.string.no_trips_title),
                        body = stringResource(R.string.no_trips_body),
                    )

                    trips.isEmpty() -> CenteredEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.no_matches_title),
                        body = stringResource(R.string.no_matches_body),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = Dimens.Screen,
                            end = Dimens.Screen,
                            bottom = Dimens.BottomGap,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Item),
                    ) {
                        items(trips, key = { it.trip.id }) { item ->
                            TripRow(
                                item = item,
                                settings = settings,
                                healthAvailable = viewModel.healthAvailable,
                                onClick = { onOpenTrip(item.trip.id) },
                                onDownload = {
                                    viewModel.downloadGpx(
                                        item.trip.id,
                                        downloadTemplate,
                                        exportFailed,
                                    )
                                },
                                onShare = { viewModel.shareGpx(item.trip.id, exportFailed) },
                                onShareSummary = viewModel::shareSummary,
                                onSync = {
                                    viewModel.syncToHealth(
                                        item.trip.id,
                                        syncedMessage,
                                        healthUnavailable,
                                    )
                                },
                                onRename = { tripBeingRenamed = item.trip },
                                onChangeActivity = { tripChangingActivity = item.trip },
                                onAddToTour = { tripForTour = item.trip.id },
                                onDelete = { tripPendingDelete = item.trip.id },
                            )
                        }
                    }
                }
            }

            else -> if (tours.isEmpty()) {
                CenteredEmptyState(
                    icon = Icons.Outlined.Luggage,
                    title = stringResource(R.string.no_tours_title),
                    body = stringResource(R.string.no_tours_body),
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
                    items(tours, key = { it.tour.id }) { summary ->
                        TourRow(
                            summary = summary,
                            settings = settings,
                            onClick = { onOpenTour(summary.tour.id) },
                            onRename = { tourBeingRenamed = summary },
                            onDelete = { viewModel.deleteTour(summary.tour.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.delete_all_title),
            message = stringResource(R.string.delete_all_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                confirmDeleteAll = false
                viewModel.deleteAllTrips()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }

    tripPendingDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.delete_trip_title),
            message = stringResource(R.string.delete_trip_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                tripPendingDelete = null
                viewModel.deleteTrip(id)
            },
            onDismiss = { tripPendingDelete = null },
        )
    }

    tripBeingRenamed?.let { trip ->
        TextFieldDialog(
            title = stringResource(R.string.rename_trip),
            label = stringResource(R.string.trip_name),
            initial = trip.title.orEmpty(),
            onDismiss = { tripBeingRenamed = null },
            onConfirm = { name ->
                viewModel.renameTrip(trip.id, name)
                tripBeingRenamed = null
            },
        )
    }

    tripChangingActivity?.let { trip ->
        ActivityDialog(
            selected = trip.activityType,
            onDismiss = { tripChangingActivity = null },
            onSelect = { activity ->
                viewModel.setActivity(trip.id, activity)
                tripChangingActivity = null
            },
        )
    }

    tourBeingRenamed?.let { summary ->
        TextFieldDialog(
            title = stringResource(R.string.rename_tour),
            label = stringResource(R.string.tour_name),
            initial = summary.tour.name,
            onDismiss = { tourBeingRenamed = null },
            onConfirm = { name ->
                viewModel.renameTour(summary.tour.id, name)
                tourBeingRenamed = null
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
private fun TripRow(
    item: TripListItem,
    settings: AppSettings,
    healthAvailable: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onShareSummary: (String) -> Unit,
    onSync: () -> Unit,
    onRename: () -> Unit,
    onChangeActivity: () -> Unit,
    onAddToTour: () -> Unit,
    onDelete: () -> Unit,
) {
    val trip = item.trip
    val units = settings.units
    val summary = tripSummaryText(trip, settings)

    val actions = buildList {
        add(MenuAction(stringResource(R.string.rename_trip), onClick = onRename))
        add(MenuAction(stringResource(R.string.change_activity), onClick = onChangeActivity))
        add(MenuAction(stringResource(R.string.share_summary)) { onShareSummary(summary) })
        add(MenuAction(stringResource(R.string.share_gpx), onClick = onShare))
        add(MenuAction(stringResource(R.string.download_gpx), onClick = onDownload))
        if (healthAvailable) {
            add(MenuAction(stringResource(R.string.sync_health_connect), onClick = onSync))
        }
        add(MenuAction(stringResource(R.string.add_to_tour), onClick = onAddToTour))
        add(
            MenuAction(
                label = stringResource(R.string.delete),
                destructive = true,
                onClick = onDelete,
            )
        )
    }

    ListCard(onClick = onClick) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = trip.activityType.icon,
                        contentDescription = stringResource(trip.activityType.labelRes),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 0.dp),
                    )
                    Text(
                        text = trip.title?.takeIf { it.isNotBlank() }
                            ?: formatTripDate(trip.startedAt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = Formatters.distance(trip.distanceM, units),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                StatLine(
                    label = stringResource(R.string.stat_avg),
                    value = if (trip.activityType.prefersPace) {
                        Formatters.pace(trip.avgSpeedMps, units)
                    } else {
                        Formatters.speed(trip.avgSpeedMps, units)
                    },
                )
                StatLine(
                    label = stringResource(R.string.stat_max),
                    value = Formatters.speed(trip.maxSpeedMps, units),
                )
                StatLine(
                    label = stringResource(R.string.stat_time),
                    value = Formatters.durationLong(trip.durationMs),
                )
                if (trip.ascentM >= 1 || trip.descentM >= 1) {
                    Text(
                        text = "↑ ${Formatters.elevation(trip.ascentM, units)}" +
                            "   ↓ ${Formatters.elevation(trip.descentM, units)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OverflowMenu(actions = actions, contentDescription = stringResource(R.string.more))
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Text(
        text = "$label $value",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TourRow(
    summary: TourSummary,
    settings: AppSettings,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ListCard(onClick = onClick) {
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
                    text = pluralStringResource(
                        R.plurals.trips_count,
                        summary.tripCount,
                        summary.tripCount,
                    ),
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
            OverflowMenu(
                actions = listOf(
                    MenuAction(stringResource(R.string.rename_tour), onClick = onRename),
                    MenuAction(
                        label = stringResource(R.string.delete),
                        destructive = true,
                        onClick = onDelete,
                    ),
                ),
                contentDescription = stringResource(R.string.more),
            )
        }
    }
}

/** The app's one confirmation dialog, so every "are you sure" looks alike. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Naming a trip, a tour or anything else with one line of text. */
@Composable
internal fun TextFieldDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowEmpty: Boolean = false,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = allowEmpty || value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Re-filing a recorded trip as a different kind of activity. */
@Composable
internal fun ActivityDialog(
    selected: ActivityType,
    onSelect: (ActivityType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_activity)) },
        text = {
            Column {
                ActivityType.entries.forEach { activity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(activity) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = activity == selected, onClick = null)
                        Icon(
                            imageVector = activity.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Text(
                            text = stringResource(activity.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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
private val tripTimeFormat = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault())

internal fun formatTripDate(millis: Long): String = tripDateFormat.format(Date(millis))

internal fun formatTripTime(millis: Long): String = tripTimeFormat.format(Date(millis))
