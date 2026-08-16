package com.lasse.speedometer.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bridge between the UI and [TrackingService].
 *
 * The service owns the recording; this object is how the rest of the app
 * observes it and sends commands, so nothing has to bind to the service.
 */
object TrackingController {

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    /** Emits the id of a trip the moment it lands in the database. */
    private val _savedTripId = MutableStateFlow<Long?>(null)
    val savedTripId: StateFlow<Long?> = _savedTripId.asStateFlow()

    internal fun publish(state: TrackingState) {
        _state.value = state
    }

    internal fun publishSavedTrip(id: Long) {
        _savedTripId.value = id
    }

    fun consumeSavedTrip() {
        _savedTripId.value = null
    }

    fun start(context: Context) = send(context, TrackingService.ACTION_START)
    fun pause(context: Context) = send(context, TrackingService.ACTION_PAUSE)
    fun resume(context: Context) = send(context, TrackingService.ACTION_RESUME)
    fun stopAndSave(context: Context) = send(context, TrackingService.ACTION_STOP_SAVE)
    fun stopAndDiscard(context: Context) = send(context, TrackingService.ACTION_STOP_DISCARD)

    /** Keeps the map warm while idle; harmless if permission is missing. */
    fun observeIdleLocation(context: Context) = send(context, TrackingService.ACTION_IDLE_WATCH)

    /** Drops the idle subscription once no map is on screen. */
    fun stopIdleLocation(context: Context) = send(context, TrackingService.ACTION_IDLE_STOP)

    private fun send(context: Context, action: String) {
        val intent = Intent(context, TrackingService::class.java).setAction(action)
        if (action == TrackingService.ACTION_START) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
