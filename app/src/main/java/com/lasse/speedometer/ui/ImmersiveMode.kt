package com.lasse.speedometer.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets a screen take over the whole display.
 *
 * The battery-saving readout is meant to be glanced at on a handlebar mount,
 * so the app's own navigation bar and the system's status and gesture bars all
 * get out of the way. Shared state rather than a parameter because the bars
 * belong to the activity and the navigation scaffold, neither of which the
 * screen asking for this can reach.
 */
object ImmersiveMode {

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(value: Boolean) {
        _enabled.value = value
    }
}
