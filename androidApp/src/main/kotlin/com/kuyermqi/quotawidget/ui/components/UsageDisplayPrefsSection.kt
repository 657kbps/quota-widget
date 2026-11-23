package com.kuyermqi.quotawidget.ui.components

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatNewApiUsageWidgetTitle
import com.kuyermqi.quotawidget.domain.formatNewApiWidgetFooter
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.newApiUsageProgressDisplayMode
import com.kuyermqi.quotawidget.domain.newApiUsageProgressUsedPercent
import com.kuyermqi.quotawidget.domain.newApiUsageWidgetShowsProgress
import com.kuyermqi.quotawidget.domain.resetAtEpochMs
import com.kuyermqi.quotawidget.domain.resolveCodexUsageSummaryWindowKind
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import com.kuyermqi.quotawidget.domain.usage.usageWindowModeLabelRes
import com.kuyermqi.quotawidget.widget.ResetMoment
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.balanceTitleFontSize
import com.kuyermqi.quotawidget.widget.compactBalanceTitleFontSize

private enum class WidgetPreviewLayout {
    STANDARD,
    COMPACT,
    OVERVIEW,
}

private val RESET_VISIBLE_STYLES = listOf(
    UsageResetStyle.FOOTER,
    UsageResetStyle.FOOTER_STACKED,
    UsageResetStyle.PILL,
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
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

                if (showProgressStyle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        }
                        Column(modifier = Modifier.weight(1f)) {
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
                } else {
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

    val hasPreviewContent = windows.isNotEmpty() &&
        (overviewKinds.isNotEmpty() || !supportsCompactAndOverviewPreview)
    if (hasPreviewContent) {
        Spacer(modifier = Modifier.height(12.dp))
        WidgetLayoutsPreviewCard(
            platformName = platformName,
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            usageDisplayMode = usageDisplayMode,
            usageProgressStyle = usageProgressStyle,
            usageResetStyle = usageResetStyle,
            overviewKinds = overviewKinds,
            showResetLabels = showResetLabels,
            showProgressStyle = showProgressStyle,
            forceZeroProgressPreview = forceZeroProgressPreview,
            supportsCompactAndOverviewPreview = supportsCompactAndOverviewPreview,
            allowWindowFallback = allowWindowFallback,
            updatedAtEpochMs = updatedAtEpochMs,
            newApiSnapshot = newApiSnapshot,
        )
    }
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

@Composable
private fun WidgetLayoutsPreviewCard(
    platformName: String,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
    overviewKinds: List<QuotaWindowKind>,
    showResetLabels: Boolean,
    showProgressStyle: Boolean,
    forceZeroProgressPreview: Boolean,
    supportsCompactAndOverviewPreview: Boolean,
    allowWindowFallback: Boolean,
    updatedAtEpochMs: Long,
    newApiSnapshot: QuotaSnapshot?,
) {
    val context = LocalContext.current
    var selectedLayout by remember { mutableStateOf(WidgetPreviewLayout.STANDARD) }
    val effectiveLayout = if (supportsCompactAndOverviewPreview) {
        selectedLayout
    } else {
        WidgetPreviewLayout.STANDARD
    }

    val labelStandard = stringResource(R.string.usage_preview_layout_standard)
    val labelCompact = stringResource(R.string.usage_preview_layout_compact)
    val labelOverview = stringResource(R.string.usage_preview_layout_overview)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.usage_preview_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                if (supportsCompactAndOverviewPreview) {
                    SegmentedOptionRow(
                        options = WidgetPreviewLayout.entries,
                        selectedOption = selectedLayout,
                        labelOf = { layout ->
                            when (layout) {
                                WidgetPreviewLayout.STANDARD -> labelStandard
                                WidgetPreviewLayout.COMPACT -> labelCompact
                                WidgetPreviewLayout.OVERVIEW -> labelOverview
                            }
                        },
                        onOptionSelected = { selectedLayout = it },
                        showIcon = false,
                        minHeight = 32.dp,
                        modifier = Modifier.widthIn(min = 144.dp, max = 184.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            val updatedLabel = if (updatedAtEpochMs > 0L) {
                stringResource(
                    R.string.widget_updated_at,
                    WidgetDateFormatter.formatUpdatedAt(context, updatedAtEpochMs),
                )
            } else {
                stringResource(R.string.usage_preview_updated_sample)
            }
            val widgetModifier = when (effectiveLayout) {
                WidgetPreviewLayout.STANDARD -> Modifier
                    .widthIn(max = 184.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                WidgetPreviewLayout.COMPACT -> Modifier
                    .widthIn(max = 184.dp)
                    .fillMaxWidth()
                    .height(92.dp)
                WidgetPreviewLayout.OVERVIEW -> Modifier
                    .widthIn(max = 184.dp)
                    .fillMaxWidth()
                    .height(if (overviewKinds.size >= 3) 208.dp else 184.dp)
            }
            val wallpaperColors = rememberWallpaperBackdropColors()
            val fallbackStageColor = MaterialTheme.colorScheme.surfaceContainerLow
            val stageModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .let { base ->
                    if (!wallpaperColors.isNullOrEmpty()) {
                        val gradientColors = if (wallpaperColors.size == 1) {
                            listOf(
                                wallpaperColors[0],
                                lerp(wallpaperColors[0], fallbackStageColor, 0.35f),
                            )
                        } else {
                            wallpaperColors
                        }
                        base.background(
                            brush = Brush.linearGradient(
                                colors = gradientColors,
                                start = Offset.Zero,
                                end = Offset(900f, 600f),
                            ),
                        )
                    } else {
                        base.background(fallbackStageColor)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 14.dp)

            val widgetCornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dimensionResource(android.R.dimen.system_app_widget_background_radius)
            } else {
                16.dp
            }
            val widgetBackgroundColor = rememberWidgetBackgroundColor()

            Box(
                modifier = stageModifier,
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = widgetModifier,
                    shape = RoundedCornerShape(widgetCornerRadius),
                    color = widgetBackgroundColor,
                ) {
                    CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
                        when (effectiveLayout) {
                            WidgetPreviewLayout.STANDARD -> StandardWidgetPreview(
                                platformName = platformName,
                                windows = windows,
                                widgetWindowKind = widgetWindowKind,
                                usageDisplayMode = usageDisplayMode,
                                usageProgressStyle = usageProgressStyle,
                                usageResetStyle = usageResetStyle,
                                showResetLabels = showResetLabels,
                                showProgressStyle = showProgressStyle,
                                forceZeroProgressPreview = forceZeroProgressPreview,
                                allowWindowFallback = allowWindowFallback,
                                updatedAtEpochMs = updatedAtEpochMs,
                                updatedLabel = updatedLabel,
                                newApiSnapshot = newApiSnapshot,
                            )
                            WidgetPreviewLayout.COMPACT -> CompactWidgetPreview(
                                windows = windows,
                                widgetWindowKind = widgetWindowKind,
                                usageDisplayMode = usageDisplayMode,
                                usageResetStyle = usageResetStyle,
                                showResetLabels = showResetLabels,
                                forceZeroProgressPreview = forceZeroProgressPreview,
                                allowWindowFallback = allowWindowFallback,
                                updatedAtEpochMs = updatedAtEpochMs,
                                updatedLabel = updatedLabel,
                            )
                            WidgetPreviewLayout.OVERVIEW -> OverviewWidgetPreview(
                                platformName = platformName,
                                windows = windows,
                                overviewKinds = overviewKinds,
                                usageDisplayMode = usageDisplayMode,
                                usageProgressStyle = usageProgressStyle,
                                usageResetStyle = usageResetStyle,
                                showResetLabels = showResetLabels,
                                forceZeroProgressPreview = forceZeroProgressPreview,
                                updatedAtEpochMs = updatedAtEpochMs,
                                updatedLabel = updatedLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardWidgetPreview(
    platformName: String,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
    showResetLabels: Boolean,
    showProgressStyle: Boolean,
    forceZeroProgressPreview: Boolean,
    allowWindowFallback: Boolean,
    updatedAtEpochMs: Long,
    updatedLabel: String,
    newApiSnapshot: QuotaSnapshot?,
) {
    val context = LocalContext.current
    val resolvedKind = resolvePreviewWindowKind(windows, widgetWindowKind, allowWindowFallback)
    val window = windows.find { it.kind == resolvedKind.toQuotaWindowKind() }
    val used = if (forceZeroProgressPreview) 0.0 else window?.usedPercent
    val percentText = when {
        newApiSnapshot != null ->
            formatNewApiUsageWidgetTitle(newApiSnapshot, usageDisplayMode)
                ?: stringResource(R.string.usage_unavailable)
        forceZeroProgressPreview -> formatUsageDisplayPercent(0.0, UsageDisplayMode.USED)
        else -> used?.let { formatUsageDisplayPercent(it, usageDisplayMode) }
            ?: stringResource(R.string.usage_unavailable)
    }
    val showProgress = showProgressStyle && if (newApiSnapshot != null) {
        newApiUsageWidgetShowsProgress(newApiSnapshot)
    } else {
        used != null
    }
    val progressUsed = if (newApiSnapshot != null) {
        newApiUsageProgressUsedPercent(newApiSnapshot, usageDisplayMode)
    } else {
        used
    }
    val progressMode = when {
        newApiSnapshot != null -> newApiUsageProgressDisplayMode(newApiSnapshot, usageDisplayMode)
        forceZeroProgressPreview -> UsageDisplayMode.USED
        else -> usageDisplayMode
    }
    val fillFraction = progressUsed?.let { displayUsageFillFraction(it, progressMode) } ?: 0f
    val nearLimit = progressUsed?.let { isUsageNearLimitForDisplay(it, progressMode) } ?: false

    val nowMs = remember(updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetAtMs = if (showResetLabels && used != null) {
        resolvePreviewResetEpochMs(window, updatedAtEpochMs, nowMs)
    } else {
        null
    }
    val resetAtLabel = resetAtMs
        ?.let { WidgetDateFormatter.resetMoment(context, it, nowMs) }
        ?.let { formatResetAtLabel(it) }
    val pillText = resetAtMs
        ?.takeIf { usageResetStyle == UsageResetStyle.PILL }
        ?.let { formatUsageResetCountdown(it - nowMs) }
    val baseFontSize = balanceTitleFontSize(percentText)
    val percentFontSize = if (pillText != null) {
        minOf(26f, baseFontSize.value).sp
    } else {
        baseFontSize
    }
    val scheme = MaterialTheme.colorScheme
    val defaultFooter = if (newApiSnapshot != null) {
        formatNewApiWidgetFooter(
            snapshot = newApiSnapshot,
            expiredLabel = stringResource(R.string.new_api_token_expired),
            updatedAtText = updatedLabel,
        )
    } else {
        updatedLabel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
    ) {
        PreviewWidgetHeader(
            title = platformName.ifBlank { stringResource(usageWindowLabelRes(resolvedKind.toQuotaWindowKind())) },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (pillText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = percentText,
                    style = previewTextStyle(percentFontSize, FontWeight.Bold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = scheme.secondaryContainer,
                ) {
                    Text(
                        text = pillText,
                        style = previewTextStyle(11.sp, FontWeight.Medium, lineHeight = 13.sp),
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        } else {
            Text(
                text = percentText,
                style = previewTextStyle(percentFontSize, FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
            )
        }
        if (showProgress && progressUsed != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val fillColor = if (nearLimit) scheme.error else scheme.primary
            when (usageProgressStyle) {
                UsageProgressStyle.BAR -> LinearProgressIndicator(
                    progress = { fillFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = fillColor,
                    trackColor = scheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                UsageProgressStyle.CAPSULE -> {
                    val trackColor =
                        if (nearLimit) scheme.errorContainer else scheme.primaryContainer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(trackColor),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                                .background(fillColor),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
        when {
            resetAtLabel != null && usageResetStyle == UsageResetStyle.FOOTER -> Text(
                text = resetAtLabel,
                style = previewTextStyle(12.sp, lineHeight = 14.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            resetAtLabel != null && usageResetStyle == UsageResetStyle.FOOTER_STACKED -> {
                Text(
                    text = resetAtLabel,
                    style = previewTextStyle(12.5.sp, FontWeight.Medium, lineHeight = 15.sp),
                    color = scheme.primary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = defaultFooter,
                    style = previewTextStyle(10.sp, lineHeight = 12.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            else -> Text(
                text = defaultFooter,
                style = previewTextStyle(12.sp, lineHeight = 14.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactWidgetPreview(
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageResetStyle: UsageResetStyle,
    showResetLabels: Boolean,
    forceZeroProgressPreview: Boolean,
    allowWindowFallback: Boolean,
    updatedAtEpochMs: Long,
    updatedLabel: String,
) {
    val context = LocalContext.current
    val resolvedKind = resolvePreviewWindowKind(windows, widgetWindowKind, allowWindowFallback)
    val window = windows.find { it.kind == resolvedKind.toQuotaWindowKind() }
    val used = if (forceZeroProgressPreview) 0.0 else window?.usedPercent
    val mode = if (forceZeroProgressPreview) UsageDisplayMode.USED else usageDisplayMode
    val percentText = used?.let { formatUsageDisplayPercent(it, mode) }
        ?: stringResource(R.string.usage_unavailable)

    val nowMs = remember(updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetAtMs = if (showResetLabels && used != null && usageResetStyle != UsageResetStyle.HIDDEN) {
        resolvePreviewResetEpochMs(window, updatedAtEpochMs, nowMs)
    } else {
        null
    }
    val resetAtLabel = resetAtMs
        ?.let { WidgetDateFormatter.resetMoment(context, it, nowMs) }
        ?.let { formatResetAtLabel(it) }
    val footerText = resetAtLabel ?: updatedLabel
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val compactPercentSize = compactBalanceTitleFontSize(percentText)
            Text(
                text = percentText,
                style = previewTextStyle(compactPercentSize, FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val footerSize = if (resetAtLabel != null) 10.sp else 11.sp
            Text(
                text = footerText,
                style = previewTextStyle(footerSize, lineHeight = footerSize * 1.2f),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun OverviewWidgetPreview(
    platformName: String,
    windows: List<QuotaWindow>,
    overviewKinds: List<QuotaWindowKind>,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
    showResetLabels: Boolean,
    forceZeroProgressPreview: Boolean,
    updatedAtEpochMs: Long,
    updatedLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
    ) {
        if (platformName.isNotBlank()) {
            PreviewWidgetHeader(title = platformName)
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        overviewKinds.forEachIndexed { index, kind ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            UsageWindowBar(
                label = stringResource(
                    R.string.usage_window_label_with_mode,
                    stringResource(usageWindowLabelRes(kind)),
                    stringResource(usageWindowModeLabelRes(usageDisplayMode)),
                ),
                window = windows.find { it.kind == kind },
                usageDisplayMode = usageDisplayMode,
                usageProgressStyle = usageProgressStyle,
                showResetLabel = showResetLabels &&
                    usageResetStyle != UsageResetStyle.HIDDEN,
                updatedAtEpochMs = updatedAtEpochMs,
                forceZeroProgress = forceZeroProgressPreview,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = updatedLabel,
            style = previewTextStyle(12.sp, lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewWidgetHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = previewTextStyle(15.sp, FontWeight.Medium, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun UsageWindowBar(
    label: String,
    window: QuotaWindow?,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    showResetLabel: Boolean,
    updatedAtEpochMs: Long,
    forceZeroProgress: Boolean = false,
) {
    val used = if (forceZeroProgress) 0.0 else window?.usedPercent
    val mode = if (forceZeroProgress) UsageDisplayMode.USED else usageDisplayMode
    val progress = used?.let { displayUsageFillFraction(it, mode) } ?: 0f
    val percentText = used?.let { formatUsageDisplayPercent(it, mode) }
        ?: stringResource(R.string.usage_unavailable)
    val nearLimit = used?.let { isUsageNearLimitForDisplay(it, mode) } ?: false
    val nowMs = remember(updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetText = if (showResetLabel && used != null) {
        resolvePreviewResetEpochMs(window, updatedAtEpochMs, nowMs)
            ?.let { formatUsageResetCountdown(it - nowMs) }
    } else {
        null
    }
    val labelText = if (resetText != null) "$label · $resetText" else label
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
                            text = labelText,
                            style = capsulePreviewTextStyle(FontWeight.Medium),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labelText,
                        style = previewTextStyle(12.sp, FontWeight.Medium, lineHeight = 14.sp),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = percentText,
                        style = previewTextStyle(12.sp, FontWeight.Bold, lineHeight = 14.sp),
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = barFill,
                    trackColor = scheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

private fun resolvePreviewWindowKind(
    windows: List<QuotaWindow>,
    preferred: UsageWindowKind,
    allowFallback: Boolean,
): UsageWindowKind {
    if (!allowFallback) return preferred
    return resolveCodexUsageSummaryWindowKind(windows, preferred) ?: preferred
}

private fun resolvePreviewResetEpochMs(
    window: QuotaWindow?,
    updatedAtEpochMs: Long,
    nowMs: Long,
): Long? {
    val w = window ?: return null
    if (updatedAtEpochMs > 0L) {
        return w.resetAtEpochMs(updatedAtEpochMs)?.takeIf { it > nowMs }
    }
    val seconds = w.resetInSec?.takeIf { it > 0L } ?: return null
    return nowMs + seconds * 1_000L
}

@Composable
private fun formatResetAtLabel(moment: ResetMoment): String = when (moment) {
    is ResetMoment.Today -> stringResource(R.string.widget_resets_at, moment.time)
    is ResetMoment.Tomorrow ->
        stringResource(R.string.widget_resets_at_tomorrow, moment.time)
    is ResetMoment.Later ->
        stringResource(R.string.widget_resets_at_date, moment.date, moment.time)
}

private fun previewTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = fontSize,
): TextStyle = TextStyle(
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Matches overview widget comfortable density (12.sp inside 28.dp bar). */
private fun capsulePreviewTextStyle(weight: FontWeight): TextStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 12.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Prefer on-container text until most of the bar is filled (mirrors Glance capsule). */
private const val CapsuleTextOnFillThreshold = 0.45f

/**
 * Mirrors Glance Material3 [androidx.glance.material3.ColorProviders] widgetBackground
 * tone adjustment on [secondaryContainer].
 */
@Composable
private fun rememberWidgetBackgroundColor(): Color {
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    return remember(secondaryContainer) {
        val hct = FloatArray(3)
        ColorUtils.colorToM3HCT(secondaryContainer.toArgb(), hct)
        val adjustment = if (hct[2] > 50f) 5f else -10f
        val adjustedTone = (hct[2] + adjustment).coerceIn(0f, 100f)
        Color(ColorUtils.M3HCTToColor(hct[0], hct[1], adjustedTone))
    }
}

/**
 * Reads the current system wallpaper's extracted palette via permission-free
 * [WallpaperManager.getWallpaperColors] (API 27+) so the preview stage reflects
 * the user's wallpaper tones without requesting storage permissions.
 */
@Composable
private fun rememberWallpaperBackdropColors(): List<Color>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    val context = LocalContext.current
    val colors by produceState<List<Color>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()?.let { wc ->
                buildList {
                    add(Color(wc.primaryColor.toArgb()))
                    wc.secondaryColor?.let { add(Color(it.toArgb())) }
                    wc.tertiaryColor?.let { add(Color(it.toArgb())) }
                }
            }
        }
    }
    return colors
}
