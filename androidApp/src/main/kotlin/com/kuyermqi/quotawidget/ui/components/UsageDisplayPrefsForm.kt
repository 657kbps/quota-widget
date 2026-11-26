package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressBaseStyle
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.toBaseStyle

private val RESET_VISIBLE_STYLES = listOf(
    UsageResetStyle.FOOTER,
    UsageResetStyle.FOOTER_STACKED,
    UsageResetStyle.PILL,
)

private val COMPACT_RESET_VISIBLE_STYLES = listOf(
    UsageResetStyle.FOOTER,
    UsageResetStyle.PILL,
)

private val PREFS_CONTROL_ROW_MIN_HEIGHT = 40.dp
private val PREFS_LABEL_TO_CONTROL_GAP = 2.dp
private val PREFS_SECTION_GAP = 8.dp

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

internal enum class WidgetConfigTab {
    STANDARD,
    COMPACT,
    OVERVIEW,
}

private data class UsageTabSettingsModel(
    val widgetWindowKind: UsageWindowKind,
    val onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    val usageDisplayMode: UsageDisplayMode,
    val onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    val usageProgressStyle: UsageProgressStyle,
    val onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    val usageResetStyle: UsageResetStyle,
    val onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    val enabled: Boolean,
    val windowKindChoices: List<UsageWindowKind>,
    val showWindowKindPicker: Boolean,
    val showProgressStyle: Boolean,
    val showResetStyle: Boolean,
)

/**
 * Shared usage prefs + live widget preview organized into widget-kind tabs
 * (Standard / Compact / Overview), showing only options that apply to the active tab.
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
    var selectedTab by remember { mutableStateOf(WidgetConfigTab.STANDARD) }
    val tabs = if (supportsCompactAndOverviewPreview) {
        WidgetConfigTab.entries
    } else {
        listOf(WidgetConfigTab.STANDARD)
    }
    val effectiveTab = if (supportsCompactAndOverviewPreview) {
        selectedTab
    } else {
        WidgetConfigTab.STANDARD
    }
    val labelStandard = stringResource(R.string.usage_preview_layout_standard)
    val labelCompact = stringResource(R.string.usage_preview_layout_compact)
    val labelOverview = stringResource(R.string.usage_preview_layout_overview)
    val previewWindows = windows.ifEmpty { SAMPLE_PREVIEW_WINDOWS }
    val previewOverviewKinds = overviewKinds.ifEmpty { SAMPLE_PREVIEW_OVERVIEW_KINDS }

    val settingsModel = UsageTabSettingsModel(
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        usageResetStyle = usageResetStyle,
        onUsageResetStyleChange = onUsageResetStyleChange,
        enabled = enabled,
        windowKindChoices = windowKindChoices,
        showWindowKindPicker = showWindowKindPicker,
        showProgressStyle = showProgressStyle,
        showResetStyle = showResetStyle,
    )
    val previewModel = UsageLayoutPreviewModel(
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
        allowWindowFallback = allowWindowFallback,
        updatedAtEpochMs = updatedAtEpochMs,
        newApiSnapshot = newApiSnapshot,
    )

    WidgetTabPreviewCard(
        tabState = WidgetTabHeaderState(
            tabs = tabs,
            selectedTab = effectiveTab,
            tabLabelOf = { tab ->
                when (tab) {
                    WidgetConfigTab.STANDARD -> labelStandard
                    WidgetConfigTab.COMPACT -> labelCompact
                    WidgetConfigTab.OVERVIEW -> labelOverview
                }
            },
            onTabSelected = { selectedTab = it },
        ),
        widgetModifier = { tab ->
            Modifier.usageLayoutWidgetSize(
                layout = tab,
                overviewKindsCount = previewOverviewKinds.size,
            )
        },
        settingsContent = { tab ->
            UsageTabSettingsContent(tab = tab, model = settingsModel)
            Spacer(modifier = Modifier.height(20.dp))
        },
        previewContent = { tab ->
            UsageLayoutPreviewContent(layout = tab, model = previewModel)
        },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun UsageTabSettingsContent(
    tab: WidgetConfigTab,
    model: UsageTabSettingsModel,
) {
    val labelRolling = stringResource(R.string.usage_window_rolling)
    val labelWeekly = stringResource(R.string.usage_window_weekly)
    val labelMonthly = stringResource(R.string.usage_window_monthly)
    val labelToken = stringResource(R.string.usage_window_token)
    val labelUsed = stringResource(R.string.usage_display_used)
    val labelRemaining = stringResource(R.string.usage_display_remaining)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (tab != WidgetConfigTab.OVERVIEW &&
            model.showWindowKindPicker &&
            model.windowKindChoices.isNotEmpty()
        ) {
            PrefsFieldLabel(stringResource(R.string.usage_widget_window_label))
            Spacer(modifier = Modifier.height(PREFS_LABEL_TO_CONTROL_GAP))
            SegmentedOptionRow(
                options = model.windowKindChoices,
                selectedOption = model.widgetWindowKind,
                labelOf = { kind ->
                    when (kind) {
                        UsageWindowKind.ROLLING -> labelRolling
                        UsageWindowKind.WEEKLY -> labelWeekly
                        UsageWindowKind.MONTHLY -> labelMonthly
                        UsageWindowKind.TOKEN -> labelToken
                    }
                },
                onOptionSelected = model.onWidgetWindowKindChange,
                enabled = model.enabled,
                showIcon = false,
            )
            Spacer(modifier = Modifier.height(PREFS_SECTION_GAP))
        }

        PrefsFieldLabel(stringResource(R.string.usage_display_short_label))
        Spacer(modifier = Modifier.height(PREFS_LABEL_TO_CONTROL_GAP))
        SegmentedOptionRow(
            options = UsageDisplayMode.entries,
            selectedOption = model.usageDisplayMode,
            labelOf = { mode ->
                when (mode) {
                    UsageDisplayMode.USED -> labelUsed
                    UsageDisplayMode.REMAINING -> labelRemaining
                }
            },
            onOptionSelected = model.onUsageDisplayModeChange,
            enabled = model.enabled,
            showIcon = false,
        )

        if (model.showProgressStyle && tab != WidgetConfigTab.COMPACT) {
            Spacer(modifier = Modifier.height(PREFS_SECTION_GAP))
            when (tab) {
                WidgetConfigTab.STANDARD -> ProgressStyleSelector(
                    usageProgressStyle = model.usageProgressStyle,
                    onUsageProgressStyleChange = model.onUsageProgressStyleChange,
                    enabled = model.enabled,
                )
                WidgetConfigTab.OVERVIEW -> LinearProgressStyleSelector(
                    usageProgressStyle = model.usageProgressStyle,
                    onUsageProgressStyleChange = model.onUsageProgressStyleChange,
                    enabled = model.enabled,
                )
                WidgetConfigTab.COMPACT -> Unit
            }
        }

        if (model.showResetStyle) {
            val resetStyleOptions = when (tab) {
                WidgetConfigTab.STANDARD -> RESET_VISIBLE_STYLES
                WidgetConfigTab.COMPACT -> COMPACT_RESET_VISIBLE_STYLES
                WidgetConfigTab.OVERVIEW -> emptyList()
            }
            Spacer(modifier = Modifier.height(PREFS_SECTION_GAP))
            ResetTimeSelector(
                usageResetStyle = model.usageResetStyle,
                onUsageResetStyleChange = model.onUsageResetStyleChange,
                styleOptions = resetStyleOptions,
                enabled = model.enabled,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ResetTimeSelector(
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    styleOptions: List<UsageResetStyle>,
    enabled: Boolean,
) {
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
    val resetEnabled = usageResetStyle != UsageResetStyle.HIDDEN
    val toggleReset = { turnOn: Boolean ->
        val restored = if (styleOptions.isNotEmpty() && lastNonHiddenResetStyle !in styleOptions) {
            styleOptions.first()
        } else {
            lastNonHiddenResetStyle
        }
        onUsageResetStyleChange(if (turnOn) restored else UsageResetStyle.HIDDEN)
    }
    CompactSwitchRow(
        title = stringResource(R.string.usage_reset_toggle_title),
        checked = resetEnabled,
        enabled = enabled,
        isSectionHeader = true,
        onCheckedChange = toggleReset,
    )
    if (styleOptions.isNotEmpty()) {
        val activeStyle = if (resetEnabled) usageResetStyle else lastNonHiddenResetStyle
        val selectedOption = activeStyle.takeIf { it in styleOptions } ?: styleOptions.first()
        ResetStyleOptionsRow(
            styleOptions = styleOptions,
            selectedOption = selectedOption,
            visible = resetEnabled,
            enabled = enabled,
            onOptionSelected = onUsageResetStyleChange,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ResetStyleOptionsRow(
    styleOptions: List<UsageResetStyle>,
    selectedOption: UsageResetStyle,
    visible: Boolean,
    enabled: Boolean,
    onOptionSelected: (UsageResetStyle) -> Unit,
) {
    val labelResetFooter = stringResource(R.string.usage_reset_style_footer)
    val labelResetStacked = stringResource(R.string.usage_reset_style_stacked)
    val labelResetPill = stringResource(R.string.usage_reset_style_pill)
    key(styleOptions) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(PREFS_LABEL_TO_CONTROL_GAP))
                SegmentedOptionRow(
                    options = styleOptions,
                    selectedOption = selectedOption,
                    labelOf = { style ->
                        when (style) {
                            UsageResetStyle.FOOTER -> labelResetFooter
                            UsageResetStyle.FOOTER_STACKED -> labelResetStacked
                            UsageResetStyle.PILL -> labelResetPill
                            UsageResetStyle.HIDDEN -> ""
                        }
                    },
                    onOptionSelected = onOptionSelected,
                    enabled = enabled,
                    showIcon = false,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun LinearProgressStyleSelector(
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    enabled: Boolean,
) {
    val labelBar = stringResource(R.string.usage_progress_style_bar)
    val labelCapsule = stringResource(R.string.usage_progress_style_capsule)
    PrefsFieldLabel(stringResource(R.string.usage_progress_style_label))
    Spacer(modifier = Modifier.height(PREFS_LABEL_TO_CONTROL_GAP))
    SegmentedOptionRow(
        options = listOf(UsageProgressStyle.BAR, UsageProgressStyle.CAPSULE),
        selectedOption = usageProgressStyle.toLinearFallback(),
        labelOf = { style ->
            when (style) {
                UsageProgressStyle.CAPSULE -> labelCapsule
                else -> labelBar
            }
        },
        onOptionSelected = onUsageProgressStyleChange,
        enabled = enabled,
        showIcon = false,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ProgressStyleSelector(
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    enabled: Boolean,
) {
    val labelBar = stringResource(R.string.usage_progress_style_bar)
    val labelCapsule = stringResource(R.string.usage_progress_style_capsule)
    val labelRing = stringResource(R.string.usage_progress_style_ring)
    val labelGauge = stringResource(R.string.usage_progress_style_gauge)
    var lastSegmented by remember { mutableStateOf(usageProgressStyle.isSegmented) }
    LaunchedEffect(usageProgressStyle) {
        if (usageProgressStyle.isCircular) {
            lastSegmented = usageProgressStyle.isSegmented
        }
    }

    PrefsFieldLabel(stringResource(R.string.usage_progress_style_label))
    Spacer(modifier = Modifier.height(PREFS_LABEL_TO_CONTROL_GAP))
    SegmentedOptionRow(
        options = UsageProgressBaseStyle.entries,
        selectedOption = usageProgressStyle.toBaseStyle(),
        labelOf = { base ->
            when (base) {
                UsageProgressBaseStyle.BAR -> labelBar
                UsageProgressBaseStyle.CAPSULE -> labelCapsule
                UsageProgressBaseStyle.RING -> labelRing
                UsageProgressBaseStyle.GAUGE -> labelGauge
            }
        },
        onOptionSelected = { base ->
            onUsageProgressStyleChange(base.toProgressStyle(lastSegmented))
        },
        enabled = enabled,
        showIcon = false,
    )
    AnimatedVisibility(
        visible = usageProgressStyle.isCircular,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        val checked = usageProgressStyle.isSegmented
        CompactSwitchRow(
            title = stringResource(R.string.usage_progress_segmented_toggle_title),
            checked = checked,
            enabled = enabled,
            isSectionHeader = false,
            modifier = Modifier.padding(top = 6.dp),
            onCheckedChange = { segmented ->
                onUsageProgressStyleChange(usageProgressStyle.withSegmented(segmented))
            },
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    isSectionHeader: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = if (isSectionHeader) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    }
    val color = if (isSectionHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PREFS_CONTROL_ROW_MIN_HEIGHT)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun PrefsFieldLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PREFS_CONTROL_ROW_MIN_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
