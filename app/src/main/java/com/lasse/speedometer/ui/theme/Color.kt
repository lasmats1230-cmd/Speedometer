package com.lasse.speedometer.ui.theme

import androidx.compose.ui.graphics.Color

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

/** The palette offered when colouring a waypoint. */
object WaypointColors {
    val options = listOf(
        Color(0xFFE0402F),
        Color(0xFFF07316),
        Color(0xFFD5A400),
        Color(0xFF19E68C),
        Color(0xFF009FA8),
        Color(0xFF2F7BFF),
        Color(0xFF7C5CFF),
        Color(0xFFE0459B),
        Color(0xFFB0B7B3),
    )

    val default = options[5]
}
