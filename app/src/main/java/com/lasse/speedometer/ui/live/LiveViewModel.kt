package com.lasse.speedometer.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.data.io.RouteParser
import com.lasse.speedometer.data.io.RoutePoint
import com.lasse.speedometer.ui.components.LatLng
import com.lasse.speedometer.ui.components.MapWaypoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The route the user chose to follow, shared between Tools and Live. */
object ActiveRoute {
    private val _routeId = MutableStateFlow<Long?>(null)
    val routeId: StateFlow<Long?> = _routeId.asStateFlow()

    fun follow(id: Long) {
        _routeId.value = id
    }

    fun clear() {
        _routeId.value = null
    }

    fun toggle(id: Long) {
        _routeId.value = if (_routeId.value == id) null else id
    }
}

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository
    private val settingsRepository = application.app.settingsRepository

    /**
     * The activity the next recording is filed as. Kept in settings rather
     * than in this view model so the tracking service can read it when the
     * trip is saved, with the screen long gone.
     */
    fun setActivity(activity: ActivityType) =
        viewModelScope.launch { settingsRepository.setActivity(activity) }

    fun markOnboarded() = viewModelScope.launch { settingsRepository.setOnboarded(true) }

    /**
     * Every saved trip, for the summary shown before a recording starts.
     *
     * The figures are derived in the composition rather than here, the same
     * way the statistics screen does it: they need the user's units to be
     * readable, and totalling a few hundred rows costs less than the
     * recomposition that draws them.
     */
    val trips: StateFlow<List<TripEntity>> = repository.trips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * A recording that was still open when the app last stopped.
     *
     * Only interesting while nothing is being recorded — during a recording
     * this is the row currently being written, which is not something to
     * offer back to the user.
     */
    val interruptedTrip: StateFlow<TripEntity?> = repository.inProgressTrip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recoverInterrupted(tripId: Long) = viewModelScope.launch {
        repository.recoverRecording(tripId)
    }

    fun discardInterrupted(tripId: Long) = viewModelScope.launch {
        repository.discardRecording(tripId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val followedRoutePoints: StateFlow<List<RoutePoint>> = ActiveRoute.routeId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeRoute(id)
        }
        .map { route -> route?.let { RouteParser.decode(it.encodedPoints) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The same route as map coordinates. */
    val followedRoute: StateFlow<List<LatLng>> = followedRoutePoints
        .map { points -> points.map { LatLng(it.latitude, it.longitude) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val waypoints: StateFlow<List<MapWaypoint>> = repository.waypoints
        .map { list ->
            list.map { waypoint ->
                MapWaypoint(
                    id = waypoint.id,
                    label = waypoint.label,
                    latitude = waypoint.latitude,
                    longitude = waypoint.longitude,
                    colorArgb = waypoint.colorArgb,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The waypoint being edited, once loaded from the database. */
    private val _editingWaypoint = MutableStateFlow<WaypointEntity?>(null)
    val editingWaypoint: StateFlow<WaypointEntity?> = _editingWaypoint.asStateFlow()

    fun openWaypoint(id: Long) = viewModelScope.launch {
        _editingWaypoint.value = repository.getWaypoint(id)
    }

    fun closeWaypoint() {
        _editingWaypoint.value = null
    }

    fun addWaypoint(latitude: Double, longitude: Double, label: String, note: String, color: Int) =
        viewModelScope.launch {
            repository.addWaypoint(label, latitude, longitude, color, note)
        }

    fun updateWaypoint(waypoint: WaypointEntity, label: String, note: String, color: Int) =
        viewModelScope.launch {
            repository.updateWaypoint(
                waypoint.copy(
                    label = label.ifBlank { waypoint.label },
                    note = note.ifBlank { null },
                    colorArgb = color,
                )
            )
            _editingWaypoint.value = null
        }

    fun deleteWaypoint(id: Long) = viewModelScope.launch {
        repository.deleteWaypoint(id)
        _editingWaypoint.value = null
    }
}
