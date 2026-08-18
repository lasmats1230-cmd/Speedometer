package com.lasse.speedometer.ui.settings

import android.app.Application
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.io.CsvHeadings
import com.lasse.speedometer.data.io.RestoreResult
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.data.prefs.MapStyle
import com.lasse.speedometer.data.prefs.MinimapSize
import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.data.prefs.StatType
import com.lasse.speedometer.data.prefs.ThemeMode
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.ui.theme.AccentColor
import com.lasse.speedometer.util.AppLanguage
import com.lasse.speedometer.util.AppLocale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = application.app.settingsRepository
    private val health = application.app.healthConnectManager
    private val backups = application.app.backupManager

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted: StateFlow<Boolean> = _healthPermissionsGranted.asStateFlow()

    /** True while a backup is being written or read, to hold the buttons. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val language: StateFlow<AppLanguage> = AppLocale.language

    val healthAvailable: Boolean get() = health.isAvailable

    val healthPermissions: Set<String> get() = health.permissions

    init {
        refreshHealthPermissions()
    }

    fun healthPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun refreshHealthPermissions() = viewModelScope.launch {
        _healthPermissionsGranted.value = health.isAvailable && health.hasPermissions()
    }

    /**
     * Changing language needs the activity rebuilt, since resources resolve
     * against the configuration it was created with. Returns true when the
     * caller has to do that restart itself.
     */
    fun setLanguage(value: AppLanguage): Boolean =
        AppLocale.set(getApplication(), value)

    fun setUnits(value: UnitSystem) = update { settingsRepository.setUnits(value) }
    fun setThemeMode(value: ThemeMode) = update { settingsRepository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = update { settingsRepository.setDynamicColor(value) }
    fun setAccentColor(value: AccentColor) = update { settingsRepository.setAccentColor(value) }
    fun setPureBlack(value: Boolean) = update { settingsRepository.setPureBlack(value) }
    fun setKeepScreenOn(value: Boolean) = update { settingsRepository.setKeepScreenOn(value) }
    fun setAutoPause(value: Boolean) = update { settingsRepository.setAutoPause(value) }
    fun setMinAccuracy(value: Float) = update { settingsRepository.setMinAccuracy(value) }
    fun setSpeedSource(value: SpeedSource) = update { settingsRepository.setSpeedSource(value) }
    fun setAutoSyncHealth(value: Boolean) = update { settingsRepository.setAutoSyncHealth(value) }
    fun setBatterySaver(value: BatterySaverMode) =
        update { settingsRepository.setBatterySaver(value) }

    fun setDimDelaySeconds(value: Int) = update { settingsRepository.setDimDelaySeconds(value) }
    fun setSpeedAlert(mps: Float) = update { settingsRepository.setSpeedAlert(mps) }
    fun setMapStyle(value: MapStyle) = update { settingsRepository.setMapStyle(value) }
    fun setVoiceInterval(metres: Double) = update { settingsRepository.setVoiceInterval(metres) }
    fun setWeeklyGoal(metres: Double) = update { settingsRepository.setWeeklyGoal(metres) }
    fun setSpeedAlertVibrate(value: Boolean) =
        update { settingsRepository.setSpeedAlertVibrate(value) }

    fun setWaypointAlerts(value: Boolean) = update { settingsRepository.setWaypointAlerts(value) }

    /**
     * Writes every trip into Downloads as its own GPX file.
     *
     * The JSON backup is for restoring this app; this is for handing a season
     * of riding to something else — Strava, Komoot, a spreadsheet — which all
     * speak GPX and none of which speak our backup format.
     */
    fun exportAllGpx(successTemplate: String, failure: String) = viewModelScope.launch {
        _busy.value = true
        val repository = getApplication<Application>().app.tripRepository
        val exporter = getApplication<Application>().app.tripExporter
        var written = 0
        runCatching {
            repository.trips.first().forEach { trip ->
                exporter.saveToDownloads(trip, repository.getPoints(trip.id))
                    .onSuccess { written++ }
            }
        }.onFailure { _messages.emit(failure) }
        if (written > 0) _messages.emit(successTemplate.format(written))
        _busy.value = false
    }

    /** Writes the history into Downloads as one spreadsheet. */
    fun exportCsv(
        headings: CsvHeadings,
        activityName: (com.lasse.speedometer.data.db.TripEntity) -> String,
        successTemplate: String,
        failure: String,
    ) = viewModelScope.launch {
        _busy.value = true
        val application = getApplication<Application>()
        val trips = application.app.tripRepository.trips.first()
        application.app.tripExporter
            .saveCsvToDownloads(trips, settingsRepository.settings.first().units, headings) {
                activityName(it)
            }
            .onSuccess { _messages.emit(successTemplate.format(it)) }
            .onFailure { _messages.emit(failure) }
        _busy.value = false
    }

    /** Writes every trip, tour, route and waypoint into Downloads. */
    fun exportBackup(successTemplate: String, failure: String) = viewModelScope.launch {
        _busy.value = true
        backups.export()
            .onSuccess { _messages.emit(successTemplate.format(it)) }
            .onFailure { _messages.emit(failure) }
        _busy.value = false
    }

    /**
     * The outcome of the last restore, for the screen to put into words —
     * "one trip" and "two trips" are a plural, and plurals are resolved in the
     * composition, not here.
     */
    private val _lastRestore = MutableStateFlow<RestoreResult?>(null)
    val lastRestore: StateFlow<RestoreResult?> = _lastRestore.asStateFlow()

    fun restoreBackup(uri: Uri, failure: String) = viewModelScope.launch {
        _busy.value = true
        backups.restore(uri)
            .onSuccess { _lastRestore.value = it }
            .onFailure { _messages.emit(failure) }
        _busy.value = false
    }

    fun consumeRestoreResult() {
        _lastRestore.value = null
    }

    fun setMinimapSize(value: MinimapSize) = update { settingsRepository.setMinimapSize(value) }
    fun setShowTimer(value: Boolean) = update { settingsRepository.setShowTimer(value) }
    fun setShowStatusChip(value: Boolean) = update { settingsRepository.setShowStatusChip(value) }
    fun setStatColumns(value: Int) = update { settingsRepository.setStatColumns(value) }
    fun setHudMirror(value: Boolean) = update { settingsRepository.setHudMirror(value) }
    fun resetLayout() = update { settingsRepository.resetLayout() }

    fun toggleStat(stat: StatType, current: List<StatType>) =
        update { settingsRepository.toggleStat(stat, current) }

    fun moveStat(stat: StatType, offset: Int, current: List<StatType>) =
        update { settingsRepository.moveStat(stat, offset, current) }

    private fun update(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
