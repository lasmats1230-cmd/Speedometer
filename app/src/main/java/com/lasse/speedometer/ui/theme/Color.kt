package com.lasse.speedometer.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.lasse.speedometer.R

/**
 * Error colours, shared by every scheme.
 *
 * The accent families are derived from a seed in [AccentSchemes]; error is
 * deliberately not, because a warning should read as a warning whatever the
 * user picked for their accent.
 */
object Palette {
    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)
}

/** Colours that stay constant so a recorded track always reads the same. */
object TrackColors {
    val Track = Color(0xFF19E68C)
    val TrackDim = Color(0xFF0E7F4E)
    val Recording = Color(0xFFFF4438)
    val Paused = Color(0xFFFFB020)
}

/**
 * The palette offered when colouring a waypoint.
 *
 * Each colour carries its name, because a row of nine circles is nine
 * unlabelled buttons to anyone using a screen reader.
 */
object WaypointColors {
    val options = listOf(
        WaypointColor(Color(0xFFE0402F), R.string.colour_red),
        WaypointColor(Color(0xFFF07316), R.string.colour_orange),
        WaypointColor(Color(0xFFD5A400), R.string.colour_yellow),
        WaypointColor(Color(0xFF19E68C), R.string.colour_green),
        WaypointColor(Color(0xFF009FA8), R.string.colour_teal),
        WaypointColor(Color(0xFF2F7BFF), R.string.colour_blue),
        WaypointColor(Color(0xFF7C5CFF), R.string.colour_purple),
        WaypointColor(Color(0xFFE0459B), R.string.colour_pink),
        WaypointColor(Color(0xFFB0B7B3), R.string.colour_grey),
    )

    val default = options[5].color
}

/** One choice in the waypoint palette. */
data class WaypointColor(val color: Color, @param:StringRes val nameRes: Int)
