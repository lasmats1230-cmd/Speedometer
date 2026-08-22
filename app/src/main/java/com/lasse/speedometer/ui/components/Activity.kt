package com.lasse.speedometer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.R
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

/**
 * The chosen activity as one chip that opens the full list.
 *
 * The live view has to fit the speed, the tiles and the map, and an activity
 * is picked once and then left alone for months. A chip says which one is set
 * from inside the line the status chip already occupies; the list itself is
 * one tap away for the rare occasion it changes.
 */
@Composable
fun ActivityChip(
    activity: ActivityType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(stringResource(activity.labelRes)) },
        leadingIcon = {
            Icon(
                imageVector = activity.icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** Choosing what a recording is, or re-filing one that has been saved. */
@Composable
fun ActivityDialog(
    selected: ActivityType,
    onSelect: (ActivityType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_activity)) },
        text = {
            Column {
                ActivityType.entries.forEach { activity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(activity) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = activity == selected, onClick = null)
                        Icon(
                            imageVector = activity.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Text(
                            text = stringResource(activity.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
