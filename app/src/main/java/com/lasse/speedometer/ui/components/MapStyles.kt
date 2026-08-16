package com.lasse.speedometer.ui.components

/**
 * Vector tile styles for the map.
 *
 * OpenFreeMap serves OpenStreetMap-derived vector tiles with no API key and
 * no sign-up, which keeps the app free of account setup. Both styles are
 * ordinary MapLibre style documents, so swapping in another provider — or a
 * style bundled in the APK — is a matter of changing these two URLs.
 */
object MapStyles {

    /** Muted dark basemap, matching the app's dark theme. */
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    /** Light, low-contrast basemap so the track stays the brightest thing. */
    const val LIGHT = "https://tiles.openfreemap.org/styles/positron"

    fun forTheme(darkTheme: Boolean) = if (darkTheme) DARK else LIGHT
}
