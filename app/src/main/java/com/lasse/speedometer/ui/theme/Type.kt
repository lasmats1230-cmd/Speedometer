package com.lasse.speedometer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SpeedometerTypography = Typography()

val SpeedometerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** The oversized readout on the live view. */
val SpeedDisplayStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 120.sp,
    lineHeight = 124.sp,
    letterSpacing = (-4).sp,
    textAlign = TextAlign.Center,
)

val SpeedUnitStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    textAlign = TextAlign.Center,
)

val TimerStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 40.sp,
    lineHeight = 46.sp,
    letterSpacing = 0.sp,
    textAlign = TextAlign.Center,
)
