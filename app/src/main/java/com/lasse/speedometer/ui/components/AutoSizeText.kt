package com.lasse.speedometer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that shrinks until it fits on one line.
 *
 * Stat tiles sit in equal-width columns, so a longer value like "0,0 km/h"
 * would otherwise wrap and make its tile taller than its neighbours. Shrinking
 * the few offending characters keeps every tile the same height without
 * truncating anything.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 9.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    val startSize = style.fontSize.value
    val floor = minFontSize.value

    // Keyed on the text so a new value starts from the full size again rather
    // than inheriting a shrink the previous one needed.
    var fontSize by remember(text, startSize) { mutableFloatStateOf(startSize) }
    var settled by remember(text, startSize) { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize.sp),
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        // Drawing only once the size has settled avoids a visible reflow as
        // the measurement converges.
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > floor) {
                fontSize = (fontSize * SHRINK_STEP).coerceAtLeast(floor)
            } else {
                settled = true
            }
        },
    )
}

private const val SHRINK_STEP = 0.92f
