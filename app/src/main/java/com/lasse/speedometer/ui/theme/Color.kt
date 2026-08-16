package com.lasse.speedometer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fallback palette used when the device cannot supply Material You colours
 * (pre Android 12) or when the user turns dynamic colour off.
 */
object Palette {
    val PrimaryLight = Color(0xFF006D48)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFF64FCBA)
    val OnPrimaryContainerLight = Color(0xFF002114)
    val SecondaryLight = Color(0xFF4C6358)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFCEE9DA)
    val OnSecondaryContainerLight = Color(0xFF082017)
    val TertiaryLight = Color(0xFF3D6373)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFC0E9FB)
    val OnTertiaryContainerLight = Color(0xFF001F29)
    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF410002)
    val BackgroundLight = Color(0xFFFBFDF8)
    val OnBackgroundLight = Color(0xFF191C1A)
    val SurfaceLight = Color(0xFFFBFDF8)
    val OnSurfaceLight = Color(0xFF191C1A)
    val SurfaceVariantLight = Color(0xFFDCE5DD)
    val OnSurfaceVariantLight = Color(0xFF404943)
    val OutlineLight = Color(0xFF707973)
    val SurfaceContainerLight = Color(0xFFEFF1EC)
    val SurfaceContainerHighLight = Color(0xFFE9EBE6)

    val PrimaryDark = Color(0xFF00E38C)
    val OnPrimaryDark = Color(0xFF003824)
    val PrimaryContainerDark = Color(0xFF005235)
    val OnPrimaryContainerDark = Color(0xFF64FCBA)
    val SecondaryDark = Color(0xFFB2CCBF)
    val OnSecondaryDark = Color(0xFF1E352B)
    val SecondaryContainerDark = Color(0xFF344B41)
    val OnSecondaryContainerDark = Color(0xFFCEE9DA)
    val TertiaryDark = Color(0xFFA5CDDF)
    val OnTertiaryDark = Color(0xFF063544)
    val TertiaryContainerDark = Color(0xFF244C5B)
    val OnTertiaryContainerDark = Color(0xFFC0E9FB)
    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)
    val BackgroundDark = Color(0xFF0D0F0E)
    val OnBackgroundDark = Color(0xFFE1E3DF)
    val SurfaceDark = Color(0xFF0D0F0E)
    val OnSurfaceDark = Color(0xFFE1E3DF)
    val SurfaceVariantDark = Color(0xFF404943)
    val OnSurfaceVariantDark = Color(0xFFBFC9C2)
    val OutlineDark = Color(0xFF8A938C)
    val SurfaceContainerDark = Color(0xFF1B1F1D)
    val SurfaceContainerHighDark = Color(0xFF262B28)
}

/** Colours that stay constant so a recorded track always reads the same. */
object TrackColors {
    val Track = Color(0xFF19E68C)
    val TrackDim = Color(0xFF0E7F4E)
    val Recording = Color(0xFFFF4438)
    val Paused = Color(0xFFFFB020)
}
