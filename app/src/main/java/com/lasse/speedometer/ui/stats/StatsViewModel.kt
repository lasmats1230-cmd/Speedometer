package com.lasse.speedometer.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.db.TripEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    private val allTrips: StateFlow<List<TripEntity>> = application.app.tripRepository.trips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null means every activity, matching the History screen's chips. */
    private val _activityFilter = MutableStateFlow<ActivityType?>(null)
    val activityFilter: StateFlow<ActivityType?> = _activityFilter.asStateFlow()

    /**
     * The activities there is anything to show for, so the filter row never
     * offers a chip that would empty the screen.
     */
    val presentActivities: StateFlow<List<ActivityType>> = allTrips
        .map { trips -> ActivityType.entries.filter { a -> trips.any { it.activityType == a } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trips: StateFlow<List<TripEntity>> =
        combine(allTrips, _activityFilter) { trips, activity ->
            if (activity == null) trips else trips.filter { it.activityType == activity }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActivityFilter(value: ActivityType?) {
        _activityFilter.value = value
    }
}
