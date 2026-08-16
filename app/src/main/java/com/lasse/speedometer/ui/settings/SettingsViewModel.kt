package com.lasse.speedometer.ui.settings

import android.app.Application
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.data.prefs.ThemeMode
import com.lasse.speedometer.data.prefs.UnitSystem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = application.app.settingsRepository
    private val health = application.app.healthConnectManager

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted: StateFlow<Boolean> = _healthPermissionsGranted.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

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

    fun setUnits(value: UnitSystem) = viewModelScope.launch {
        settingsRepository.setUnits(value)
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(value)
    }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(value)
    }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch {
        settingsRepository.setKeepScreenOn(value)
    }

    fun setAutoPause(value: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoPause(value)
    }

    fun setMinAccuracy(value: Float) = viewModelScope.launch {
        settingsRepository.setMinAccuracy(value)
    }

    fun setSpeedSource(value: SpeedSource) = viewModelScope.launch {
        settingsRepository.setSpeedSource(value)
    }

    fun setAutoSyncHealth(value: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoSyncHealth(value)
    }
}
