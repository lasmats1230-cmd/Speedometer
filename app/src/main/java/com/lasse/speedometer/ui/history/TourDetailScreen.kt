package com.lasse.speedometer.ui.history

import android.app.Application
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.lasse.speedometer.ui.components.StatTile
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
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

    fun removeTrip(tripId: Long) = viewModelScope.launch {
        repository.addTripToTour(tripId, null)
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

@OptIn(ExperimentalMaterial3Api::class)
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
                title = { Text(tour?.tour?.name.orEmpty()) },
            )
        },
    ) { padding ->
        val current = tour ?: return@Scaffold Box(Modifier.fillMaxSize())

        val trips = remember(current) { current.trips.sortedByDescending { it.startedAt } }
        val totalDistance = remember(trips) { trips.sumOf { it.distanceM } }
        val totalDuration = remember(trips) { trips.sumOf { it.durationMs } }
        val totalAscent = remember(trips) { trips.sumOf { it.ascentM } }
        val maxSpeed = remember(trips) { trips.maxOfOrNull { it.maxSpeedMps } ?: 0.0 }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("totals") {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { onOpenTrip(trip.id) },
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = trip.title?.takeIf { it.isNotBlank() }
                                ?: formatTripDate(trip.startedAt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                }
            }
        }
    }
}
