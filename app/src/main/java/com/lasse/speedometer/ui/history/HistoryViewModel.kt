package com.lasse.speedometer.ui.history

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.TourEntity
import com.lasse.speedometer.data.repo.TourSummary
import com.lasse.speedometer.data.repo.TripListItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot things the screen has to react to, like a snackbar or a chooser. */
sealed interface HistoryEvent {
    data class Message(val text: String) : HistoryEvent
    data class Share(val intent: Intent) : HistoryEvent
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository
    private val exporter = application.app.tripExporter
    private val health = application.app.healthConnectManager

    val trips: StateFlow<List<TripListItem>> = repository.tripListItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tours: StateFlow<List<TourSummary>> = repository.tourSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tourList: StateFlow<List<TourEntity>> = repository.tourList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val healthAvailable: Boolean get() = health.isAvailable

    fun deleteTrip(id: Long) = viewModelScope.launch { repository.deleteTrip(id) }

    fun deleteAllTrips() = viewModelScope.launch { repository.deleteAllTrips() }

    fun renameTrip(id: Long, title: String) =
        viewModelScope.launch { repository.renameTrip(id, title) }

    fun createTourWith(tripId: Long, name: String) = viewModelScope.launch {
        val tourId = repository.createTour(name)
        repository.addTripToTour(tripId, tourId)
    }

    fun addTripToTour(tripId: Long, tourId: Long?) =
        viewModelScope.launch { repository.addTripToTour(tripId, tourId) }

    fun deleteTour(id: Long) = viewModelScope.launch { repository.deleteTour(id) }

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
