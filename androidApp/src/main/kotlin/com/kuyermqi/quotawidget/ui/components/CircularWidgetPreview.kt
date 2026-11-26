package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuyermqi.quotawidget.domain.CircularArcSpec
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.buildCircularArcSpec
import com.kuyermqi.quotawidget.widget.TextFitBounds
import com.kuyermqi.quotawidget.widget.fitMeasuredTextSize
import com.kuyermqi.quotawidget.widget.measureBoldWidthPerPx
import com.kuyermqi.quotawidget.widget.measureTextWidthPerPx
import com.kuyermqi.quotawidget.widget.usage.CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR
import com.kuyermqi.quotawidget.widget.usage.CIRCULAR_DETAIL_MIN_FIT_SP
import com.kuyermqi.quotawidget.widget.usage.CIRCULAR_PERCENT_HEIGHT_RATIO
import com.kuyermqi.quotawidget.widget.usage.CIRCULAR_PERCENT_MIN_SP
import com.kuyermqi.quotawidget.widget.usage.CIRCULAR_PERCENT_WIDTH_RATIO
import com.kuyermqi.quotawidget.widget.usage.CircularRingLayout
import com.kuyermqi.quotawidget.widget.usage.circularDetailLineCount
import com.kuyermqi.quotawidget.widget.usage.circularPercentMaxSp
import com.kuyermqi.quotawidget.widget.usage.circularRingSizeForBox
import com.kuyermqi.quotawidget.widget.usage.circularRingStrokeDp
import com.kuyermqi.quotawidget.widget.usage.computeCircularRingLayout

private const val MIN_PREVIEW_INNER_DIAMETER_DP = 48f
private const val PREVIEW_PERCENT_SIDE_INSET_DP = 4f
private const val PREVIEW_LINE_HEIGHT_FACTOR = 1.2f
private const val MIN_PREVIEW_PILL_WIDTH_DP = 24f

internal data class CircularPreviewModel(
    val percentText: String,
    val fillFraction: Float,
    val nearLimit: Boolean,
    val usageProgressStyle: UsageProgressStyle,
    val usageResetStyle: UsageResetStyle,
    val resetAtLabel: String?,
    val pillText: String?,
    val defaultFooter: String,
)

private data class CircularPreviewDetailsModel(
    val layout: CircularRingLayout,
    val usageResetStyle: UsageResetStyle,
    val resetAtLabel: String?,
    val pillText: String?,
    val defaultFooter: String,
)

@Suppress("FunctionNaming")
@Composable
internal fun StandardCircularWidgetPreview(
    model: CircularPreviewModel,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    val fillColor = if (model.nearLimit) scheme.error else scheme.primary
    val trackColor = if (model.nearLimit) {
        scheme.errorContainer
    } else {
        previewLinearTrackColor()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val ringSize = circularRingSizeForBox(maxWidth, maxHeight)
        val strokeWidth = circularRingStrokeDp(ringSize)
        val innerDiameter = (ringSize - strokeWidth * 2).coerceAtLeast(MIN_PREVIEW_INNER_DIAMETER_DP.dp)
        val widthPerPx = remember(model.percentText) { measureBoldWidthPerPx(model.percentText) }
        val percentFontSize = fitMeasuredTextSize(
            metrics = resources.displayMetrics,
            widthPerPx = widthPerPx,
            bounds = TextFitBounds(
                maxWidth = innerDiameter * CIRCULAR_PERCENT_WIDTH_RATIO,
                maxHeight = innerDiameter * CIRCULAR_PERCENT_HEIGHT_RATIO,
                minSp = CIRCULAR_PERCENT_MIN_SP,
                maxSp = circularPercentMaxSp(ringSize),
            ),
        )
        val showReset = model.resetAtLabel != null && model.usageResetStyle != UsageResetStyle.HIDDEN
        val detailLineCount = circularDetailLineCount(
            showReset = showReset,
            usageResetStyle = model.usageResetStyle,
            hasFooterText = model.defaultFooter.isNotBlank(),
        )
        val layout = computeCircularRingLayout(
            ringSize = ringSize,
            detailLineCount = detailLineCount,
            isGauge = model.usageProgressStyle.isGauge,
            percentFontSizeSp = percentFontSize.value,
        )

        Box(
            modifier = Modifier.size(layout.ringSize),
            contentAlignment = Alignment.TopCenter,
        ) {
            CircularProgressCanvas(
                usageProgressStyle = model.usageProgressStyle,
                fillFraction = model.fillFraction,
                strokeWidth = layout.strokeWidth,
                fillColor = fillColor,
                trackColor = trackColor,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = layout.strokeWidth + PREVIEW_PERCENT_SIDE_INSET_DP.dp,
                        end = layout.strokeWidth + PREVIEW_PERCENT_SIDE_INSET_DP.dp,
                        bottom = layout.percentBottomBias,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = model.percentText,
                    style = previewTextStyle(percentFontSize, FontWeight.Bold),
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            if (detailLineCount > 0) {
                CircularPreviewDetails(
                    details = CircularPreviewDetailsModel(
                        layout = layout,
                        usageResetStyle = model.usageResetStyle,
                        resetAtLabel = model.resetAtLabel,
                        pillText = model.pillText,
                        defaultFooter = model.defaultFooter,
                    ),
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularPreviewDetails(details: CircularPreviewDetailsModel) {
    val layout = details.layout
    val resetAtLabel = details.resetAtLabel
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = layout.detailsTopPadding,
                start = layout.detailsHorizontalPadding,
                end = layout.detailsHorizontalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            details.pillText != null -> CircularPreviewPillDetails(
                pillText = details.pillText,
                footerText = details.defaultFooter,
                layout = layout,
            )
            resetAtLabel != null && details.usageResetStyle == UsageResetStyle.FOOTER -> {
                CircularPreviewSingleLine(
                    text = resetAtLabel,
                    layout = layout,
                    maxSp = layout.primaryDetailMaxSp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            resetAtLabel != null && details.usageResetStyle == UsageResetStyle.FOOTER_STACKED -> {
                CircularPreviewStackedDetails(
                    resetAtLabel = resetAtLabel,
                    footerText = details.defaultFooter,
                    layout = layout,
                )
            }
            else -> if (details.defaultFooter.isNotBlank()) {
                CircularPreviewSingleLine(
                    text = details.defaultFooter,
                    layout = layout,
                    maxSp = layout.primaryDetailMaxSp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularPreviewPillDetails(
    pillText: String,
    footerText: String,
    layout: CircularRingLayout,
) {
    val scheme = MaterialTheme.colorScheme
    val pillMaxWidth = (layout.detailMaxWidth - layout.pillHorizontalPadding * 2)
        .coerceAtLeast(MIN_PREVIEW_PILL_WIDTH_DP.dp)
    val pillSize = fitPreviewDetailFontSize(
        text = pillText,
        maxWidth = pillMaxWidth,
        maxSp = layout.primaryDetailMaxSp,
        bold = true,
    )
    Surface(
        shape = RoundedCornerShape(layout.pillCornerRadius),
        color = scheme.secondaryContainer,
    ) {
        Text(
            text = pillText,
            style = previewTextStyle(
                pillSize,
                FontWeight.Medium,
                lineHeight = pillSize * PREVIEW_LINE_HEIGHT_FACTOR,
            ),
            color = scheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = layout.pillHorizontalPadding,
                vertical = layout.pillVerticalPadding,
            ),
        )
    }
    if (footerText.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        CircularPreviewSingleLine(
            text = footerText,
            layout = layout,
            maxSp = layout.secondaryDetailMaxSp,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularPreviewStackedDetails(
    resetAtLabel: String,
    footerText: String,
    layout: CircularRingLayout,
) {
    val scheme = MaterialTheme.colorScheme
    val resetSize = fitPreviewDetailFontSize(
        text = resetAtLabel,
        maxWidth = layout.detailMaxWidth,
        maxSp = layout.primaryDetailMaxSp,
        bold = true,
    )
    Text(
        text = resetAtLabel,
        style = previewTextStyle(
            resetSize,
            FontWeight.Medium,
            lineHeight = resetSize * PREVIEW_LINE_HEIGHT_FACTOR,
        ),
        color = scheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (footerText.isNotBlank()) {
        Spacer(modifier = Modifier.height(1.dp))
        CircularPreviewSingleLine(
            text = footerText,
            layout = layout,
            maxSp = layout.secondaryDetailMaxSp,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CircularPreviewSingleLine(
    text: String,
    layout: CircularRingLayout,
    maxSp: Float,
    color: Color,
) {
    val fontSize = fitPreviewDetailFontSize(
        text = text,
        maxWidth = layout.detailMaxWidth,
        maxSp = maxSp,
        bold = false,
    )
    Text(
        text = text,
        style = previewTextStyle(fontSize, lineHeight = fontSize * PREVIEW_LINE_HEIGHT_FACTOR),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun fitPreviewDetailFontSize(
    text: String,
    maxWidth: Dp,
    maxSp: Float,
    bold: Boolean,
): TextUnit {
    val resources = LocalResources.current
    val widthPerPx = remember(text, bold) { measureTextWidthPerPx(text, bold) }
    return fitMeasuredTextSize(
        metrics = resources.displayMetrics,
        widthPerPx = widthPerPx,
        bounds = TextFitBounds(
            maxWidth = maxWidth,
            maxHeight = (maxSp * CIRCULAR_DETAIL_LINE_HEIGHT_FACTOR).dp,
            minSp = CIRCULAR_DETAIL_MIN_FIT_SP,
            maxSp = maxSp,
        ),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun CircularProgressCanvas(
    usageProgressStyle: UsageProgressStyle,
    fillFraction: Float,
    strokeWidth: Dp,
    fillColor: Color,
    trackColor: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(strokePx / 2f, strokePx / 2f)
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        when (
            val spec = buildCircularArcSpec(
                style = usageProgressStyle,
                fillFraction = fillFraction,
                diameterPx = size.minDimension,
                strokePx = strokePx,
            )
        ) {
            is CircularArcSpec.Continuous -> {
                drawArc(
                    color = trackColor,
                    startAngle = spec.startAngleDeg,
                    sweepAngle = spec.trackSweepDeg,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                if (spec.progressSweepDeg > 0f) {
                    drawArc(
                        color = fillColor,
                        startAngle = spec.startAngleDeg,
                        sweepAngle = spec.progressSweepDeg,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            }
            is CircularArcSpec.Segmented -> {
                spec.segments.forEach { segment ->
                    drawArc(
                        color = if (segment.active) fillColor else trackColor,
                        startAngle = segment.startAngleDeg,
                        sweepAngle = segment.sweepAngleDeg,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                }
            }
        }
    }
}
