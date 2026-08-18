package com.lasse.speedometer.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.TripEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Hands the statistics screen the trips and nothing else.
 *
 * Every figure on that screen is derived by [com.lasse.speedometer.data.repo.StatsCalculator],
 * which needs the user's units and locale to be readable — both of which live
 * in the composition. Aggregating here instead would mean pushing formatting
 * down into the view model for no gain: totalling a few hundred rows is
 * cheaper than the recomposition that draws them.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    val trips: StateFlow<List<TripEntity>> = application.app.tripRepository.trips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
