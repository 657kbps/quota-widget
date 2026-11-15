package com.kuyermqi.quotawidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleySnapshot
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleyTier
import com.kuyermqi.quotawidget.ui.peakvalley.formatPeakValleyCountdown
import com.kuyermqi.quotawidget.ui.peakvalley.formatPeakValleyTarget

@Composable
internal fun DeepSeekPeakValleyWidgetSection(
    snapshot: PeakValleySnapshot,
    showTarget: Boolean,
    showCountdown: Boolean,
    compact: Boolean,
    openApp: Action,
) {
    val status = when (snapshot.tier) {
        PeakValleyTier.PEAK -> contextString(R.string.widget_peak_valley_peak)
        PeakValleyTier.OFF_PEAK -> contextString(R.string.widget_peak_valley_off_peak)
        PeakValleyTier.UNCONFIRMED -> contextString(R.string.widget_peak_valley_unconfirmed)
    }
    val statusColor = when (snapshot.tier) {
        PeakValleyTier.PEAK -> GlanceTheme.colors.primary
        PeakValleyTier.OFF_PEAK -> GlanceTheme.colors.onSurfaceVariant
        PeakValleyTier.UNCONFIRMED -> GlanceTheme.colors.error
    }
    val nextChangeEpochMs = snapshot.nextChangeEpochMs
    val tomorrowLabel = contextString(R.string.peak_valley_tomorrow)
    val targetText = if (nextChangeEpochMs != null) {
        contextString(
            R.string.widget_peak_valley_until,
            formatPeakValleyTarget(
                nextChangeEpochMs,
                snapshot.referenceEpochMs,
                tomorrowLabel,
            ),
        )
    } else {
        contextString(R.string.widget_peak_valley_until_unknown)
    }
    val statusSize = if (compact) 11.sp else 12.sp
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickableNoRipple(openApp),
    ) {
        Text(
            text = status,
            style = TextStyle(
                color = statusColor,
                fontSize = statusSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
        if (showTarget) {
            Text(
                text = targetText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        }
        if (showCountdown) {
            Spacer(GlanceModifier.height(1.dp))
            val countdownText = snapshot.countdownToNextChangeMs?.let { remaining ->
                contextString(
                    R.string.widget_peak_valley_countdown,
                    formatPeakValleyCountdown(contextResources(), remaining),
                )
            } ?: contextString(R.string.widget_peak_valley_countdown_unknown)
            Text(
                text = countdownText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        }
    }
}
