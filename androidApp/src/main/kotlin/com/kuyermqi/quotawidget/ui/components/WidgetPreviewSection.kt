package com.kuyermqi.quotawidget.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kuyermqi.quotawidget.domain.usage.overviewWindowLabel
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.balanceTitleFontSize
import com.kuyermqi.quotawidget.widget.compactBalanceTitleFontSize

private enum class WidgetPreviewLayout {
    STANDARD,
    COMPACT,
    OVERVIEW,
}

@Composable
internal fun <T> WidgetPreviewCard(
    options: List<T>,
    selectedOption: T,
    labelOf: (T) -> String,
    onOptionSelected: (T) -> Unit,
    widgetModifier: (T) -> Modifier,
    content: @Composable (T) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.usage_preview_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedOptionRow(
                    options = options,
                    selectedOption = selectedOption,
                    labelOf = labelOf,
                    onOptionSelected = onOptionSelected,
                    showIcon = false,
                    minHeight = 32.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

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
                    modifier = widgetModifier(selectedOption),
                    shape = RoundedCornerShape(widgetCornerRadius),
                    color = widgetBackgroundColor,
                ) {
                    CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
                        content(selectedOption)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WidgetLayoutsPreviewCard(
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
    val updatedLabel = if (updatedAtEpochMs > 0L) {
        stringResource(
            R.string.widget_updated_at,
            WidgetDateFormatter.formatUpdatedAt(context, updatedAtEpochMs),
        )
    } else {
        stringResource(R.string.usage_preview_updated_sample)
    }

    WidgetPreviewCard(
        options = if (supportsCompactAndOverviewPreview) {
            WidgetPreviewLayout.entries
        } else {
            emptyList()
        },
        selectedOption = effectiveLayout,
        labelOf = { layout ->
            when (layout) {
                WidgetPreviewLayout.STANDARD -> labelStandard
                WidgetPreviewLayout.COMPACT -> labelCompact
                WidgetPreviewLayout.OVERVIEW -> labelOverview
            }
        },
        onOptionSelected = { selectedLayout = it },
        widgetModifier = { layout ->
            when (layout) {
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
        },
    ) { layout ->
        when (layout) {
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
    val resources = LocalResources.current
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
                label = overviewWindowLabel(resources, kind),
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
