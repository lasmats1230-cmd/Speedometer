package com.lasse.speedometer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.BuildConfig
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.ActivityType
import com.lasse.speedometer.data.io.CsvHeadings
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.data.prefs.MapStyle
import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.data.prefs.ThemeMode
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.tracking.VoiceCoach
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.ScreenTitle
import com.lasse.speedometer.ui.components.SectionCard
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.ui.theme.AccentColor
import com.lasse.speedometer.util.AppLanguage
import com.lasse.speedometer.util.Formatters
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onOpenLayout: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val healthGranted by viewModel.healthPermissionsGranted.collectAsState()
    val language by viewModel.language.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // A restore reports in words the composition can pluralise.
    val lastRestore by viewModel.lastRestore.collectAsState()
    val restoreMessage = lastRestore?.let { result ->
        pluralStringResource(
            R.plurals.settings_backup_restored,
            result.trips,
            result.trips,
            result.skipped,
        )
    }
    LaunchedEffect(restoreMessage) {
        val message = restoreMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeRestoreResult()
    }

    val backupSaved = stringResource(R.string.settings_backup_saved)
    val backupFailed = stringResource(R.string.settings_backup_failed)
    val exportedAll = stringResource(R.string.settings_exported_all)
    val exportedCsv = stringResource(R.string.settings_exported_csv)

    // The spreadsheet's headings and activity names are interface text, so
    // they are resolved here and handed down rather than looked up in a
    // writer that has no business knowing about resources.
    val csvHeadings = CsvHeadings(
        date = stringResource(R.string.detail_started),
        title = stringResource(R.string.trip_name),
        activity = stringResource(R.string.activity),
        distance = stringResource(R.string.stat_distance),
        duration = stringResource(R.string.stat_time),
        movingTime = stringResource(R.string.moving_time),
        avgSpeed = stringResource(R.string.stat_avg),
        maxSpeed = stringResource(R.string.stat_max),
        ascent = stringResource(R.string.ascent),
        descent = stringResource(R.string.descent),
        note = stringResource(R.string.note),
    )
    val activityNames = ActivityType.entries.associateWith { stringResource(it.labelRes) }

    val restoreFailed = stringResource(R.string.settings_restore_failed)

    // Backups are JSON, but file pickers disagree about what that means often
    // enough that anything is accepted and the parser decides.
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreBackup(uri, restoreFailed)
    }

    val healthLauncher = rememberLauncherForActivityResult(
        viewModel.healthPermissionContract()
    ) { viewModel.refreshHealthPermissions() }

    val healthUnavailable = stringResource(R.string.health_unavailable)
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccentPicker = !settings.dynamicColor || !dynamicSupported

    Column(Modifier.fillMaxSize()) {
    ScreenTitle(title = stringResource(R.string.settings))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.Screen,
            end = Dimens.Screen,
            bottom = Dimens.BottomGap,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("layout") {
            SettingsSection(stringResource(R.string.settings_layout)) {
                NavigationRow(
                    title = stringResource(R.string.settings_layout_row),
                    subtitle = stringResource(R.string.settings_layout_summary),
                    onClick = onOpenLayout,
                )
            }
        }

        item("units") {
            SettingsSection(stringResource(R.string.settings_units)) {
                Text(
                    text = stringResource(R.string.settings_unit_system),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SegmentedTabs(
                    options = listOf("km/h", "mph"),
                    selectedIndex = if (settings.units == UnitSystem.METRIC) 0 else 1,
                    onSelect = {
                        viewModel.setUnits(if (it == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL)
                    },
                )
            }
        }

        item("language") {
            SettingsSection(stringResource(R.string.settings_language)) {
                NavigationRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(language.labelRes),
                    onClick = { showLanguagePicker = true },
                )
            }
        }

        item("appearance") {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.theme_system),
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_dark),
                    ),
                    selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                    onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                )

                if (dynamicSupported) {
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }

                if (showAccentPicker) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_accent),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(
                            if (dynamicSupported) {
                                R.string.settings_accent_summary
                            } else {
                                R.string.settings_accent_summary_legacy
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    AccentPicker(
                        selected = settings.accentColor,
                        onSelect = viewModel::setAccentColor,
                    )
                }

                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_pure_black),
                    subtitle = stringResource(R.string.settings_pure_black_summary),
                    checked = settings.pureBlack,
                    onCheckedChange = viewModel::setPureBlack,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    checked = settings.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }
        }

        item("battery") {
            SettingsSection(stringResource(R.string.settings_battery)) {
                BatterySaverMode.entries.forEach { mode ->
                    RadioRow(
                        title = stringResource(mode.labelRes),
                        subtitle = stringResource(mode.summaryRes),
                        selected = mode == settings.batterySaver,
                        onClick = { viewModel.setBatterySaver(mode) },
                    )
                }

                if (settings.batterySaver == BatterySaverMode.CYCLING) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_dim_delay,
                            settings.dimDelaySeconds,
                            settings.dimDelaySeconds,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    var delay by remember(settings.dimDelaySeconds) {
                        mutableFloatStateOf(settings.dimDelaySeconds.toFloat())
                    }
                    Slider(
                        value = delay,
                        onValueChange = { delay = it },
                        onValueChangeFinished = {
                            viewModel.setDimDelaySeconds(delay.roundToInt())
                        },
                        valueRange = 3f..60f,
                        steps = 18,
                    )
                }
            }
        }

        item("recording") {
            SettingsSection(stringResource(R.string.settings_recording)) {
                // Aggressive battery optimisation is the single most common
                // reason a long recording stops early, and the fix is two taps
                // away in a screen nobody knows exists.
                val powerManager = LocalContext.current
                    .getSystemService(android.os.PowerManager::class.java)
                val unrestricted = powerManager
                    ?.isIgnoringBatteryOptimizations(LocalContext.current.packageName) == true
                val batterySettingsContext = LocalContext.current
                NavigationRow(
                    title = stringResource(R.string.settings_background),
                    subtitle = stringResource(
                        if (unrestricted) {
                            R.string.settings_background_unrestricted
                        } else {
                            R.string.settings_background_restricted
                        }
                    ),
                    onClick = {
                        runCatching {
                            batterySettingsContext.startActivity(
                                android.content.Intent(
                                    android.provider.Settings
                                        .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                )
                            )
                        }
                    },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_pause),
                    subtitle = stringResource(R.string.settings_auto_pause_summary),
                    checked = settings.autoPause,
                    onCheckedChange = viewModel::setAutoPause,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_min_accuracy) + "  " +
                        Formatters.elevation(settings.minAccuracyM.toDouble(), settings.units),
                    style = MaterialTheme.typography.bodyLarge,
                )
                var accuracy by remember(settings.minAccuracyM) {
                    mutableFloatStateOf(settings.minAccuracyM)
                }
                Slider(
                    value = accuracy,
                    onValueChange = { accuracy = it },
                    onValueChangeFinished = {
                        viewModel.setMinAccuracy(accuracy.roundToInt().toFloat())
                    },
                    valueRange = 10f..150f,
                    steps = 13,
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_speed_source),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.speed_source_gps),
                        stringResource(R.string.speed_source_computed),
                    ),
                    selectedIndex = if (settings.speedSource == SpeedSource.GNSS) 0 else 1,
                    onSelect = {
                        viewModel.setSpeedSource(
                            if (it == 0) SpeedSource.GNSS else SpeedSource.COMPUTED
                        )
                    },
                )
            }
        }

        item("map") {
            SettingsSection(stringResource(R.string.settings_map)) {
                Text(
                    text = stringResource(R.string.settings_map_style),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                MapStyle.entries.forEach { style ->
                    RadioRow(
                        title = stringResource(style.labelRes),
                        selected = style == settings.mapStyle,
                        onClick = { viewModel.setMapStyle(style) },
                    )
                }
            }
        }

        item("voice") {
            SettingsSection(stringResource(R.string.settings_voice)) {
                Text(
                    text = stringResource(R.string.settings_voice_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // Whole units only: "every 1.6 km" is not a milestone anyone
                // is waiting to hear.
                val unitM = if (settings.units == UnitSystem.METRIC) 1000.0 else 1609.344
                val choices = listOf(0.0, unitM, 2 * unitM, 5 * unitM)
                val labels = listOf(stringResource(R.string.voice_off)) +
                    listOf(1, 2, 5).map { multiple ->
                        Formatters.distanceUnitCount(multiple, settings.units)
                    }
                SegmentedTabs(
                    options = labels,
                    selectedIndex = choices
                        .indexOfFirst { kotlin.math.abs(it - settings.voiceIntervalM) < 1.0 }
                        .coerceAtLeast(0),
                    onSelect = { viewModel.setVoiceInterval(choices[it]) },
                )

                if (settings.voiceIntervalM > 0) {
                    Spacer(Modifier.height(12.dp))
                    // Whether a device can actually speak depends on engines
                    // and downloaded voices, neither of which this app
                    // controls — so let the user hear it rather than find out
                    // ten kilometres into a ride.
                    val voiceContext = LocalContext.current
                    val coach = remember { VoiceCoach(voiceContext) }
                    DisposableEffect(coach) { onDispose { coach.shutdown() } }
                    val sample = stringResource(
                        R.string.voice_update,
                        Formatters.distance(
                            if (settings.units == UnitSystem.METRIC) 5_000.0 else 8_046.72,
                            settings.units,
                        ),
                        coach.spokenDuration(18 * 60 * 1000L),
                        Formatters.speed(6.4, settings.units),
                    )
                    OutlinedButton(onClick = { coach.say(sample) }) {
                        Text(stringResource(R.string.voice_test))
                    }
                }
            }
        }

        item("goal") {
            SettingsSection(stringResource(R.string.settings_goal)) {
                Text(
                    text = stringResource(R.string.settings_goal_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = if (settings.weeklyGoalM <= 0) {
                        stringResource(R.string.goal_none)
                    } else {
                        Formatters.distance(settings.weeklyGoalM, settings.units)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                var goal by remember(settings.weeklyGoalM) {
                    mutableFloatStateOf(
                        Formatters.distanceIn(settings.weeklyGoalM, settings.units).toFloat()
                    )
                }
                Slider(
                    value = goal.coerceIn(0f, 500f),
                    onValueChange = { goal = it },
                    onValueChangeFinished = {
                        // Rounded to fives: nobody aims for 63 kilometres.
                        val rounded = (goal / 5f).roundToInt() * 5.0
                        val metres = when (settings.units) {
                            UnitSystem.METRIC -> rounded * 1000.0
                            UnitSystem.IMPERIAL -> rounded * 1609.344
                        }
                        viewModel.setWeeklyGoal(metres)
                    },
                    valueRange = 0f..500f,
                )
            }
        }

        item("alerts") {
            SettingsSection(stringResource(R.string.settings_alerts)) {
                val alertOn = settings.speedAlertMps > 0f
                SwitchRow(
                    title = stringResource(R.string.settings_speed_alert),
                    subtitle = stringResource(R.string.settings_speed_alert_summary),
                    checked = alertOn,
                    onCheckedChange = { enabled ->
                        viewModel.setSpeedAlert(if (enabled) DEFAULT_ALERT_MPS else 0f)
                    },
                )

                if (alertOn) {
                    Spacer(Modifier.height(8.dp))
                    // The slider works in whatever the user reads speeds in;
                    // what is stored stays metres per second either way.
                    val displayed = Formatters.speedIn(
                        settings.speedAlertMps.toDouble(),
                        settings.units,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_speed_alert_at,
                            Formatters.speed(settings.speedAlertMps.toDouble(), settings.units),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    var limit by remember(settings.speedAlertMps) {
                        mutableFloatStateOf(displayed.toFloat())
                    }
                    val range = if (settings.units == UnitSystem.METRIC) 10f..200f else 5f..125f
                    Slider(
                        value = limit.coerceIn(range),
                        onValueChange = { limit = it },
                        onValueChangeFinished = {
                            val rounded = limit.roundToInt().toDouble()
                            val mps = when (settings.units) {
                                UnitSystem.METRIC -> rounded / 3.6
                                UnitSystem.IMPERIAL -> rounded / 2.2369362920544
                            }
                            viewModel.setSpeedAlert(mps.toFloat())
                        },
                        valueRange = range,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_speed_alert_vibrate),
                        checked = settings.speedAlertVibrate,
                        onCheckedChange = viewModel::setSpeedAlertVibrate,
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.settings_waypoint_alerts),
                    subtitle = stringResource(R.string.settings_waypoint_alerts_summary),
                    checked = settings.waypointAlerts,
                    onCheckedChange = viewModel::setWaypointAlerts,
                )
            }
        }

        item("data") {
            SettingsSection(stringResource(R.string.settings_data)) {
                Text(
                    text = stringResource(R.string.settings_backup_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.exportBackup(backupSaved, backupFailed)
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_backup_export)) }
                    OutlinedButton(
                        onClick = { backupPicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.settings_backup_restore)) }
                }
                Spacer(Modifier.height(8.dp))
                NavigationRow(
                    title = stringResource(R.string.settings_export_all_gpx),
                    subtitle = stringResource(R.string.settings_export_all_gpx_summary),
                    onClick = { viewModel.exportAllGpx(exportedAll, backupFailed) },
                )
                NavigationRow(
                    title = stringResource(R.string.settings_export_csv),
                    subtitle = stringResource(R.string.settings_export_csv_summary),
                    onClick = {
                        viewModel.exportCsv(
                            headings = csvHeadings,
                            activityName = { activityNames[it.activityType] ?: it.activity },
                            successTemplate = exportedCsv,
                            failure = backupFailed,
                        )
                    },
                )
            }
        }

        item("health") {
            SettingsSection(stringResource(R.string.settings_health)) {
                if (!viewModel.healthAvailable) {
                    Text(
                        text = healthUnavailable,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    NavigationRow(
                        title = stringResource(R.string.settings_health_connect),
                        subtitle = stringResource(
                            if (healthGranted) R.string.connected else R.string.not_connected
                        ),
                        onClick = { healthLauncher.launch(viewModel.healthPermissions) },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_health_auto),
                        checked = settings.autoSyncHealth,
                        onCheckedChange = viewModel::setAutoSyncHealth,
                    )
                }
            }
        }

        item("about") {
            SettingsSection(stringResource(R.string.settings_about)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.made_by),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                NavigationRow(
                    title = stringResource(R.string.settings_licenses_title),
                    subtitle = stringResource(R.string.settings_licenses_summary),
                    onClick = onOpenLicenses,
                )
                Text(
                    text = stringResource(R.string.settings_osm_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

    }
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { option ->
                        RadioRow(
                            title = stringResource(option.labelRes),
                            selected = option == language,
                            onClick = {
                                showLanguagePicker = false
                                // Android 13 and up restarts us itself.
                                if (viewModel.setLanguage(option)) activity?.recreate()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Unwraps the activity from whatever context wrappers Compose hands over. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.seed)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(accent.labelRes),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * A titled settings group. Thin wrapper over the app-wide [SectionCard] so
 * settings, statistics and trip details all use the same card.
 */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title = title, content = content)
}

/** 50 km/h — the usual urban limit, and a sane place for the slider to start. */
private const val DEFAULT_ALERT_MPS = 13.9f

@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavigationRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
