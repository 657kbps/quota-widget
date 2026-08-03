package com.kuyermqi.quotawidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.kuyermqi.quotawidget.domain.isUsageNearLimit
import kotlin.math.roundToInt

/**
 * Segmented usage progress for OpenCode Glance widgets.
 * [usedPercent] drives fill amount and the ≥90% warning color.
 */
@Composable
internal fun OpenCodeUsedProgressBar(usedPercent: Double) {
    OpenCodeSegmentProgressBar(
        fillFraction = (usedPercent / 100.0).toFloat().coerceIn(0f, 1f),
        nearLimit = isUsageNearLimit(usedPercent),
    )
}

/**
 * Segmented bar filled by [fillFraction] (0–1). Use [nearLimit] when remaining is shown
 * but usage itself is past the warning threshold.
 */
@Composable
internal fun OpenCodeSegmentProgressBar(
    fillFraction: Float,
    nearLimit: Boolean,
) {
    val segments = 20
    val filled = (fillFraction * segments).roundToInt().coerceIn(0, segments)
    val fillColor = if (nearLimit) {
        GlanceTheme.colors.error
    } else {
        GlanceTheme.colors.primary
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .cornerRadius(3.dp),
    ) {
        repeat(filled) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .background(fillColor),
            ) {}
        }
        repeat(segments - filled) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
        }
    }
}
