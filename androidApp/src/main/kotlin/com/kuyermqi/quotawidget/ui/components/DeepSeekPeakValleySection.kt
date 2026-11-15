package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleySnapshot
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleyTier
import com.kuyermqi.quotawidget.ui.peakvalley.formatPeakValleyCountdown
import com.kuyermqi.quotawidget.ui.peakvalley.formatPeakValleyTarget
import com.kuyermqi.quotawidget.ui.peakvalley.formatPeakValleyTimestamp

sealed interface CalendarUpdateNotice {
    data object Success : CalendarUpdateNotice
    data class NotPublished(val years: List<Int>) : CalendarUpdateNotice
    data object Failed : CalendarUpdateNotice
}

@Composable
fun ColumnScope.DeepSeekPeakValleySection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    snapshot: PeakValleySnapshot,
    currentYear: Int,
    coveredYears: Set<Int>,
    lastSuccessAtEpochMs: Long,
    failedYears: List<Int>,
    notPublishedYears: List<Int>,
    isUpdating: Boolean,
    updateNotice: CalendarUpdateNotice?,
    onUpdate: () -> Unit,
) {
    val resources = LocalResources.current
    val tomorrowLabel = stringResource(R.string.peak_valley_tomorrow)
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(
        text = stringResource(R.string.deepseek_peak_valley_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.deepseek_peak_valley_price_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.deepseek_peak_valley_enabled),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.deepseek_peak_valley_enabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.deepseek_peak_valley_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (enabled) {
                        stringResource(R.string.deepseek_peak_valley_preview_enabled)
                    } else {
                        stringResource(R.string.deepseek_peak_valley_preview_disabled)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            val status = when (snapshot.tier) {
                PeakValleyTier.PEAK -> stringResource(R.string.deepseek_peak_valley_peak)
                PeakValleyTier.OFF_PEAK -> stringResource(R.string.deepseek_peak_valley_off_peak)
                PeakValleyTier.UNCONFIRMED ->
                    stringResource(R.string.deepseek_peak_valley_unconfirmed)
            }
            val statusColor = when (snapshot.tier) {
                PeakValleyTier.PEAK -> MaterialTheme.colorScheme.primary
                PeakValleyTier.OFF_PEAK -> MaterialTheme.colorScheme.onSurface
                PeakValleyTier.UNCONFIRMED -> MaterialTheme.colorScheme.error
            }
            Text(
                text = status,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor,
            )
            Text(
                text = snapshot.nextChangeEpochMs?.let { target ->
                    stringResource(
                        R.string.deepseek_peak_valley_target,
                        formatPeakValleyTarget(
                            target,
                            snapshot.referenceEpochMs,
                            tomorrowLabel,
                        ),
                    )
                } ?: stringResource(R.string.deepseek_peak_valley_target_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snapshot.countdownToNextChangeMs != null) {
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_countdown,
                        formatPeakValleyCountdown(
                            resources,
                            snapshot.countdownToNextChangeMs ?: 0L,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.deepseek_peak_valley_countdown_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_next_evaluation,
                        formatPeakValleyTarget(
                            snapshot.nextEvaluationEpochMs,
                            snapshot.referenceEpochMs,
                            tomorrowLabel,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.deepseek_peak_valley_calendar_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (coveredYears.isEmpty()) {
                    stringResource(R.string.deepseek_peak_valley_calendar_no_years)
                } else {
                    stringResource(
                        R.string.deepseek_peak_valley_calendar_years,
                        coveredYears.sorted().joinToString("、"),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (currentYear !in coveredYears) {
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_calendar_current_missing,
                        currentYear,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (lastSuccessAtEpochMs > 0L) {
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_calendar_updated,
                        formatPeakValleyTimestamp(lastSuccessAtEpochMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.deepseek_peak_valley_calendar_builtin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failedYears.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_calendar_attempt_failed,
                        failedYears.joinToString("、"),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (notPublishedYears.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.deepseek_peak_valley_calendar_not_published,
                        notPublishedYears.joinToString("、"),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            updateNotice?.let { notice ->
                val noticeText = when (notice) {
                    CalendarUpdateNotice.Success ->
                        stringResource(R.string.deepseek_peak_valley_calendar_update_success)
                    CalendarUpdateNotice.Failed ->
                        stringResource(R.string.deepseek_peak_valley_calendar_update_failed)
                    is CalendarUpdateNotice.NotPublished -> stringResource(
                        R.string.deepseek_peak_valley_calendar_update_not_published,
                        notice.years.joinToString("、"),
                    )
                }
                Text(
                    text = noticeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notice is CalendarUpdateNotice.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = stringResource(R.string.deepseek_peak_valley_calendar_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onUpdate, enabled = !isUpdating) {
                    Text(
                        if (isUpdating) {
                            stringResource(R.string.deepseek_peak_valley_calendar_updating)
                        } else {
                            stringResource(R.string.deepseek_peak_valley_calendar_update)
                        },
                    )
                }
            }
        }
    }
}
