package com.lasse.speedometer.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.R
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.ui.theme.WaypointColors

/** Shown after a long press on the map, and again when one is tapped. */
@Composable
fun WaypointEditorDialog(
    existing: WaypointEntity?,
    onDismiss: () -> Unit,
    onSave: (label: String, note: String, colorArgb: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var colorArgb by remember {
        mutableIntStateOf(existing?.colorArgb ?: WaypointColors.default.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.waypoint_new else R.string.waypoint_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.waypoint_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.waypoint_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.waypoint_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPicker(
                    selectedArgb = colorArgb,
                    onSelect = { colorArgb = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onSave(label.trim(), note.trim(), colorArgb) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row2(
                onDelete = onDelete,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun Row2(onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    Column {
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selectedArgb: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WaypointColors.options.forEach { option ->
            val argb = option.color.toArgb()
            val selected = argb == selectedArgb
            val name = stringResource(option.nameRes)
            Box(
                // The swatch stays 38dp, but the thing you tap is 48dp: a
                // circle small enough to look right is smaller than a fingertip.
                modifier = Modifier
                    .size(TOUCH_TARGET)
                    .clip(CircleShape)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(argb) },
                    )
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SWATCH)
                        .clip(CircleShape)
                        .background(option.color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Small enough to fit nine in a dialog, big enough to read as a colour. */
private val SWATCH = 38.dp

/** Material's smallest comfortable target, whatever the swatch looks like. */
private val TOUCH_TARGET = 48.dp
