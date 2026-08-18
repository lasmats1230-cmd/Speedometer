package com.lasse.speedometer.ui.history

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.io.TripCardText
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.data.repo.Split
import com.lasse.speedometer.data.repo.Splits
import com.lasse.speedometer.data.repo.StatsCalculator
import com.lasse.speedometer.ui.components.ActivityBadge
import com.lasse.speedometer.ui.components.ChartSample
import com.lasse.speedometer.ui.components.DetailRow
import com.lasse.speedometer.ui.components.DetailScaffold
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.ProfileChart
import com.lasse.speedometer.ui.components.SectionCard
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.util.Formatters
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripDetailViewModel(
    application: Application,
    private val tripId: Long,
) : androidx.lifecycle.AndroidViewModel(application) {

    private val repository = application.app.tripRepository
    private val exporter = application.app.tripExporter
    private val health = application.app.healthConnectManager

    val trip: StateFlow<TripEntity?> = repository.observeTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val points: StateFlow<List<TrackPointEntity>> = repository.observePoints(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every other trip, for the "faster than usual" line. */
    val allTrips: StateFlow<List<TripEntity>> = repository.trips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val healthAvailable: Boolean get() = health.isAvailable

    fun downloadGpx(successTemplate: String, failure: String) = viewModelScope.launch {
        val current = trip.value ?: return@launch
        exporter.saveToDownloads(current, repository.getPoints(tripId))
            .onSuccess { _events.emit(HistoryEvent.Message(successTemplate.format(it))) }
            .onFailure { _events.emit(HistoryEvent.Message(failure)) }
    }

    fun shareGpx(failure: String) = viewModelScope.launch {
        val current = trip.value ?: return@launch
        exporter.shareIntent(current, repository.getPoints(tripId))
            .onSuccess { _events.emit(HistoryEvent.Share(it)) }
            .onFailure { _events.emit(HistoryEvent.Message(failure)) }
    }

    fun shareSummary(text: String) = viewModelScope.launch {
        _events.emit(HistoryEvent.Share(TripExporterIntents.text(text)))
    }

    fun shareCard(text: TripCardText, failure: String) = viewModelScope.launch {
        val current = trip.value ?: return@launch
        exporter.shareCard(current, repository.getPoints(tripId), text)
            .onSuccess { _events.emit(HistoryEvent.Share(it)) }
            .onFailure { _events.emit(HistoryEvent.Message(failure)) }
    }

    fun rename(title: String) = viewModelScope.launch { repository.renameTrip(tripId, title) }

    fun setNote(note: String) = viewModelScope.launch { repository.setTripNote(tripId, note) }

    fun setActivity(activity: ActivityType) =
        viewModelScope.launch { repository.setTripActivity(tripId, activity) }

    fun syncToHealth(success: String, unavailable: String) = viewModelScope.launch {
        if (!health.isAvailable) {
            _events.emit(HistoryEvent.Message(unavailable))
            return@launch
        }
        health.writeTrip(tripId)
            .onSuccess { _events.emit(HistoryEvent.Message(success)) }
            .onFailure { _events.emit(HistoryEvent.Message(it.message ?: unavailable)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteTrip(tripId)
        onDone()
    }

    class Factory(
        private val application: Application,
        private val tripId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TripDetailViewModel(application, tripId) as T
    }
}

@Composable
fun TripDetailScreen(
    tripId: Long,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: TripDetailViewModel = viewModel(
        factory = TripDetailViewModel.Factory(context.app, tripId),
        key = "trip-$tripId",
    )
    val trip by viewModel.trip.collectAsState()
    val points by viewModel.points.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var changingActivity by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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

    val current = trip
    val summary = current?.let { tripSummaryText(it, settings) }.orEmpty()
    val cardText = tripCardText(current, settings)

    DetailScaffold(
        title = current?.let { entry ->
            entry.title?.takeIf { it.isNotBlank() } ?: formatTripDate(entry.startedAt)
        }.orEmpty(),
        onBack = onBack,
        actions = {
            OverflowMenu(
                actions = buildList {
                    add(MenuAction(stringResource(R.string.rename_trip)) { renaming = true })
                    add(MenuAction(stringResource(R.string.edit_note)) { editingNote = true })
                    add(
                        MenuAction(stringResource(R.string.change_activity)) {
                            changingActivity = true
                        }
                    )
                    add(
                        MenuAction(stringResource(R.string.share_summary)) {
                            viewModel.shareSummary(summary)
                        }
                    )
                    add(
                        MenuAction(stringResource(R.string.share_image)) {
                            viewModel.shareCard(cardText, exportFailed)
                        }
                    )
                    add(
                        MenuAction(stringResource(R.string.share_gpx)) {
                            viewModel.shareGpx(exportFailed)
                        }
                    )
                    add(
                        MenuAction(stringResource(R.string.download_gpx)) {
                            viewModel.downloadGpx(downloadTemplate, exportFailed)
                        }
                    )
                    if (viewModel.healthAvailable) {
                        add(
                            MenuAction(stringResource(R.string.sync_health_connect)) {
                                viewModel.syncToHealth(syncedMessage, healthUnavailable)
                            }
                        )
                    }
                    add(
                        MenuAction(
                            label = stringResource(R.string.delete),
                            destructive = true,
                            onClick = { confirmDelete = true },
                        )
                    )
                },
                contentDescription = stringResource(R.string.more),
            )
        },
    ) { padding ->
        if (current == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@DetailScaffold
        }

        TripDetailContent(
            trip = current,
            points = points,
            allTrips = allTrips,
            settings = settings,
            contentPadding = padding,
        )
    }

    if (renaming && current != null) {
        TextFieldDialog(
            title = stringResource(R.string.rename_trip),
            label = stringResource(R.string.trip_name),
            initial = current.title.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }

    if (editingNote && current != null) {
        TextFieldDialog(
            title = stringResource(R.string.edit_note),
            label = stringResource(R.string.note),
            initial = current.note.orEmpty(),
            allowEmpty = true,
            onDismiss = { editingNote = false },
            onConfirm = {
                viewModel.setNote(it)
                editingNote = false
            },
        )
    }

    if (changingActivity && current != null) {
        ActivityDialog(
            selected = current.activityType,
            onDismiss = { changingActivity = false },
            onSelect = {
                viewModel.setActivity(it)
                changingActivity = false
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_trip_title),
            message = stringResource(R.string.delete_trip_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TripDetailContent(
    trip: TripEntity,
    points: List<TrackPointEntity>,
    allTrips: List<TripEntity>,
    settings: AppSettings,
    contentPadding: PaddingValues,
) {
    val track = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }

    // Elevation reads against distance travelled, the way a route profile is
    // normally drawn; speed reads against elapsed time.
    val elevationSamples = remember(points) {
        points.mapNotNull { point ->
            point.altitudeM?.let {
                ChartSample(x = point.cumulativeDistanceM.toFloat(), y = it.toFloat())
            }
        }
    }
    val startedAt = remember(points) { points.firstOrNull()?.timestamp ?: trip.startedAt }
    val speedSamples = remember(points) {
        points.map { point ->
            ChartSample(x = (point.timestamp - startedAt).toFloat(), y = point.speedMps)
        }
    }
    val elevations = remember(elevationSamples) { elevationSamples.map { it.y } }
    val splitUnit = if (settings.units == UnitSystem.METRIC) {
        Splits.KILOMETRE_M
    } else {
        Splits.MILE_M
    }
    val splits = remember(points, splitUnit) { Splits.compute(points, splitUnit) }

    // How this one sat against the others of its kind, which is what you
    // actually want to know when you open a ride you have just finished.
    val comparison = remember(trip, allTrips) {
        StatsCalculator.comparedWithUsual(trip, allTrips)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.Screen,
            end = Dimens.Screen,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + Dimens.BottomGap,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.Item),
    ) {
        item("map") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                TrackMap(
                    modifier = Modifier.fillMaxSize(),
                    track = track,
                    fitTrack = true,
                    followPosition = false,
                )
            }
        }

        item("heading") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActivityBadge(activity = trip.activityType)
                Text(
                    text = formatTripDate(trip.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!trip.note.isNullOrBlank()) {
            item("note") {
                SectionCard {
                    Text(
                        text = trip.note,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        if (comparison != null) {
            item("comparison") {
                val percent = (comparison * 100).roundToInt()
                Text(
                    text = when {
                        percent >= 3 -> stringResource(
                            R.string.trip_faster_than_usual,
                            percent,
                            stringResource(trip.activityType.labelRes),
                        )

                        percent <= -3 -> stringResource(
                            R.string.trip_slower_than_usual,
                            -percent,
                            stringResource(trip.activityType.labelRes),
                        )

                        else -> stringResource(
                            R.string.trip_typical,
                            stringResource(trip.activityType.labelRes),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (percent >= 3) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        item("summary") {
            TileRow(
                tiles = listOf(
                    stringResource(R.string.stat_avg) to
                        Formatters.speed(trip.avgSpeedMps, settings.units),
                    stringResource(R.string.stat_max) to
                        Formatters.speed(trip.maxSpeedMps, settings.units),
                    stringResource(R.string.stat_distance) to
                        Formatters.distance(trip.distanceM, settings.units),
                    stringResource(R.string.stat_time) to
                        Formatters.durationLong(trip.durationMs),
                )
            )
        }

        if (elevations.isNotEmpty()) {
            item("elevation-stats") {
                TileRow(
                    tiles = listOf(
                        stringResource(R.string.ascent) to
                            Formatters.elevation(trip.ascentM, settings.units),
                        stringResource(R.string.descent) to
                            Formatters.elevation(trip.descentM, settings.units),
                        stringResource(R.string.altitude) to Formatters.elevation(
                            trip.maxAltitudeM ?: elevations.max().toDouble(),
                            settings.units,
                        ),
                    )
                )
            }

            item("elevation-profile") {
                SectionCard(
                    title = stringResource(R.string.elevation_profile),
                    subtitle = stringResource(R.string.axis_distance_altitude),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    val lowest = stringResource(R.string.lowest, "%s")
                    val highest = stringResource(R.string.highest, "%s")
                    ProfileChart(
                        samples = elevationSamples,
                        formatX = { Formatters.distance(it.toDouble(), settings.units) },
                        formatY = { Formatters.elevation(it.toDouble(), settings.units) },
                        describeLow = {
                            lowest.format(Formatters.elevation(it.toDouble(), settings.units))
                        },
                        describeHigh = {
                            highest.format(Formatters.elevation(it.toDouble(), settings.units))
                        },
                    )
                }
            }
        }

        if (speedSamples.size >= 2) {
            item("speed-profile") {
                SectionCard(
                    title = stringResource(R.string.speed_profile),
                    subtitle = stringResource(R.string.axis_time_speed),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    ProfileChart(
                        samples = speedSamples,
                        zeroBased = true,
                        formatX = { Formatters.duration(it.toLong()) },
                        formatY = { Formatters.speed(it.toDouble(), settings.units) },
                    )
                }
            }
        }

        if (splits.size >= 2) {
            item("splits") {
                SplitsCard(splits = splits, trip = trip, settings = settings)
            }
        }

        item("details") {
            DetailTable(trip = trip, points = points, settings = settings)
        }
    }
}

/** A row of equally sized tiles, matched in height whatever the values. */
@Composable
private fun TileRow(tiles: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
    ) {
        tiles.forEach { (label, value) ->
            StatTile(
                label = label,
                value = value,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * Each kilometre or mile as its own line, with a bar showing how it compared
 * to the fastest one — which is the whole point: the eye finds the slow
 * stretch long before it reads the numbers.
 */
@Composable
private fun SplitsCard(splits: List<Split>, trip: TripEntity, settings: AppSettings) {
    val units = settings.units
    val fastest = remember(splits) { splits.maxOf { it.speedMps } }
    val unitLabel = Formatters.distanceUnit(units)

    SectionCard(
        title = stringResource(R.string.splits),
        subtitle = stringResource(R.string.splits_subtitle, unitLabel),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        splits.forEach { split ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (split.partial) {
                        Formatters.distanceValue(split.distanceM, units)
                    } else {
                        split.index.toString()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(
                                if (fastest > 0) {
                                    (split.speedMps / fastest).toFloat().coerceIn(0.06f, 1f)
                                } else {
                                    0.06f
                                }
                            )
                            .height(20.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (split.partial) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ) {}
                }
                Text(
                    text = if (trip.activityType.prefersPace) {
                        Formatters.pace(split.speedMps, units)
                    } else {
                        Formatters.speed(split.speedMps, units)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = Formatters.duration(split.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * The long tail of numbers behind a trip.
 *
 * Kept as a list of label/value rows rather than more tiles: these are figures
 * you read once out of curiosity, not ones you scan at a glance.
 */
@Composable
private fun DetailTable(
    trip: TripEntity,
    points: List<TrackPointEntity>,
    settings: AppSettings,
) {
    val units = settings.units
    val stoppedMs = (trip.durationMs - trip.movingTimeMs).coerceAtLeast(0L)
    val movingSeconds = trip.movingTimeMs / 1000.0
    val avgMovingSpeed = if (movingSeconds > 0) trip.distanceM / movingSeconds else 0.0
    val avgAccuracy = points.takeIf { it.isNotEmpty() }?.map { it.accuracyM }?.average()
    val elevationRange = trip.maxAltitudeM?.let { high ->
        trip.minAltitudeM?.let { low -> high - low }
    }

    val rows = buildList {
        add(stringResource(R.string.activity) to stringResource(trip.activityType.labelRes))
        add(stringResource(R.string.detail_started) to formatTripTime(trip.startedAt))
        add(stringResource(R.string.detail_ended) to formatTripTime(trip.endedAt))
        add(stringResource(R.string.stat_time) to Formatters.durationLong(trip.durationMs))
        add(stringResource(R.string.moving_time) to Formatters.durationLong(trip.movingTimeMs))
        add(stringResource(R.string.detail_stopped_time) to Formatters.durationLong(stoppedMs))
        add(stringResource(R.string.stat_distance) to Formatters.distance(trip.distanceM, units))
        add(stringResource(R.string.stat_avg) to Formatters.speed(trip.avgSpeedMps, units))
        add(stringResource(R.string.detail_avg_moving) to Formatters.speed(avgMovingSpeed, units))
        add(stringResource(R.string.stat_max) to Formatters.speed(trip.maxSpeedMps, units))
        add(stringResource(R.string.detail_avg_pace) to Formatters.pace(avgMovingSpeed, units))
        add(stringResource(R.string.detail_best_pace) to Formatters.pace(trip.maxSpeedMps, units))
        add(stringResource(R.string.ascent) to Formatters.elevation(trip.ascentM, units))
        add(stringResource(R.string.descent) to Formatters.elevation(trip.descentM, units))
        trip.maxAltitudeM?.let {
            add(stringResource(R.string.detail_max_altitude) to Formatters.elevation(it, units))
        }
        trip.minAltitudeM?.let {
            add(stringResource(R.string.detail_min_altitude) to Formatters.elevation(it, units))
        }
        elevationRange?.let {
            add(stringResource(R.string.detail_elevation_range) to Formatters.elevation(it, units))
        }
        add(stringResource(R.string.detail_points) to points.size.toString())
        avgAccuracy?.let {
            add(
                stringResource(R.string.detail_avg_accuracy) to
                    Formatters.elevation(it, units)
            )
        }
        add(
            stringResource(R.string.detail_health_synced) to stringResource(
                if (trip.syncedToHealth) R.string.yes else R.string.no
            )
        )
    }

    SectionCard(
        title = stringResource(R.string.detail_all_statistics),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) -> DetailRow(label = label, value = value) }
        }
    }
}
