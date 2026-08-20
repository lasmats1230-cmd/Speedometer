package com.lasse.speedometer.ui.history

import android.app.Application
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.TourWithTrips
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.ExpandableTrackMap
import com.lasse.speedometer.ui.components.DetailScaffold
import com.lasse.speedometer.ui.components.ListCard
import com.lasse.speedometer.ui.components.MenuAction
import com.lasse.speedometer.ui.components.OverflowMenu
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.ui.components.icon
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TourDetailViewModel(
    application: Application,
    private val tourId: Long,
) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository

    val tour: StateFlow<TourWithTrips?> = repository.observeTour(tourId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every ride in the tour, decimated, for one map of the whole thing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<List<LatLng>>> = tour
        .mapLatest { current ->
            repository.tourTracks(current?.trips.orEmpty().map { it.id })
                .map { track -> track.map { (latitude, longitude) -> LatLng(latitude, longitude) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeTrip(tripId: Long) = viewModelScope.launch {
        repository.addTripToTour(tripId, null)
    }

    fun rename(name: String) = viewModelScope.launch { repository.renameTour(tourId, name) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteTour(tourId)
        onDone()
    }

    class Factory(
        private val application: Application,
        private val tourId: Long,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TourDetailViewModel(application, tourId) as T
    }
}

@Composable
fun TourDetailScreen(
    tourId: Long,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: TourDetailViewModel = viewModel(
        factory = TourDetailViewModel.Factory(context.app, tourId),
        key = "tour-$tourId",
    )
    val tour by viewModel.tour.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DetailScaffold(
        title = tour?.tour?.name.orEmpty(),
        onBack = onBack,
        actions = {
            OverflowMenu(
                actions = listOf(
                    MenuAction(stringResource(R.string.rename_tour)) { renaming = true },
                    MenuAction(
                        label = stringResource(R.string.delete),
                        destructive = true,
                        onClick = { confirmDelete = true },
                    ),
                ),
                contentDescription = stringResource(R.string.more),
            )
        },
    ) { padding ->
        val current = tour ?: return@DetailScaffold Box(Modifier.fillMaxSize())

        val trips = remember(current) { current.trips.sortedByDescending { it.startedAt } }
        val totalDistance = remember(trips) { trips.sumOf { it.distanceM } }
        val totalDuration = remember(trips) { trips.sumOf { it.durationMs } }
        val totalAscent = remember(trips) { trips.sumOf { it.ascentM } }
        val maxSpeed = remember(trips) { trips.maxOfOrNull { it.maxSpeedMps } ?: 0.0 }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.Screen,
                end = Dimens.Screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.BottomGap,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.Item),
        ) {
            if (tracks.any { it.size >= 2 }) {
                item("map") {
                    ExpandableTrackMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        tracks = tracks,
                    )
                }
            }

            item("totals") {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
                ) {
                    StatTile(
                        label = stringResource(R.string.stat_distance),
                        value = Formatters.distance(totalDistance, settings.units),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    StatTile(
                        label = stringResource(R.string.stat_time),
                        value = Formatters.durationLong(totalDuration),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            item("totals2") {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Tile),
                ) {
                    StatTile(
                        label = stringResource(R.string.ascent),
                        value = Formatters.elevation(totalAscent, settings.units),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    StatTile(
                        label = stringResource(R.string.stat_max),
                        value = Formatters.speed(maxSpeed, settings.units),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            items(trips, key = { it.id }) { trip ->
                ListCard(onClick = { onOpenTrip(trip.id) }) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = trip.activityType.icon,
                                    contentDescription = stringResource(
                                        trip.activityType.labelRes
                                    ),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
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
                                text = Formatters.distance(trip.distanceM, settings.units),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = Formatters.durationLong(trip.durationMs),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OverflowMenu(
                            actions = listOf(
                                MenuAction(
                                    label = stringResource(R.string.remove_from_tour),
                                    destructive = true,
                                    onClick = { viewModel.removeTrip(trip.id) },
                                ),
                            ),
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        TextFieldDialog(
            title = stringResource(R.string.rename_tour),
            label = stringResource(R.string.tour_name),
            initial = tour?.tour?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_tour_title),
            message = stringResource(R.string.delete_tour_message),
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
