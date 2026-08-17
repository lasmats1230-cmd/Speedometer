package com.lasse.speedometer.ui.history

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.lasse.speedometer.data.db.TrackPointEntity
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.ProfileChart
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.TrackMap
import com.lasse.speedometer.util.Formatters
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var menuOpen by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Text(
                        text = trip?.let { current ->
                            current.title?.takeIf { it.isNotBlank() }
                                ?: formatTripDate(current.startedAt)
                        }.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_gpx)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.downloadGpx(downloadTemplate, exportFailed)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_gpx)) },
                                onClick = { menuOpen = false; viewModel.shareGpx(exportFailed) },
                            )
                            if (viewModel.healthAvailable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sync_health_connect)) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.syncToHealth(syncedMessage, healthUnavailable)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = { menuOpen = false; viewModel.delete(onBack) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = trip
        if (current == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        TripDetailContent(
            trip = current,
            points = points,
            settings = settings,
            contentPadding = padding,
        )
    }
}

@Composable
private fun TripDetailContent(
    trip: TripEntity,
    points: List<TrackPointEntity>,
    settings: AppSettings,
    contentPadding: PaddingValues,
) {
    val track = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
    val elevations = remember(points) { points.mapNotNull { it.altitudeM?.toFloat() } }
    val speeds = remember(points) { points.map { it.speedMps } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        item("summary") {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.stat_avg),
                    value = Formatters.speed(trip.avgSpeedMps, settings.units),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                StatTile(
                    label = stringResource(R.string.stat_max),
                    value = Formatters.speed(trip.maxSpeedMps, settings.units),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                StatTile(
                    label = stringResource(R.string.stat_distance),
                    value = Formatters.distance(trip.distanceM, settings.units),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                StatTile(
                    label = stringResource(R.string.stat_time),
                    value = Formatters.durationLong(trip.durationMs),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        if (elevations.isNotEmpty()) {
            item("elevation-header") {
                Text(
                    text = stringResource(R.string.elevation),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item("elevation-stats") {
                Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                    StatTile(
                        label = stringResource(R.string.ascent),
                        value = Formatters.elevation(trip.ascentM, settings.units),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.descent),
                        value = Formatters.elevation(trip.descentM, settings.units),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.altitude),
                        value = Formatters.elevation(
                            trip.maxAltitudeM ?: elevations.max().toDouble(),
                            settings.units,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item("elevation-profile") {
                ChartCard(title = stringResource(R.string.elevation_profile)) {
                    ProfileChart(
                        values = elevations,
                        startLabel = stringResource(
                            R.string.lowest,
                            Formatters.elevation(elevations.min().toDouble(), settings.units),
                        ),
                        endLabel = stringResource(
                            R.string.highest,
                            Formatters.elevation(elevations.max().toDouble(), settings.units),
                        ),
                    )
                }
            }
        }

        if (speeds.size >= 2) {
            item("speed-profile") {
                ChartCard(title = stringResource(R.string.speed_profile)) {
                    ProfileChart(
                        values = speeds,
                        zeroBased = true,
                        startLabel = Formatters.speed(0.0, settings.units),
                        endLabel = Formatters.speed(trip.maxSpeedMps, settings.units),
                    )
                }
            }
        }

        item("details") {
            DetailTable(trip = trip, points = points, settings = settings)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_all_statistics),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
