package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import kotlin.math.roundToInt

/**
 * Shared usage prefs: optional window-kind chips, used/remaining mode, and progress bars.
 *
 * [overviewKinds] controls which bars appear (OpenCode: fixed 5h/week/month;
 * Codex: kinds present in [windows]).
 */
@Composable
fun ColumnScope.UsageDisplayPrefsSection(
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    enabled: Boolean,
    windowKindChoices: List<UsageWindowKind>,
    showWindowKindPicker: Boolean,
    overviewKinds: List<QuotaWindowKind>,
) {
    if (showWindowKindPicker && windowKindChoices.isNotEmpty()) {
        Text(
            text = stringResource(R.string.usage_widget_window_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            windowKindChoices.forEach { kind ->
                UsageWindowChip(
                    selected = widgetWindowKind == kind,
                    label = stringResource(usageWindowKindLabelRes(kind)),
                    enabled = enabled,
                    onClick = { onWidgetWindowKindChange(kind) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    Text(
        text = stringResource(R.string.usage_display_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UsageWindowChip(
            selected = usageDisplayMode == UsageDisplayMode.USED,
            label = stringResource(R.string.usage_display_used),
            enabled = enabled,
            onClick = { onUsageDisplayModeChange(UsageDisplayMode.USED) },
        )
        UsageWindowChip(
            selected = usageDisplayMode == UsageDisplayMode.REMAINING,
            label = stringResource(R.string.usage_display_remaining),
            enabled = enabled,
            onClick = { onUsageDisplayModeChange(UsageDisplayMode.REMAINING) },
        )
    }
    if (windows.isNotEmpty() && overviewKinds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                overviewKinds.forEachIndexed { index, kind ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    UsageWindowBar(
                        label = stringResource(usageWindowLabelRes(kind, usageDisplayMode)),
                        window = windows.find { it.kind == kind },
                        usageDisplayMode = usageDisplayMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageWindowChip(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
private fun UsageWindowBar(
    label: String,
    window: QuotaWindow?,
    usageDisplayMode: UsageDisplayMode,
) {
    val used = window?.usedPercent ?: 0.0
    val progress = displayUsageFillFraction(used, usageDisplayMode)
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = formatUsageDisplayPercent(used, usageDisplayMode),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (isUsageNearLimitForDisplay(used, usageDisplayMode)) {
                scheme.error
            } else {
                scheme.primary
            },
            trackColor = scheme.onSurface.copy(alpha = 0.18f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUsageResetLabel(window?.resetInSec),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

private fun usageWindowKindLabelRes(kind: UsageWindowKind): Int = when (kind) {
    UsageWindowKind.ROLLING -> R.string.usage_window_rolling
    UsageWindowKind.WEEKLY -> R.string.usage_window_weekly
    UsageWindowKind.MONTHLY -> R.string.usage_window_monthly
}

@Composable
private fun formatUsageResetLabel(resetInSec: Long?): String {
    if (resetInSec == null || resetInSec < 0) {
        return stringResource(R.string.usage_resets_unknown)
    }
    val totalMinutes = (resetInSec / 60.0).roundToInt().coerceAtLeast(0)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val text = when {
        days > 0 && hours > 0 -> "$days 天 $hours 小时"
        days > 0 -> "$days 天"
        hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分"
        hours > 0 -> "$hours 小时"
        else -> "$minutes 分钟"
    }
    return stringResource(R.string.usage_resets_in, text)
}
