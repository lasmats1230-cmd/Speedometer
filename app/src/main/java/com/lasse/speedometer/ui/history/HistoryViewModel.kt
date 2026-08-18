package com.lasse.speedometer.ui.history

import android.app.Application
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.R
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.repo.TourSummary
import com.lasse.speedometer.data.repo.TripListItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot things the screen has to react to, like a snackbar or a chooser. */
sealed interface HistoryEvent {
    data class Message(val text: String) : HistoryEvent
    data class Share(val intent: Intent) : HistoryEvent
}

/** How the trip list is ordered. */
enum class TripSort(@param:StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    DISTANCE(R.string.sort_distance),
    DURATION(R.string.sort_duration),
    SPEED(R.string.sort_speed),
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository
    private val exporter = application.app.tripExporter
    private val health = application.app.healthConnectManager

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(TripSort.NEWEST)
    val sort: StateFlow<TripSort> = _sort.asStateFlow()

    /** Null means every activity. */
    private val _activityFilter = MutableStateFlow<ActivityType?>(null)
    val activityFilter: StateFlow<ActivityType?> = _activityFilter.asStateFlow()

    private val allTrips: StateFlow<List<TripListItem>> = repository.tripListItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether anything is recorded at all — which is a different empty state
     * from "your search matched nothing", and wants different words.
     */
    val hasAnyTrips: StateFlow<Boolean> = allTrips
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The activities actually present, so the filter row offers no dead chips. */
    val presentActivities: StateFlow<List<ActivityType>> = allTrips
        .map { trips ->
            ActivityType.entries.filter { activity ->
                trips.any { it.trip.activityType == activity }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trips: StateFlow<List<TripListItem>> =
        combine(allTrips, _query, _sort, _activityFilter) { trips, query, sort, activity ->
            val needle = query.trim().lowercase()
            trips
                .filter { activity == null || it.trip.activityType == activity }
                .filter { item ->
                    needle.isEmpty() ||
                        item.trip.title?.lowercase()?.contains(needle) == true ||
                        item.trip.note?.lowercase()?.contains(needle) == true ||
                        formatTripDate(item.trip.startedAt).lowercase().contains(needle)
                }
                .sortedWith(
                    when (sort) {
                        TripSort.NEWEST -> compareByDescending { it.trip.startedAt }
                        TripSort.OLDEST -> compareBy { it.trip.startedAt }
                        TripSort.DISTANCE -> compareByDescending { it.trip.distanceM }
                        TripSort.DURATION -> compareByDescending { it.trip.durationMs }
                        TripSort.SPEED -> compareByDescending { it.trip.maxSpeedMps }
                    }
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tours: StateFlow<List<TourSummary>> = repository.tourSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tourList: StateFlow<List<TourEntity>> = repository.tourList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val healthAvailable: Boolean get() = health.isAvailable

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSort(value: TripSort) {
        _sort.value = value
    }

    fun setActivityFilter(value: ActivityType?) {
        _activityFilter.value = value
    }

    fun deleteTrip(id: Long) = viewModelScope.launch { repository.deleteTrip(id) }

    fun deleteAllTrips() = viewModelScope.launch { repository.deleteAllTrips() }

    fun renameTrip(id: Long, title: String) =
        viewModelScope.launch { repository.renameTrip(id, title) }

    fun setActivity(id: Long, activity: ActivityType) =
        viewModelScope.launch { repository.setTripActivity(id, activity) }

    fun createTourWith(tripId: Long, name: String) = viewModelScope.launch {
        val tourId = repository.createTour(name)
        repository.addTripToTour(tripId, tourId)
    }

    fun addTripToTour(tripId: Long, tourId: Long?) =
        viewModelScope.launch { repository.addTripToTour(tripId, tourId) }

    fun deleteTour(id: Long) = viewModelScope.launch { repository.deleteTour(id) }

    fun renameTour(id: Long, name: String) =
        viewModelScope.launch { repository.renameTour(id, name) }

    fun downloadGpx(tripId: Long, successTemplate: String, failure: String) =
        viewModelScope.launch {
            val trip = repository.getTrip(tripId) ?: return@launch
            val points = repository.getPoints(tripId)
            exporter.saveToDownloads(trip, points)
                .onSuccess { _events.emit(HistoryEvent.Message(successTemplate.format(it))) }
                .onFailure { _events.emit(HistoryEvent.Message(failure)) }
        }

    fun shareGpx(tripId: Long, failure: String) = viewModelScope.launch {
        val trip = repository.getTrip(tripId) ?: return@launch
        val points = repository.getPoints(tripId)
        exporter.shareIntent(trip, points)
            .onSuccess { _events.emit(HistoryEvent.Share(it)) }
            .onFailure { _events.emit(HistoryEvent.Message(failure)) }
    }

    /** Shares the trip as words rather than a file, for a chat window. */
    fun shareSummary(text: String) = viewModelScope.launch {
        _events.emit(HistoryEvent.Share(TripExporterIntents.text(text)))
    }

    fun syncToHealth(tripId: Long, success: String, unavailable: String) =
        viewModelScope.launch {
            if (!health.isAvailable) {
                _events.emit(HistoryEvent.Message(unavailable))
                return@launch
            }
            health.writeTrip(tripId)
                .onSuccess { _events.emit(HistoryEvent.Message(success)) }
                .onFailure {
                    _events.emit(
                        HistoryEvent.Message(it.message ?: unavailable)
                    )
                }
        }
}

/** Intents that need no file behind them. */
object TripExporterIntents {
    fun text(body: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
}
