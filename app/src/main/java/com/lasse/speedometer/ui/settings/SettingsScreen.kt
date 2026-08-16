package com.lasse.speedometer.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.BuildConfig
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.SpeedSource
import com.lasse.speedometer.data.prefs.ThemeMode
import com.lasse.speedometer.data.prefs.UnitSystem
import com.lasse.speedometer.ui.components.SegmentedTabs
import com.lasse.speedometer.util.Formatters
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val healthGranted by viewModel.healthPermissionsGranted.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val healthLauncher = rememberLauncherForActivityResult(
        viewModel.healthPermissionContract()
    ) { viewModel.refreshHealthPermissions() }

    val healthUnavailable = stringResource(R.string.health_unavailable)

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
                        viewModel.setUnits(
                            if (it == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL
                        )
                    },
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
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                    onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    checked = settings.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
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
                var sliderValue by remember(settings.minAccuracyM) {
                    mutableStateOf(settings.minAccuracyM)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.setMinAccuracy(sliderValue.roundToInt().toFloat())
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
                    options = listOf("GPS", "Computed"),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { healthLauncher.launch(viewModel.healthPermissions) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_health_connect),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (healthGranted) "Connected" else "Not connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_licenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item("spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun SwitchRow(
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
