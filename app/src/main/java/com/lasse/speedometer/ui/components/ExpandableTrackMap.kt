package com.lasse.speedometer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lasse.speedometer.R

/**
 * A finished track on a map, with a button that opens it full screen.
 *
 * A ride drawn into a card two hundred dip tall is a shape, not a route — the
 * streets it went down are there but too small to read. The card stays the
 * size it was, because the rest of the page is worth seeing too, and the
 * whole screen is one tap away when the route itself is the question.
 */
@Composable
fun ExpandableTrackMap(
    modifier: Modifier = Modifier,
    /** A single ride. */
    track: List<LatLng> = emptyList(),
    /** Several rides, for a tour: each is drawn as its own line. */
    tracks: List<List<LatLng>> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    // Nothing to enlarge for a trip that never got two fixes.
    val drawable = track.size >= 2 || tracks.any { it.size >= 2 }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box {
            TrackMap(
                modifier = Modifier.fillMaxSize(),
                track = track,
                tracks = tracks,
                fitTrack = true,
                followPosition = false,
            )
            if (drawable) {
                FilledTonalIconButton(
                    onClick = { expanded = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = stringResource(R.string.map_expand),
                    )
                }
            }
        }
    }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            // The point of the thing is the whole screen; the default dialog
            // width would hand back a card barely bigger than the one tapped.
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.fillMaxSize()) {
                    TrackMap(
                        modifier = Modifier.fillMaxSize(),
                        track = track,
                        tracks = tracks,
                        fitTrack = true,
                        followPosition = false,
                    )
                    FilledTonalIconButton(
                        onClick = { expanded = false },
                        // The map runs under the status bar; the button that
                        // closes it must not.
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .safeDrawingPadding()
                            .padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
            }
        }
    }
}
