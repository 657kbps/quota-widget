package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay

/**
 * Continuous thin usage progress for Glance widgets (BAR style).
 * Fill and warning color follow [usageDisplayMode].
 */
@Composable
internal fun UsageProgressBar(
    usedPercent: Double,
    usageDisplayMode: UsageDisplayMode,
) {
    UsageBarProgressIndicator(
        fillFraction = displayUsageFillFraction(usedPercent, usageDisplayMode),
        nearLimit = isUsageNearLimitForDisplay(usedPercent, usageDisplayMode),
    )
}

@Composable
internal fun UsageBarProgressIndicator(
    fillFraction: Float,
    nearLimit: Boolean,
) {
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error
    } else {
        GlanceTheme.colors.primary
    }
    LinearProgressIndicator(
        progress = fillFraction.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp),
        color = fillColor,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

/**
 * Overview-style usage bar with label + percent inside.
 * Progress is [LinearProgressIndicator]; Box only overlays text.
 * Uses a rounded rectangle (not a full stadium capsule).
 *
 * Text color follows fill amount: on the track when fill is low
 * ([onPrimaryContainer] / [onErrorContainer]), on the fill when high
 * ([onPrimary] / [onError]).
 */
@Composable
internal fun UsageCapsuleProgressBar(
    label: String,
    percentText: String,
    usedPercent: Double?,
    usageDisplayMode: UsageDisplayMode,
    height: Dp = 28.dp,
    cornerRadius: Dp = 8.dp,
    labelSize: TextUnit = 12.sp,
    percentSize: TextUnit = 12.sp,
) {
    val fillFraction = usedPercent?.let {
        displayUsageFillFraction(it, usageDisplayMode)
    } ?: 0f
    val nearLimit = usedPercent?.let {
        isUsageNearLimitForDisplay(it, usageDisplayMode)
    } ?: false
    UsageCapsuleProgressBar(
        label = label,
        percentText = percentText,
        fillFraction = fillFraction,
        nearLimit = nearLimit,
        height = height,
        cornerRadius = cornerRadius,
        labelSize = labelSize,
        percentSize = percentSize,
    )
}

@Composable
internal fun UsageCapsuleProgressBar(
    label: String,
    percentText: String,
    fillFraction: Float,
    nearLimit: Boolean,
    height: Dp = 28.dp,
    cornerRadius: Dp = 8.dp,
    labelSize: TextUnit = 12.sp,
    percentSize: TextUnit = 12.sp,
) {
    val progress = fillFraction.coerceIn(0f, 1f)
    val trackColor = if (nearLimit) {
        GlanceTheme.colors.errorContainer
    } else {
        GlanceTheme.colors.primaryContainer
    }
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error
    } else {
        GlanceTheme.colors.primary
    }
    // Prefer contrast against the larger visible region of the bar.
    val contentColor = if (nearLimit) {
        if (progress < CapsuleTextOnFillThreshold) {
            GlanceTheme.colors.onErrorContainer
        } else {
            GlanceTheme.colors.onError
        }
    } else if (progress < CapsuleTextOnFillThreshold) {
        GlanceTheme.colors.onPrimaryContainer
    } else {
        GlanceTheme.colors.onPrimary
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .cornerRadius(cornerRadius),
        contentAlignment = Alignment.CenterStart,
    ) {
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(cornerRadius),
            color = fillColor,
            backgroundColor = trackColor,
        )
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = percentText,
                style = TextStyle(
                    color = contentColor,
                    fontSize = percentSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Capsule track only — percent widgets show the value as a large title above. */
@Composable
internal fun UsageCapsuleProgressTrack(
    usedPercent: Double?,
    usageDisplayMode: UsageDisplayMode,
    height: Dp = 12.dp,
) {
    val fillFraction = usedPercent?.let {
        displayUsageFillFraction(it, usageDisplayMode)
    } ?: 0f
    val nearLimit = usedPercent?.let {
        isUsageNearLimitForDisplay(it, usageDisplayMode)
    } ?: false
    val trackColor = if (nearLimit) {
        GlanceTheme.colors.errorContainer
    } else {
        GlanceTheme.colors.primaryContainer
    }
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error
    } else {
        GlanceTheme.colors.primary
    }
    LinearProgressIndicator(
        progress = fillFraction.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .cornerRadius(height / 2),
        color = fillColor,
        backgroundColor = trackColor,
    )
}

/** Switch overlay text to on-fill colors once most of the bar is filled. */
private const val CapsuleTextOnFillThreshold = 0.45f
