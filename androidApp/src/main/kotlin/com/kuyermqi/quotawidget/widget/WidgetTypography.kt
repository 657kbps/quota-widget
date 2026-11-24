package com.kuyermqi.quotawidget.widget

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext

private const val REFERENCE_TEXT_SIZE_PX = 100f
private const val LINE_HEIGHT_FACTOR = 1.2f
private const val WIDTH_SAFETY_FACTOR = 0.95f

/**
 * Largest bold single-line size that fits [text] in the given area. RemoteViews cannot
 * auto-size text, so the string is measured and the result stays within [minSp]..[maxSp].
 */
@Composable
internal fun fittedTitleFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    minSp: Float,
    maxSp: Float,
): TextUnit {
    val metrics = LocalContext.current.resources.displayMetrics
    val widthPerPx = remember(text) {
        if (text.isBlank()) {
            0f
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                textSize = REFERENCE_TEXT_SIZE_PX
            }
            paint.measureText(text) / REFERENCE_TEXT_SIZE_PX
        }
    }
    if (widthPerPx <= 0f) return minSp.sp

    val widthPx = maxWidth.value * metrics.density * WIDTH_SAFETY_FACTOR
    val heightPx = maxHeight.value * metrics.density / LINE_HEIGHT_FACTOR
    val fittedPx = minOf(widthPx / widthPerPx, heightPx)
    val fittedSp = fittedPx / metrics.scaledDensity
    return fittedSp.coerceIn(minSp, maxSp).sp
}
