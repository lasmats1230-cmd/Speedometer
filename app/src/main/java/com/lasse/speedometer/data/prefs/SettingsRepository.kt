package com.lasse.speedometer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class UnitSystem { METRIC, IMPERIAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the speed number comes from. */
enum class SpeedSource {
    /** The Doppler-derived speed the GNSS chip reports — smoothest at speed. */
    GNSS,

    /** Distance between fixes over elapsed time — steadier when standing still. */
    COMPUTED,
}

data class AppSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoPause: Boolean = false,
    val minAccuracyM: Float = 50f,
    val speedSource: SpeedSource = SpeedSource.GNSS,
    val autoSyncHealth: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val MIN_ACCURACY = floatPreferencesKey("min_accuracy")
        val SPEED_SOURCE = stringPreferencesKey("speed_source")
        val AUTO_SYNC_HEALTH = booleanPreferencesKey("auto_sync_health")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            units = prefs[Keys.UNITS]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: UnitSystem.METRIC,
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            autoPause = prefs[Keys.AUTO_PAUSE] ?: false,
            minAccuracyM = prefs[Keys.MIN_ACCURACY] ?: 50f,
            speedSource = prefs[Keys.SPEED_SOURCE]
                ?.let { runCatching { SpeedSource.valueOf(it) }.getOrNull() }
                ?: SpeedSource.GNSS,
            autoSyncHealth = prefs[Keys.AUTO_SYNC_HEALTH] ?: false,
        )
    }

    suspend fun setUnits(value: UnitSystem) = edit { it[Keys.UNITS] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setAutoPause(value: Boolean) = edit { it[Keys.AUTO_PAUSE] = value }
    suspend fun setMinAccuracy(value: Float) = edit { it[Keys.MIN_ACCURACY] = value }
    suspend fun setSpeedSource(value: SpeedSource) = edit { it[Keys.SPEED_SOURCE] = value.name }
    suspend fun setAutoSyncHealth(value: Boolean) = edit { it[Keys.AUTO_SYNC_HEALTH] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
