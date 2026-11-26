package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.domain.UsageResetStyle

private const val MIN_RING_SIZE_DP = 84f
private const val BASE_RING_SIZE_DP = 124f
private const val BASE_STROKE_DP = 9f
private const val MIN_STROKE_DP = 6.5f
private const val MAX_STROKE_DP = 22f
internal const val MIN_INNER_DIAMETER_DP = 48f
private const val OFFSET_UP_TWO_LINES_DP = 9.5f
private const val OFFSET_UP_ONE_LINE_DP = 5.5f
private const val DETAILS_GAP_TWO_LINES_DP = 3f
private const val DETAILS_GAP_ONE_LINE_DP = 4f
private const val HALF_HEIGHT_FACTOR = 0.48f
private const val GAUGE_HORIZONTAL_RATIO = 0.10f
private const val RING_HORIZONTAL_RATIO = 0.14f
internal const val CIRCULAR_PERCENT_WIDTH_RATIO = 0.84f
internal const val CIRCULAR_PERCENT_HEIGHT_RATIO = 0.30f
internal const val CIRCULAR_PERCENT_MIN_SP = 14f
internal const val CIRCULAR_PERCENT_BASE_MAX_SP = 27f
internal const val CIRCULAR_PERCENT_MIN_MAX_SP = 18f
internal const val CIRCULAR_PERCENT_UPPER_MAX_SP = 64f
internal const val CIRCULAR_DETAIL_MIN_FIT_SP = 6.5f
internal const val CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR = 1.4f
private const val MIN_DETAIL_PRIMARY_SP = 8.5f
private const val BASE_DETAIL_PRIMARY_SP = 10f
private const val MAX_DETAIL_PRIMARY_SP = 16f
private const val MIN_DETAIL_SECONDARY_SP = 8f
private const val BASE_DETAIL_SECONDARY_SP = 9f
private const val MAX_DETAIL_SECONDARY_SP = 14f
private const val BASE_PILL_HORIZONTAL_PADDING_DP = 6f
private const val MIN_PILL_HORIZONTAL_PADDING_DP = 5f
private const val MAX_PILL_HORIZONTAL_PADDING_DP = 10f
private const val BASE_PILL_VERTICAL_PADDING_DP = 2f
private const val MIN_PILL_VERTICAL_PADDING_DP = 1.5f
private const val MAX_PILL_VERTICAL_PADDING_DP = 4f
private const val BASE_PILL_CORNER_RADIUS_DP = 6f
private const val MIN_PILL_CORNER_RADIUS_DP = 5f
private const val MAX_PILL_CORNER_RADIUS_DP = 10f
private const val MIN_DETAIL_MAX_WIDTH_DP = 40f
private const val TWO_DETAIL_LINES = 2

internal data class CircularRingLayout(
    val ringSize: Dp,
    val strokeWidth: Dp,
    val innerDiameter: Dp,
    val percentMaxWidth: Dp,
    val percentMaxHeight: Dp,
    val percentBottomBias: Dp,
    val detailsTopPadding: Dp,
    val detailsHorizontalPadding: Dp,
    val detailMaxWidth: Dp,
    val primaryDetailMaxSp: Float,
    val secondaryDetailMaxSp: Float,
    val pillHorizontalPadding: Dp,
    val pillVerticalPadding: Dp,
    val pillCornerRadius: Dp,
)

internal fun circularRingStrokeDp(ringSize: Dp): Dp {
    val scale = ringSize / BASE_RING_SIZE_DP.dp
    return (BASE_STROKE_DP.dp * scale).coerceIn(MIN_STROKE_DP.dp, MAX_STROKE_DP.dp)
}

internal fun circularPercentMaxSp(ringSize: Dp): Float {
    val scale = ringSize / BASE_RING_SIZE_DP.dp
    return (CIRCULAR_PERCENT_BASE_MAX_SP * scale)
        .coerceIn(CIRCULAR_PERCENT_MIN_MAX_SP, CIRCULAR_PERCENT_UPPER_MAX_SP)
}

internal fun circularDetailLineCount(
    showReset: Boolean,
    usageResetStyle: UsageResetStyle,
    hasFooterText: Boolean,
): Int = when {
    showReset && (usageResetStyle == UsageResetStyle.FOOTER_STACKED || usageResetStyle == UsageResetStyle.PILL) ->
        if (hasFooterText) TWO_DETAIL_LINES else 1
    showReset && usageResetStyle == UsageResetStyle.FOOTER -> 1
    hasFooterText -> 1
    else -> 0
}

internal fun computeCircularRingLayout(
    ringSize: Dp,
    detailLineCount: Int,
    isGauge: Boolean,
    percentFontSizeSp: Float,
    fontScale: Float = 1f,
): CircularRingLayout {
    val scale = ringSize / BASE_RING_SIZE_DP.dp
    val strokeWidth = circularRingStrokeDp(ringSize)
    val innerDiameter = (ringSize - strokeWidth * 2).coerceAtLeast(MIN_INNER_DIAMETER_DP.dp)
    val offsetDp = when {
        detailLineCount >= TWO_DETAIL_LINES -> OFFSET_UP_TWO_LINES_DP
        detailLineCount == 1 -> OFFSET_UP_ONE_LINE_DP
        else -> 0f
    }
    val centerOffsetUp = offsetDp.dp * scale
    val percentCenterY = ringSize / 2 - centerOffsetUp
    val gapDp = if (detailLineCount >= TWO_DETAIL_LINES) {
        DETAILS_GAP_TWO_LINES_DP
    } else {
        DETAILS_GAP_ONE_LINE_DP
    }
    val gap = gapDp.dp * scale
    val percentHalfHeightDp = (percentFontSizeSp * fontScale * HALF_HEIGHT_FACTOR).dp
    val detailsTopPadding = (percentCenterY + percentHalfHeightDp + gap)
        .coerceAtLeast(ringSize / 2)
    val horizontalRatio = if (isGauge) GAUGE_HORIZONTAL_RATIO else RING_HORIZONTAL_RATIO
    val detailsHorizontalPadding = strokeWidth + innerDiameter * horizontalRatio
    val detailMaxWidth = (ringSize - detailsHorizontalPadding * 2).coerceAtLeast(MIN_DETAIL_MAX_WIDTH_DP.dp)

    return CircularRingLayout(
        ringSize = ringSize,
        strokeWidth = strokeWidth,
        innerDiameter = innerDiameter,
        percentMaxWidth = innerDiameter * CIRCULAR_PERCENT_WIDTH_RATIO,
        percentMaxHeight = innerDiameter * CIRCULAR_PERCENT_HEIGHT_RATIO,
        percentBottomBias = centerOffsetUp * 2,
        detailsTopPadding = detailsTopPadding,
        detailsHorizontalPadding = detailsHorizontalPadding,
        detailMaxWidth = detailMaxWidth,
        primaryDetailMaxSp = (BASE_DETAIL_PRIMARY_SP * scale)
            .coerceIn(MIN_DETAIL_PRIMARY_SP, MAX_DETAIL_PRIMARY_SP),
        secondaryDetailMaxSp = (BASE_DETAIL_SECONDARY_SP * scale)
            .coerceIn(MIN_DETAIL_SECONDARY_SP, MAX_DETAIL_SECONDARY_SP),
        pillHorizontalPadding = (BASE_PILL_HORIZONTAL_PADDING_DP * scale)
            .coerceIn(MIN_PILL_HORIZONTAL_PADDING_DP, MAX_PILL_HORIZONTAL_PADDING_DP).dp,
        pillVerticalPadding = (BASE_PILL_VERTICAL_PADDING_DP * scale)
            .coerceIn(MIN_PILL_VERTICAL_PADDING_DP, MAX_PILL_VERTICAL_PADDING_DP).dp,
        pillCornerRadius = (BASE_PILL_CORNER_RADIUS_DP * scale)
            .coerceIn(MIN_PILL_CORNER_RADIUS_DP, MAX_PILL_CORNER_RADIUS_DP).dp,
    )
}

internal fun circularRingSizeForBox(availableWidth: Dp, availableHeight: Dp): Dp =
    minOf(availableWidth, availableHeight).coerceAtLeast(MIN_RING_SIZE_DP.dp)
