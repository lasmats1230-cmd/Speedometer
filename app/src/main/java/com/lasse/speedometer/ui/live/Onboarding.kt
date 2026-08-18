package com.lasse.speedometer.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lasse.speedometer.R

/**
 * What the app is, once, on first run.
 *
 * Three lines rather than a carousel: the only thing a first-time user has to
 * decide here is whether to grant location, and the two facts that make that
 * decision easy are that recording survives the screen going off and that
 * nothing leaves the phone.
 */
@Composable
fun OnboardingDialog(
    onGrantLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_intro),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Point(
                    icon = Icons.Filled.MyLocation,
                    text = stringResource(R.string.onboarding_location),
                )
                Point(
                    icon = Icons.Filled.BatteryFull,
                    text = stringResource(R.string.onboarding_background),
                )
                Point(
                    icon = Icons.Filled.Lock,
                    text = stringResource(R.string.onboarding_privacy),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGrantLocation) {
                Text(stringResource(R.string.onboarding_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_later)) }
        },
    )
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
