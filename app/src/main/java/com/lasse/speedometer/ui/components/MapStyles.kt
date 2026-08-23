package com.lasse.speedometer.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.lasse.speedometer.data.prefs.MapStyle

/**
 * Vector tile styles for the map.
 *
 * OpenFreeMap serves OpenStreetMap-derived vector tiles with no API key and
 * no sign-up, which keeps the app free of account setup. These are ordinary
 * MapLibre style documents, so swapping in another provider — or a style
 * bundled in the APK — is a matter of changing the URLs.
 */
object MapStyles {

    /** Muted dark basemap, matching the app's dark theme. */
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    /** Light, low-contrast basemap so the track stays the brightest thing. */
    const val POSITRON = "https://tiles.openfreemap.org/styles/positron"

    /** The full OpenStreetMap palette: green parks, blue water, named roads. */
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"

    /** Brighter and higher contrast than Liberty, for use in sunlight. */
    const val BRIGHT = "https://tiles.openfreemap.org/styles/bright"

    fun forTheme(darkTheme: Boolean) = if (darkTheme) DARK else POSITRON
}

/**
 * The basemap the user picked, reachable from any map without threading
 * settings through every screen that draws one.
 */
val LocalMapStyle = compositionLocalOf { MapStyle.AUTOMATIC }

/** The style document this choice resolves to, given the current theme. */
fun MapStyle.uri(darkTheme: Boolean): String = when (this) {
    MapStyle.AUTOMATIC -> MapStyles.forTheme(darkTheme)
    MapStyle.LIBERTY -> MapStyles.LIBERTY
    MapStyle.BRIGHT -> MapStyles.BRIGHT
    MapStyle.POSITRON -> MapStyles.POSITRON
    MapStyle.DARK -> MapStyles.DARK
}
