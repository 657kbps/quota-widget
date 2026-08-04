package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import kotlin.math.roundToInt

/**
 * Shared usage prefs: optional window-kind chips, used/remaining mode,
 * progress style, and progress bars.
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
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
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
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.usage_progress_style_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UsageWindowChip(
            selected = usageProgressStyle == UsageProgressStyle.BAR,
            label = stringResource(R.string.usage_progress_style_bar),
            enabled = enabled,
            onClick = { onUsageProgressStyleChange(UsageProgressStyle.BAR) },
        )
        UsageWindowChip(
            selected = usageProgressStyle == UsageProgressStyle.CAPSULE,
            label = stringResource(R.string.usage_progress_style_capsule),
            enabled = enabled,
            onClick = { onUsageProgressStyleChange(UsageProgressStyle.CAPSULE) },
        )
    }
    if (windows.isNotEmpty() && overviewKinds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
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
                        usageProgressStyle = usageProgressStyle,
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
    usageProgressStyle: UsageProgressStyle,
) {
    val used = window?.usedPercent ?: 0.0
    val progress = displayUsageFillFraction(used, usageDisplayMode)
    val percentText = formatUsageDisplayPercent(used, usageDisplayMode)
    val nearLimit = isUsageNearLimitForDisplay(used, usageDisplayMode)
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        when (usageProgressStyle) {
            UsageProgressStyle.CAPSULE -> {
                val shape = RoundedCornerShape(8.dp)
                val fillColor = if (nearLimit) scheme.error else scheme.primary
                val trackColor =
                    if (nearLimit) scheme.errorContainer else scheme.primaryContainer
                val contentColor = if (nearLimit) {
                    if (progress < CapsuleTextOnFillThreshold) {
                        scheme.onErrorContainer
                    } else {
                        scheme.onError
                    }
                } else if (progress < CapsuleTextOnFillThreshold) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onPrimary
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(shape)
                        .background(trackColor),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(fillColor),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = capsulePreviewTextStyle(FontWeight.Medium),
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = percentText,
                            style = capsulePreviewTextStyle(FontWeight.Bold),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            UsageProgressStyle.BAR -> {
                val barFill = if (nearLimit) scheme.error else scheme.primary
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
                        text = percentText,
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
                    color = barFill,
                    trackColor = scheme.surfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUsageResetLabel(window?.resetInSec),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

private fun usageWindowKindLabelRes(kind: UsageWindowKind): Int = when (kind) {
    UsageWindowKind.ROLLING -> R.string.usage_window_rolling
    UsageWindowKind.WEEKLY -> R.string.usage_window_weekly
    UsageWindowKind.MONTHLY -> R.string.usage_window_monthly
}

/** Matches overview widget comfortable density (12.sp inside 28.dp bar). */
private fun capsulePreviewTextStyle(weight: FontWeight): TextStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 12.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Prefer on-container text until most of the bar is filled (mirrors Glance capsule). */
private const val CapsuleTextOnFillThreshold = 0.45f

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
