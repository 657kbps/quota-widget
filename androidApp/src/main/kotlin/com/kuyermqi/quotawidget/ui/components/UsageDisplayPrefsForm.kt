package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

private val RESET_VISIBLE_STYLES = listOf(
    UsageResetStyle.FOOTER,
    UsageResetStyle.FOOTER_STACKED,
    UsageResetStyle.PILL,
)

internal val SAMPLE_PREVIEW_WINDOWS = listOf(
    QuotaWindow(
        kind = QuotaWindowKind.FIVE_HOUR,
        usedPercent = 42.0,
        resetInSec = 3L * 3_600 + 12 * 60,
    ),
    QuotaWindow(
        kind = QuotaWindowKind.WEEKLY,
        usedPercent = 68.0,
        resetInSec = 2L * 86_400 + 5 * 3_600,
    ),
    QuotaWindow(
        kind = QuotaWindowKind.MONTHLY,
        usedPercent = 55.0,
        resetInSec = 12L * 86_400,
    ),
    QuotaWindow(
        kind = QuotaWindowKind.TOKEN,
        usedPercent = 55.0,
    ),
)

private val SAMPLE_PREVIEW_OVERVIEW_KINDS = listOf(
    QuotaWindowKind.FIVE_HOUR,
    QuotaWindowKind.WEEKLY,
    QuotaWindowKind.MONTHLY,
)

/**
 * Shared usage prefs: segmented Material 3 selectors for window kind, used/remaining mode,
 * progress style, switch + conditional reset-time style, and live multi-layout widget preview.
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
    usageResetStyle: UsageResetStyle = UsageResetStyle.HIDDEN,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit = {},
    enabled: Boolean,
    windowKindChoices: List<UsageWindowKind>,
    showWindowKindPicker: Boolean,
    overviewKinds: List<QuotaWindowKind>,
    showResetLabels: Boolean = true,
    showProgressStyle: Boolean = true,
    showResetStyle: Boolean = true,
    /** When true, preview percent/fill is always 0% regardless of display mode. */
    forceZeroProgressPreview: Boolean = false,
    platformName: String = "",
    supportsCompactAndOverviewPreview: Boolean = true,
    allowWindowFallback: Boolean = true,
    updatedAtEpochMs: Long = 0L,
    newApiSnapshot: QuotaSnapshot? = null,
) {
    val labelRolling = stringResource(R.string.usage_window_rolling)
    val labelWeekly = stringResource(R.string.usage_window_weekly)
    val labelMonthly = stringResource(R.string.usage_window_monthly)
    val labelToken = stringResource(R.string.usage_window_token)
    val labelUsed = stringResource(R.string.usage_display_used)
    val labelRemaining = stringResource(R.string.usage_display_remaining)
    val labelBar = stringResource(R.string.usage_progress_style_bar)
    val labelCapsule = stringResource(R.string.usage_progress_style_capsule)
    val labelResetFooter = stringResource(R.string.usage_reset_style_footer)
    val labelResetStacked = stringResource(R.string.usage_reset_style_stacked)
    val labelResetPill = stringResource(R.string.usage_reset_style_pill)

    var lastNonHiddenResetStyle by remember {
        mutableStateOf(
            if (usageResetStyle != UsageResetStyle.HIDDEN) {
                usageResetStyle
            } else {
                UsageResetStyle.FOOTER_STACKED
            },
        )
    }
    LaunchedEffect(usageResetStyle) {
        if (usageResetStyle != UsageResetStyle.HIDDEN) {
            lastNonHiddenResetStyle = usageResetStyle
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (showWindowKindPicker && windowKindChoices.isNotEmpty()) {
                    PrefsFieldLabel(stringResource(R.string.usage_widget_window_label))
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedOptionRow(
                        options = windowKindChoices,
                        selectedOption = widgetWindowKind,
                        labelOf = { kind ->
                            when (kind) {
                                UsageWindowKind.ROLLING -> labelRolling
                                UsageWindowKind.WEEKLY -> labelWeekly
                                UsageWindowKind.MONTHLY -> labelMonthly
                                UsageWindowKind.TOKEN -> labelToken
                            }
                        },
                        onOptionSelected = onWidgetWindowKindChange,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                PrefsFieldLabel(stringResource(R.string.usage_display_short_label))
                Spacer(modifier = Modifier.height(6.dp))
                SegmentedOptionRow(
                    options = UsageDisplayMode.entries,
                    selectedOption = usageDisplayMode,
                    labelOf = { mode ->
                        when (mode) {
                            UsageDisplayMode.USED -> labelUsed
                            UsageDisplayMode.REMAINING -> labelRemaining
                        }
                    },
                    onOptionSelected = onUsageDisplayModeChange,
                    enabled = enabled,
                    showIcon = false,
                )
                if (showProgressStyle) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PrefsFieldLabel(stringResource(R.string.usage_progress_style_label))
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedOptionRow(
                        options = UsageProgressStyle.entries,
                        selectedOption = usageProgressStyle,
                        labelOf = { style ->
                            when (style) {
                                UsageProgressStyle.BAR -> labelBar
                                UsageProgressStyle.CAPSULE -> labelCapsule
                            }
                        },
                        onOptionSelected = onUsageProgressStyleChange,
                        enabled = enabled,
                        showIcon = false,
                    )
                }
            }

            if (showResetStyle) {
                val resetEnabled = usageResetStyle != UsageResetStyle.HIDDEN
                val toggleReset = { turnOn: Boolean ->
                    if (turnOn) {
                        onUsageResetStyleChange(lastNonHiddenResetStyle)
                    } else {
                        onUsageResetStyleChange(UsageResetStyle.HIDDEN)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { toggleReset(!resetEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.usage_reset_toggle_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                    Switch(
                        checked = resetEnabled,
                        onCheckedChange = toggleReset,
                        enabled = enabled,
                    )
                }
                AnimatedVisibility(visible = resetEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    ) {
                        SegmentedOptionRow(
                            options = RESET_VISIBLE_STYLES,
                            selectedOption = if (resetEnabled) usageResetStyle else lastNonHiddenResetStyle,
                            labelOf = { style ->
                                when (style) {
                                    UsageResetStyle.FOOTER -> labelResetFooter
                                    UsageResetStyle.FOOTER_STACKED -> labelResetStacked
                                    UsageResetStyle.PILL -> labelResetPill
                                    UsageResetStyle.HIDDEN -> ""
                                }
                            },
                            onOptionSelected = onUsageResetStyleChange,
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }

    val previewWindows = windows.ifEmpty { SAMPLE_PREVIEW_WINDOWS }
    val previewOverviewKinds = overviewKinds.ifEmpty { SAMPLE_PREVIEW_OVERVIEW_KINDS }
    Spacer(modifier = Modifier.height(12.dp))
    WidgetLayoutsPreviewCard(
        platformName = platformName,
        windows = previewWindows,
        widgetWindowKind = widgetWindowKind,
        usageDisplayMode = usageDisplayMode,
        usageProgressStyle = usageProgressStyle,
        usageResetStyle = usageResetStyle,
        overviewKinds = previewOverviewKinds,
        showResetLabels = showResetLabels,
        showProgressStyle = showProgressStyle,
        forceZeroProgressPreview = forceZeroProgressPreview,
        supportsCompactAndOverviewPreview = supportsCompactAndOverviewPreview,
        allowWindowFallback = allowWindowFallback,
        updatedAtEpochMs = updatedAtEpochMs,
        newApiSnapshot = newApiSnapshot,
    )
}

@Composable
private fun PrefsFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
