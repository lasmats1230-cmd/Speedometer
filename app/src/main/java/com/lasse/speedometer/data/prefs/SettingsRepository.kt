package com.lasse.speedometer.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.ui.theme.AccentColor
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

/**
 * Which basemap the maps draw.
 *
 * The URLs live in `ui/components/MapStyles.kt`; this enum only names the
 * choice, so the preference layer needs no opinion about tile providers.
 */
enum class MapStyle(@param:StringRes val labelRes: Int) {
    /** Light or dark to match the app's own theme. */
    AUTOMATIC(R.string.map_style_auto),
    LIBERTY(R.string.map_style_liberty),
    BRIGHT(R.string.map_style_bright),
    POSITRON(R.string.map_style_positron),
    DARK(R.string.map_style_dark),
}

/** How much of the live view the map is allowed to take. */
enum class MinimapSize(@param:StringRes val labelRes: Int) {
    HIDDEN(R.string.minimap_hidden),
    SMALL(R.string.minimap_small),
    MEDIUM(R.string.minimap_medium),
    LARGE(R.string.minimap_large),
}

/**
 * Every figure the live view can show in a tile.
 *
 * The order of this enum is only the order of the picker; what the live view
 * draws is [LayoutSettings.stats], which the user arranges.
 */
enum class StatType(@param:StringRes val labelRes: Int) {
    MAX_SPEED(R.string.stat_max),
    AVG_SPEED(R.string.stat_avg),
    DISTANCE(R.string.stat_distance),
    DURATION(R.string.stat_time),
    MOVING_TIME(R.string.moving_time),
    PACE(R.string.stat_pace),
    ALTITUDE(R.string.altitude),
    ASCENT(R.string.ascent),
    DESCENT(R.string.descent),
    ACCURACY(R.string.stat_accuracy),
    HEADING(R.string.heading),
    CLOCK(R.string.stat_clock),
}

/**
 * How hard to work at saving battery while recording.
 *
 * The screen and the map renderer cost far more than the GNSS receiver, so
 * every mode here trades away pixels rather than accuracy — a trip recorded
 * in extreme mode is the same trip.
 */
enum class BatterySaverMode(@param:StringRes val labelRes: Int, @param:StringRes val summaryRes: Int) {
    OFF(R.string.battery_off, R.string.battery_off_summary),
    CYCLING(R.string.battery_cycling, R.string.battery_cycling_summary),
    EXTREME(R.string.battery_extreme, R.string.battery_extreme_summary),
}

/** The user's arrangement of the live view. */
data class LayoutSettings(
    val minimapSize: MinimapSize = MinimapSize.LARGE,
    val stats: List<StatType> = listOf(StatType.MAX_SPEED, StatType.AVG_SPEED, StatType.DISTANCE),
    val showTimer: Boolean = true,
    val showStatusChip: Boolean = true,
    /** Tiles per row; fewer means larger, more readable numbers. */
    val statColumns: Int = 3,
)

data class AppSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    /** What the next recording will be filed as. */
    val activity: ActivityType = ActivityType.RIDE,
    /**
     * Speed above which the readout turns red, in metres per second. Zero is
     * off, which is also where it starts: an alert nobody asked for is noise.
     */
    val speedAlertMps: Float = 0f,
    val speedAlertVibrate: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: AccentColor = AccentColor.GREEN,
    val pureBlack: Boolean = false,
    val keepScreenOn: Boolean = true,
    val autoPause: Boolean = false,
    val minAccuracyM: Float = 50f,
    val speedSource: SpeedSource = SpeedSource.GNSS,
    val autoSyncHealth: Boolean = false,
    val layout: LayoutSettings = LayoutSettings(),
    val batterySaver: BatterySaverMode = BatterySaverMode.OFF,
    /** Seconds of stillness before cycling mode dims down. */
    val dimDelaySeconds: Int = 10,
    val mapStyle: MapStyle = MapStyle.AUTOMATIC,
    /** Metres between spoken updates; zero keeps the app quiet. */
    val voiceIntervalM: Double = 0.0,
    /** Metres per week the user is aiming for; zero is no goal. */
    val weeklyGoalM: Double = 0.0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val MIN_ACCURACY = floatPreferencesKey("min_accuracy")
        val SPEED_SOURCE = stringPreferencesKey("speed_source")
        val AUTO_SYNC_HEALTH = booleanPreferencesKey("auto_sync_health")
        val MINIMAP_SIZE = stringPreferencesKey("minimap_size")
        val STATS = stringPreferencesKey("stats")
        val SHOW_TIMER = booleanPreferencesKey("show_timer")
        val SHOW_STATUS_CHIP = booleanPreferencesKey("show_status_chip")
        val STAT_COLUMNS = intPreferencesKey("stat_columns")
        val BATTERY_SAVER = stringPreferencesKey("battery_saver")
        val DIM_DELAY = intPreferencesKey("dim_delay")
        val ACTIVITY = stringPreferencesKey("activity")
        val SPEED_ALERT = floatPreferencesKey("speed_alert_mps")
        val SPEED_ALERT_VIBRATE = booleanPreferencesKey("speed_alert_vibrate")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val VOICE_INTERVAL = floatPreferencesKey("voice_interval_m")
        val WEEKLY_GOAL = floatPreferencesKey("weekly_goal_m")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = LayoutSettings()
        AppSettings(
            units = prefs[Keys.UNITS].toEnum(UnitSystem.METRIC),
            activity = prefs[Keys.ACTIVITY].toEnum(ActivityType.RIDE),
            speedAlertMps = prefs[Keys.SPEED_ALERT] ?: 0f,
            speedAlertVibrate = prefs[Keys.SPEED_ALERT_VIBRATE] ?: true,
            themeMode = prefs[Keys.THEME].toEnum(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            accentColor = prefs[Keys.ACCENT_COLOR].toEnum(AccentColor.GREEN),
            pureBlack = prefs[Keys.PURE_BLACK] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            autoPause = prefs[Keys.AUTO_PAUSE] ?: false,
            minAccuracyM = prefs[Keys.MIN_ACCURACY] ?: 50f,
            speedSource = prefs[Keys.SPEED_SOURCE].toEnum(SpeedSource.GNSS),
            autoSyncHealth = prefs[Keys.AUTO_SYNC_HEALTH] ?: false,
            layout = LayoutSettings(
                minimapSize = prefs[Keys.MINIMAP_SIZE].toEnum(defaults.minimapSize),
                stats = prefs[Keys.STATS]?.let(::decodeStats) ?: defaults.stats,
                showTimer = prefs[Keys.SHOW_TIMER] ?: defaults.showTimer,
                showStatusChip = prefs[Keys.SHOW_STATUS_CHIP] ?: defaults.showStatusChip,
                statColumns = prefs[Keys.STAT_COLUMNS] ?: defaults.statColumns,
            ),
            batterySaver = prefs[Keys.BATTERY_SAVER].toEnum(BatterySaverMode.OFF),
            dimDelaySeconds = prefs[Keys.DIM_DELAY] ?: 10,
            mapStyle = prefs[Keys.MAP_STYLE].toEnum(MapStyle.AUTOMATIC),
            voiceIntervalM = (prefs[Keys.VOICE_INTERVAL] ?: 0f).toDouble(),
            weeklyGoalM = (prefs[Keys.WEEKLY_GOAL] ?: 0f).toDouble(),
        )
    }

    suspend fun setUnits(value: UnitSystem) = edit { it[Keys.UNITS] = value.name }
    suspend fun setActivity(value: ActivityType) = edit { it[Keys.ACTIVITY] = value.name }
    suspend fun setSpeedAlert(mps: Float) =
        edit { it[Keys.SPEED_ALERT] = mps.coerceAtLeast(0f) }

    suspend fun setSpeedAlertVibrate(value: Boolean) =
        edit { it[Keys.SPEED_ALERT_VIBRATE] = value }

    suspend fun setMapStyle(value: MapStyle) = edit { it[Keys.MAP_STYLE] = value.name }

    suspend fun setVoiceInterval(metres: Double) =
        edit { it[Keys.VOICE_INTERVAL] = metres.coerceAtLeast(0.0).toFloat() }

    suspend fun setWeeklyGoal(metres: Double) =
        edit { it[Keys.WEEKLY_GOAL] = metres.coerceAtLeast(0.0).toFloat() }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setAccentColor(value: AccentColor) = edit { it[Keys.ACCENT_COLOR] = value.name }
    suspend fun setPureBlack(value: Boolean) = edit { it[Keys.PURE_BLACK] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setAutoPause(value: Boolean) = edit { it[Keys.AUTO_PAUSE] = value }
    suspend fun setMinAccuracy(value: Float) = edit { it[Keys.MIN_ACCURACY] = value }
    suspend fun setSpeedSource(value: SpeedSource) = edit { it[Keys.SPEED_SOURCE] = value.name }
    suspend fun setAutoSyncHealth(value: Boolean) = edit { it[Keys.AUTO_SYNC_HEALTH] = value }

    suspend fun setMinimapSize(value: MinimapSize) = edit { it[Keys.MINIMAP_SIZE] = value.name }
    suspend fun setShowTimer(value: Boolean) = edit { it[Keys.SHOW_TIMER] = value }
    suspend fun setShowStatusChip(value: Boolean) = edit { it[Keys.SHOW_STATUS_CHIP] = value }
    suspend fun setStatColumns(value: Int) = edit { it[Keys.STAT_COLUMNS] = value.coerceIn(1, 4) }
    suspend fun setBatterySaver(value: BatterySaverMode) =
        edit { it[Keys.BATTERY_SAVER] = value.name }

    suspend fun setDimDelaySeconds(value: Int) =
        edit { it[Keys.DIM_DELAY] = value.coerceIn(3, 60) }

    suspend fun setStats(value: List<StatType>) = edit {
        it[Keys.STATS] = value.distinct().joinToString(",") { stat -> stat.name }
    }

    /** Adds or removes one tile, keeping at least one on screen. */
    suspend fun toggleStat(stat: StatType, current: List<StatType>) {
        val updated = if (stat in current) {
            current.filterNot { it == stat }.ifEmpty { current }
        } else {
            current + stat
        }
        setStats(updated)
    }

    /** Moves a tile one place earlier or later in the order. */
    suspend fun moveStat(stat: StatType, offset: Int, current: List<StatType>) {
        val index = current.indexOf(stat)
        if (index < 0) return
        val target = (index + offset).coerceIn(0, current.lastIndex)
        if (target == index) return
        val updated = current.toMutableList().apply {
            removeAt(index)
            add(target, stat)
        }
        setStats(updated)
    }

    suspend fun resetLayout() = edit {
        it.remove(Keys.MINIMAP_SIZE)
        it.remove(Keys.STATS)
        it.remove(Keys.SHOW_TIMER)
        it.remove(Keys.SHOW_STATUS_CHIP)
        it.remove(Keys.STAT_COLUMNS)
    }

    private fun decodeStats(raw: String): List<StatType> = raw
        .split(',')
        .mapNotNull { name -> runCatching { StatType.valueOf(name) }.getOrNull() }
        .distinct()
        .ifEmpty { LayoutSettings().stats }

    /**
     * Reads a stored enum name, falling back when the value is missing or no
     * longer exists — a preference written by an older build must not crash a
     * newer one. The default is a parameter rather than the expected type so
     * inference has something concrete to work from.
     */
    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private suspend fun edit(
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        context.dataStore.edit(block)
    }
}
