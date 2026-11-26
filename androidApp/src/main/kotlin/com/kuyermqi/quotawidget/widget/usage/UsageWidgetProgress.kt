package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import androidx.glance.unit.ColorProvider
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
    val glanceContext = LocalContext.current
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error
    } else {
        GlanceTheme.colors.primary
    }
    val trackColor = ColorProvider(
        Color(
            progressTrackColorArgb(
                surfaceVariantArgb = GlanceTheme.colors.surfaceVariant.getColor(glanceContext).toArgb(),
                onSurfaceArgb = GlanceTheme.colors.onSurface.getColor(glanceContext).toArgb(),
            ),
        ),
    )
    LinearProgressIndicator(
        progress = fillFraction.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp),
        color = fillColor,
        backgroundColor = trackColor,
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
    resetText: String? = null,
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
        resetText = resetText,
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
    resetText: String? = null,
) {
    val progress = fillFraction.coerceIn(0f, 1f)
    val glanceContext = LocalContext.current
    val trackColor = GlanceTheme.colors.surface
    val fillArgb = if (nearLimit) {
        deepenedCapsuleFillColor(
            containerArgb = GlanceTheme.colors.errorContainer.getColor(glanceContext).toArgb(),
            accentArgb = GlanceTheme.colors.error.getColor(glanceContext).toArgb(),
        )
    } else {
        deepenedCapsuleFillColor(
            containerArgb = GlanceTheme.colors.primaryContainer.getColor(glanceContext).toArgb(),
            accentArgb = GlanceTheme.colors.primary.getColor(glanceContext).toArgb(),
        )
    }
    val fillColor = ColorProvider(Color(fillArgb))
    val contentColor = if (nearLimit) {
        GlanceTheme.colors.onErrorContainer
    } else {
        GlanceTheme.colors.onSurface
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
                text = if (resetText != null) "$label · $resetText" else label,
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

private const val LIGHT_CAPSULE_FILL_BLEND = 0.30f
private const val DARK_CAPSULE_FILL_BLEND = 0.20f
private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.35
private const val DARK_TRACK_CONTRAST_BLEND = 0.22f

internal fun deepenedCapsuleFillColor(containerArgb: Int, accentArgb: Int): Int {
    val isLightMode = ColorUtils.calculateLuminance(containerArgb) >
        ColorUtils.calculateLuminance(accentArgb)
    val ratio = if (isLightMode) LIGHT_CAPSULE_FILL_BLEND else DARK_CAPSULE_FILL_BLEND
    return ColorUtils.blendARGB(containerArgb, accentArgb, ratio)
}

internal fun progressTrackColorArgb(surfaceVariantArgb: Int, onSurfaceArgb: Int): Int {
    val isDarkMode = ColorUtils.calculateLuminance(surfaceVariantArgb) <
        DARK_SURFACE_LUMINANCE_THRESHOLD
    return if (isDarkMode) {
        ColorUtils.blendARGB(surfaceVariantArgb, onSurfaceArgb, DARK_TRACK_CONTRAST_BLEND)
    } else {
        surfaceVariantArgb
    }
}
