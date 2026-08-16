package com.lasse.speedometer.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.io.RouteParser
import com.lasse.speedometer.ui.components.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val followedRoute: StateFlow<List<LatLng>> = ActiveRoute.routeId
        .flatMapLatest { id ->
            if (id == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                repository.observeRoute(id)
            }
        }
        .map { route ->
            route?.let {
                RouteParser.decode(it.encodedPoints).map { point ->
                    LatLng(point.latitude, point.longitude)
                }
            }.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
