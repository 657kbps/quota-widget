package com.kuyermqi.quotawidget.widget.usage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.domain.CircularArcSpec
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.buildCircularArcSpec
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.widget.clickableNoRipple
import com.kuyermqi.quotawidget.widget.fittedTextFontSize
import com.kuyermqi.quotawidget.widget.fittedTitleFontSize
import kotlin.math.roundToInt

private const val MAX_RING_BITMAP_PX = 480
private const val MIN_RING_BITMAP_PX = 32
private const val MIN_STROKE_PX = 2f
private const val RING_HORIZONTAL_RESERVE_DP = 28f
private const val RING_VERTICAL_RESERVE_DP = 54f
private const val MIN_PILL_CONTENT_WIDTH_DP = 24f
private const val PERCENT_SIDE_INSET_DP = 4f

internal data class CircularWidgetContentModel(
    val usageText: String,
    val progressUsed: Double,
    val progressMode: UsageDisplayMode,
    val usageProgressStyle: UsageProgressStyle,
    val usageResetStyle: UsageResetStyle,
    val resetAtEpochMs: Long?,
    val nowEpochMs: Long,
    val updatedFooter: String,
)

private data class CircularBitmapDrawParams(
    val ringSizeDp: Dp,
    val strokeWidthDp: Dp,
    val style: UsageProgressStyle,
    val fillFraction: Float,
    @param:ColorInt val fillColor: Int,
    @param:ColorInt val trackColor: Int,
)

private data class CircularDetailsState(
    val layout: CircularRingLayout,
    val usageResetStyle: UsageResetStyle,
    val showReset: Boolean,
    val resetAtEpochMs: Long?,
    val nowEpochMs: Long,
    val pillText: String?,
    val updatedFooter: String,
)

@Suppress("FunctionNaming")
@Composable
internal fun UsageCircularSuccessBlock(
    model: CircularWidgetContentModel,
    openApp: Action,
) {
    val widgetSize = LocalSize.current
    val ringSize = circularRingSizeForBox(
        availableWidth = widgetSize.width - RING_HORIZONTAL_RESERVE_DP.dp,
        availableHeight = widgetSize.height - RING_VERTICAL_RESERVE_DP.dp,
    )
    val strokeWidth = circularRingStrokeDp(ringSize)
    val innerDiameter = (ringSize - strokeWidth * 2).coerceAtLeast(MIN_INNER_DIAMETER_DP.dp)

    val percentFontSize = fittedTitleFontSize(
        text = model.usageText,
        maxWidth = innerDiameter * CIRCULAR_PERCENT_WIDTH_RATIO,
        maxHeight = innerDiameter * CIRCULAR_PERCENT_HEIGHT_RATIO,
        minSp = CIRCULAR_PERCENT_MIN_SP,
        maxSp = circularPercentMaxSp(ringSize),
    )

    val showReset = model.resetAtEpochMs != null && model.usageResetStyle != UsageResetStyle.HIDDEN
    val detailLineCount = circularDetailLineCount(
        showReset = showReset,
        usageResetStyle = model.usageResetStyle,
        hasFooterText = model.updatedFooter.isNotBlank(),
    )
    val layout = computeCircularRingLayout(
        ringSize = ringSize,
        detailLineCount = detailLineCount,
        isGauge = model.usageProgressStyle.isGauge,
        percentFontSizeSp = percentFontSize.value,
    )

    val fillFraction = displayUsageFillFraction(model.progressUsed, model.progressMode)
    val nearLimit = isUsageNearLimitForDisplay(model.progressUsed, model.progressMode)
    val glanceContext = LocalContext.current
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error.getColor(glanceContext).toArgb()
    } else {
        GlanceTheme.colors.primary.getColor(glanceContext).toArgb()
    }
    val trackColor = if (nearLimit) {
        GlanceTheme.colors.errorContainer.getColor(glanceContext).toArgb()
    } else {
        progressTrackColorArgb(
            surfaceVariantArgb = GlanceTheme.colors.surfaceVariant.getColor(glanceContext).toArgb(),
            onSurfaceArgb = GlanceTheme.colors.onSurface.getColor(glanceContext).toArgb(),
        )
    }
    val bitmap = drawCircularProgressBitmap(
        context = glanceContext,
        params = CircularBitmapDrawParams(
            ringSizeDp = layout.ringSize,
            strokeWidthDp = layout.strokeWidth,
            style = model.usageProgressStyle,
            fillFraction = fillFraction,
            fillColor = fillColor,
            trackColor = trackColor,
        ),
    )

    val pillText = model.resetAtEpochMs
        ?.takeIf { model.usageResetStyle == UsageResetStyle.PILL }
        ?.let { formatUsageResetCountdown(it - model.nowEpochMs) }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickableNoRipple(openApp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(layout.ringSize)
                .clickableNoRipple(openApp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickableNoRipple(openApp),
            )
            CircularCenterPercent(
                usageText = model.usageText,
                percentFontSize = percentFontSize,
                layout = layout,
                openApp = openApp,
            )
            if (detailLineCount > 0) {
                CircularCenterDetails(
                    state = CircularDetailsState(
                        layout = layout,
                        usageResetStyle = model.usageResetStyle,
                        showReset = showReset,
                        resetAtEpochMs = model.resetAtEpochMs,
                        nowEpochMs = model.nowEpochMs,
                        pillText = pillText,
                        updatedFooter = model.updatedFooter,
                    ),
                    openApp = openApp,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularCenterPercent(
    usageText: String,
    percentFontSize: TextUnit,
    layout: CircularRingLayout,
    openApp: Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(
                start = layout.strokeWidth + PERCENT_SIDE_INSET_DP.dp,
                end = layout.strokeWidth + PERCENT_SIDE_INSET_DP.dp,
                bottom = layout.percentBottomBias,
            )
            .clickableNoRipple(openApp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = usageText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = percentFontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularCenterDetails(
    state: CircularDetailsState,
    openApp: Action,
) {
    val resetAtEpochMs = state.resetAtEpochMs
    val layout = state.layout
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(
                top = layout.detailsTopPadding,
                start = layout.detailsHorizontalPadding,
                end = layout.detailsHorizontalPadding,
            )
            .clickableNoRipple(openApp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.pillText != null -> CircularCenterPillDetails(
                pillText = state.pillText,
                footerText = state.updatedFooter,
                layout = layout,
                openApp = openApp,
            )
            state.showReset && resetAtEpochMs != null && state.usageResetStyle == UsageResetStyle.FOOTER -> {
                CircularCenterFooterLine(
                    text = resetAtText(resetAtEpochMs, state.nowEpochMs, circular = true),
                    layout = layout,
                    maxSp = layout.primaryDetailMaxSp,
                    openApp = openApp,
                )
            }
            state.showReset && resetAtEpochMs != null && state.usageResetStyle == UsageResetStyle.FOOTER_STACKED -> {
                CircularCenterStackedDetails(
                    resetLabel = resetAtText(resetAtEpochMs, state.nowEpochMs, circular = true),
                    footerText = state.updatedFooter,
                    layout = layout,
                    openApp = openApp,
                )
            }
            else -> if (state.updatedFooter.isNotBlank()) {
                CircularCenterFooterLine(
                    text = state.updatedFooter,
                    layout = layout,
                    maxSp = layout.primaryDetailMaxSp,
                    openApp = openApp,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularCenterPillDetails(
    pillText: String,
    footerText: String,
    layout: CircularRingLayout,
    openApp: Action,
) {
    val pillMaxWidth = (layout.detailMaxWidth - layout.pillHorizontalPadding * 2)
        .coerceAtLeast(MIN_PILL_CONTENT_WIDTH_DP.dp)
    val pillFontSize = fittedTextFontSize(
        text = pillText,
        maxWidth = pillMaxWidth,
        maxHeight = (layout.primaryDetailMaxSp * CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR).dp,
        minSp = CIRCULAR_DETAIL_MIN_FIT_SP,
        maxSp = layout.primaryDetailMaxSp,
        bold = true,
    )
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(layout.pillCornerRadius)
            .padding(horizontal = layout.pillHorizontalPadding, vertical = layout.pillVerticalPadding)
            .clickableNoRipple(openApp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pillText,
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = pillFontSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
    if (footerText.isNotBlank()) {
        Spacer(GlanceModifier.height(2.dp))
        CircularCenterFooterLine(
            text = footerText,
            layout = layout,
            maxSp = layout.secondaryDetailMaxSp,
            openApp = openApp,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularCenterStackedDetails(
    resetLabel: String,
    footerText: String,
    layout: CircularRingLayout,
    openApp: Action,
) {
    val resetFontSize = fittedTextFontSize(
        text = resetLabel,
        maxWidth = layout.detailMaxWidth,
        maxHeight = (layout.primaryDetailMaxSp * CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR).dp,
        minSp = CIRCULAR_DETAIL_MIN_FIT_SP,
        maxSp = layout.primaryDetailMaxSp,
        bold = true,
    )
    Text(
        text = resetLabel,
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = resetFontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        modifier = GlanceModifier.clickableNoRipple(openApp),
    )
    if (footerText.isNotBlank()) {
        Spacer(GlanceModifier.height(1.dp))
        CircularCenterFooterLine(
            text = footerText,
            layout = layout,
            maxSp = layout.secondaryDetailMaxSp,
            openApp = openApp,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularCenterFooterLine(
    text: String,
    layout: CircularRingLayout,
    maxSp: Float,
    openApp: Action,
) {
    val fontSize = fittedTextFontSize(
        text = text,
        maxWidth = layout.detailMaxWidth,
        maxHeight = (maxSp * CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR).dp,
        minSp = CIRCULAR_DETAIL_MIN_FIT_SP,
        maxSp = maxSp,
        bold = false,
    )
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        modifier = GlanceModifier.clickableNoRipple(openApp),
    )
}

private fun drawCircularProgressBitmap(
    context: Context,
    params: CircularBitmapDrawParams,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val rawPx = (params.ringSizeDp.value * density).roundToInt().coerceAtLeast(MIN_RING_BITMAP_PX)
    val sizePx = rawPx.coerceAtMost(MAX_RING_BITMAP_PX)
    val scale = sizePx.toFloat() / rawPx.toFloat()
    val strokePx = (params.strokeWidthDp.value * density * scale).coerceAtLeast(MIN_STROKE_PX)

    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val halfStroke = strokePx / 2f
    val oval = RectF(
        halfStroke,
        halfStroke,
        sizePx - halfStroke,
        sizePx - halfStroke,
    )

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
        color = params.trackColor
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
        color = params.fillColor
    }

    when (
        val spec = buildCircularArcSpec(
            style = params.style,
            fillFraction = params.fillFraction,
            diameterPx = sizePx.toFloat(),
            strokePx = strokePx,
        )
    ) {
        is CircularArcSpec.Continuous -> {
            canvas.drawArc(oval, spec.startAngleDeg, spec.trackSweepDeg, false, trackPaint)
            if (spec.progressSweepDeg > 0f) {
                canvas.drawArc(oval, spec.startAngleDeg, spec.progressSweepDeg, false, fillPaint)
            }
        }
        is CircularArcSpec.Segmented -> {
            spec.segments.forEach { segment ->
                val paint = if (segment.active) fillPaint else trackPaint
                canvas.drawArc(oval, segment.startAngleDeg, segment.sweepAngleDeg, false, paint)
            }
        }
    }
    return bitmap
}
