package com.kuyermqi.quotawidget.widget

import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext

private const val REFERENCE_TEXT_SIZE_PX = 100f
private const val LINE_HEIGHT_FACTOR = 1.2f
private const val WIDTH_SAFETY_FACTOR = 0.95f
private const val MULTI_LINE_WIDTH_SAFETY_FACTOR = 0.94f
private const val MULTI_LINE_FIT_STEP_SP = 1f
private const val CJK_SYMBOLS_START = 0x3000
private const val CJK_SYMBOLS_END = 0x303F
private const val HALFWIDTH_FULLWIDTH_START = 0xFF00
private const val HALFWIDTH_FULLWIDTH_END = 0xFFEF
private val CJK_SYMBOLS_AND_PUNCT_RANGE = CJK_SYMBOLS_START..CJK_SYMBOLS_END
private val HALFWIDTH_FULLWIDTH_RANGE = HALFWIDTH_FULLWIDTH_START..HALFWIDTH_FULLWIDTH_END

internal data class TextFitBounds(
    val maxWidth: Dp,
    val maxHeight: Dp,
    val minSp: Float,
    val maxSp: Float,
)

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
): TextUnit = fittedTextFontSize(
    text = text,
    maxWidth = maxWidth,
    maxHeight = maxHeight,
    minSp = minSp,
    maxSp = maxSp,
    bold = true,
)

@Composable
internal fun fittedTextFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    minSp: Float,
    maxSp: Float,
    bold: Boolean = true,
): TextUnit {
    val metrics = LocalContext.current.resources.displayMetrics
    val widthPerPx = remember(text, bold) { measureTextWidthPerPx(text, bold) }
    return fitMeasuredTextSize(
        metrics = metrics,
        widthPerPx = widthPerPx,
        bounds = TextFitBounds(maxWidth, maxHeight, minSp, maxSp),
    )
}

@Composable
internal fun fittedMultiLineTextSize(
    text: String,
    bounds: TextFitBounds,
    maxLines: Int,
    bold: Boolean = true,
): TextUnit {
    val metrics = LocalContext.current.resources.displayMetrics
    return remember(text, bounds, maxLines, bold, metrics.density, metrics.scaledDensity) {
        fitMultiLineTextSize(
            metrics = metrics,
            text = text,
            bounds = bounds,
            maxLines = maxLines,
            bold = bold,
        )
    }
}

internal fun fitMultiLineTextSize(
    metrics: DisplayMetrics,
    text: String,
    bounds: TextFitBounds,
    maxLines: Int,
    bold: Boolean = true,
): TextUnit {
    if (text.isBlank()) return bounds.minSp.sp
    val measureBox = MultiLineMeasureBox(
        widthPx = (bounds.maxWidth.value * metrics.density * MULTI_LINE_WIDTH_SAFETY_FACTOR)
            .toInt()
            .coerceAtLeast(1),
        heightPx = (bounds.maxHeight.value * metrics.density).toInt().coerceAtLeast(1),
        maxLines = maxLines,
    )
    val unbreakableTokens = extractUnbreakableTokens(text)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    var resultSp = bounds.minSp
    var candidateSp = bounds.maxSp.coerceAtLeast(bounds.minSp)
    while (candidateSp > bounds.minSp) {
        paint.textSize = candidateSp * metrics.scaledDensity
        if (fitsMultiLineCandidate(paint, text, unbreakableTokens, measureBox)) {
            resultSp = candidateSp
            break
        }
        candidateSp = (candidateSp - MULTI_LINE_FIT_STEP_SP).coerceAtLeast(bounds.minSp)
    }
    return resultSp.sp
}

private data class MultiLineMeasureBox(
    val widthPx: Int,
    val heightPx: Int,
    val maxLines: Int,
)

private fun fitsMultiLineCandidate(
    paint: TextPaint,
    text: String,
    unbreakableTokens: List<String>,
    box: MultiLineMeasureBox,
): Boolean {
    if (unbreakableTokens.any { paint.measureText(it) > box.widthPx }) return false
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, box.widthPx)
        .setMaxLines(Int.MAX_VALUE)
        .setIncludePad(true)
        .build()
    return layout.lineCount <= box.maxLines && layout.height <= box.heightPx
}

private fun extractUnbreakableTokens(text: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    for (ch in text) {
        if (isWrappableBoundaryChar(ch)) {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        } else {
            current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        tokens += current.toString()
    }
    return tokens
}

private fun isWrappableBoundaryChar(ch: Char): Boolean {
    if (ch.isWhitespace()) return true
    val code = ch.code
    val script = Character.UnicodeScript.of(code)
    return script == Character.UnicodeScript.HAN ||
        script == Character.UnicodeScript.HIRAGANA ||
        script == Character.UnicodeScript.KATAKANA ||
        script == Character.UnicodeScript.HANGUL ||
        code in CJK_SYMBOLS_AND_PUNCT_RANGE ||
        code in HALFWIDTH_FULLWIDTH_RANGE ||
        ch == '…'
}

internal fun measureBoldWidthPerPx(text: String): Float = measureTextWidthPerPx(text, bold = true)

internal fun measureTextWidthPerPx(text: String, bold: Boolean = true): Float {
    if (text.isBlank()) return 0f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textSize = REFERENCE_TEXT_SIZE_PX
    }
    return paint.measureText(text) / REFERENCE_TEXT_SIZE_PX
}

internal fun fitMeasuredTextSize(
    metrics: DisplayMetrics,
    widthPerPx: Float,
    bounds: TextFitBounds,
): TextUnit {
    if (widthPerPx <= 0f) return bounds.minSp.sp
    val widthPx = bounds.maxWidth.value * metrics.density * WIDTH_SAFETY_FACTOR
    val heightPx = bounds.maxHeight.value * metrics.density / LINE_HEIGHT_FACTOR
    val fittedPx = minOf(widthPx / widthPerPx, heightPx)
    val fittedSp = fittedPx / metrics.scaledDensity
    return fittedSp.coerceIn(bounds.minSp, bounds.maxSp).sp
}
