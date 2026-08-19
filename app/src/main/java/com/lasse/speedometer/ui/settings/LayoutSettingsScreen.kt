package com.lasse.speedometer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lasse.speedometer.R
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.MinimapSize
import com.lasse.speedometer.data.prefs.StatType
import com.lasse.speedometer.ui.components.DetailScaffold
import com.lasse.speedometer.ui.components.Dimens
import com.lasse.speedometer.ui.components.SegmentedTabs

/**
 * Rearranges the live view: how much map, which figures, and in what order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val layout = settings.layout

    DetailScaffold(
        title = stringResource(R.string.settings_layout),
        onBack = onBack,
        actions = {
            TextButton(onClick = viewModel::resetLayout) {
                Text(stringResource(R.string.reset))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.Screen,
                end = Dimens.Screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.BottomGap,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("preview") {
                Column {
                    Text(
                        text = stringResource(R.string.layout_preview),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    )
                    LayoutPreview(
                        settings = settings,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                }
            }

            item("minimap") {
                SettingsSection(stringResource(R.string.layout_minimap)) {
                    Text(
                        text = stringResource(R.string.layout_minimap_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    SegmentedTabs(
                        options = MinimapSize.entries.map { stringResource(it.labelRes) },
                        selectedIndex = MinimapSize.entries.indexOf(layout.minimapSize),
                        onSelect = { viewModel.setMinimapSize(MinimapSize.entries[it]) },
                    )
                }
            }

            item("columns") {
                SettingsSection(stringResource(R.string.layout_columns)) {
                    Text(
                        text = stringResource(R.string.layout_columns_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    SegmentedTabs(
                        options = listOf("1", "2", "3", "4"),
                        selectedIndex = (layout.statColumns - 1).coerceIn(0, 3),
                        onSelect = { viewModel.setStatColumns(it + 1) },
                    )
                }
            }

            item("elements") {
                SettingsSection(stringResource(R.string.layout_elements)) {
                    SwitchRow(
                        title = stringResource(R.string.layout_show_status),
                        checked = layout.showStatusChip,
                        onCheckedChange = viewModel::setShowStatusChip,
                    )
                    SwitchRow(
                        title = stringResource(R.string.layout_hud),
                        subtitle = stringResource(R.string.layout_hud_summary),
                        checked = layout.hudMirror,
                        onCheckedChange = viewModel::setHudMirror,
                    )
                    SwitchRow(
                        title = stringResource(R.string.layout_show_timer),
                        checked = layout.showTimer,
                        onCheckedChange = viewModel::setShowTimer,
                    )
                }
            }

            item("chosen-header") {
                Text(
                    text = stringResource(R.string.layout_shown_stats),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                )
            }

            itemsIndexedStats(layout.stats) { index, stat ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(stat.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 16.dp),
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = { viewModel.moveStat(stat, -1, layout.stats) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.move_up),
                            )
                        }
                        IconButton(
                            enabled = index < layout.stats.lastIndex,
                            onClick = { viewModel.moveStat(stat, 1, layout.stats) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.move_down),
                            )
                        }
                        Checkbox(
                            checked = true,
                            onCheckedChange = { viewModel.toggleStat(stat, layout.stats) },
                        )
                    }
                }
            }

            val available = StatType.entries.filterNot { it in layout.stats }
            if (available.isNotEmpty()) {
                item("available-header") {
                    Text(
                        text = stringResource(R.string.layout_available_stats),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                itemsIndexedStats(available) { _, stat ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(stat.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 16.dp),
                            )
                            Spacer(Modifier.size(96.dp, 0.dp))
                            Checkbox(
                                checked = false,
                                onCheckedChange = { viewModel.toggleStat(stat, layout.stats) },
                            )
                        }
                    }
                }
            }

            item("footer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Small helper so both stat lists share one row template. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedStats(
    stats: List<StatType>,
    content: @Composable (Int, StatType) -> Unit,
) {
    stats.forEachIndexed { index, stat ->
        item("stat-${stat.name}") { content(index, stat) }
    }
}
