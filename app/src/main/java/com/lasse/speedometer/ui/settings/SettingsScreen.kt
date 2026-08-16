package com.lasse.speedometer.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.data.prefs.ThemeMode
import com.lasse.speedometer.data.prefs.UnitSystem
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

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val healthLauncher = rememberLauncherForActivityResult(
        viewModel.healthPermissionContract()
    ) { viewModel.refreshHealthPermissions() }

    val healthUnavailable = stringResource(R.string.health_unavailable)
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccentPicker = !settings.dynamicColor || !dynamicSupported

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("title") {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 12.dp),
            )
        }

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
                AppLanguage.entries.forEach { option ->
                    RadioRow(
                        title = stringResource(option.labelRes),
                        selected = option == language,
                        onClick = { viewModel.setLanguage(option) },
                    )
                }
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

        item("spacer") { Spacer(Modifier.height(24.dp)) }
    }
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

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
        }
    }
}

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
