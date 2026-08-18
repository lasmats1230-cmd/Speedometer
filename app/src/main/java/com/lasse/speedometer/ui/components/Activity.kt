package com.lasse.speedometer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.data.db.ActivityType

/** The glyph an activity is drawn with, wherever it appears. */
val ActivityType.icon: ImageVector
    get() = when (this) {
        ActivityType.RIDE -> Icons.AutoMirrored.Filled.DirectionsBike
        ActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        ActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        ActivityType.HIKE -> Icons.Filled.Hiking
        ActivityType.DRIVE -> Icons.Filled.DirectionsCar
        ActivityType.OTHER -> Icons.Filled.Timeline
    }

/**
 * The activity picker: one chip per kind, scrolling sideways rather than
 * wrapping, so it stays a single line however narrow the phone.
 */
@Composable
fun ActivityPicker(
    selected: ActivityType,
    onSelect: (ActivityType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityType.entries.forEach { activity ->
            val isSelected = activity == selected
            FilterChip(
                selected = isSelected,
                enabled = enabled,
                onClick = { onSelect(activity) },
                label = { Text(stringResource(activity.labelRes)) },
                leadingIcon = {
                    Icon(
                        imageVector = activity.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** Icon and name of an activity, for a list row or a detail header. */
@Composable
fun ActivityBadge(
    activity: ActivityType,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = activity.icon,
            contentDescription = if (showLabel) null else stringResource(activity.labelRes),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        if (showLabel) {
            Text(
                text = stringResource(activity.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
