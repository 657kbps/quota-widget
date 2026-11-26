package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatNewApiUsageWidgetTitle
import com.kuyermqi.quotawidget.domain.formatNewApiWidgetFooter
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.newApiUsageProgressDisplayMode
import com.kuyermqi.quotawidget.domain.newApiUsageProgressUsedPercent
import com.kuyermqi.quotawidget.domain.newApiUsageWidgetShowsProgress
import com.kuyermqi.quotawidget.domain.usage.usageWindowLabelRes
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.balanceTitleFontSize

private const val PILL_PERCENT_MAX_SP = 26f

private data class StandardPreviewSpec(
    val title: String,
    val percentText: String,
    val percentFontSize: TextUnit,
    val showProgress: Boolean,
    val progressUsed: Double?,
    val fillFraction: Float,
    val nearLimit: Boolean,
    val isCircular: Boolean,
    val resetAtLabel: String?,
    val pillText: String?,
    val defaultFooter: String,
    val hasFooter: Boolean,
)

@Suppress("FunctionNaming")
@Composable
internal fun StandardWidgetPreview(
    model: UsageLayoutPreviewModel,
    updatedLabel: String,
    compactUpdatedLabel: String,
) {
    val spec = rememberStandardPreviewSpec(
        model = model,
        updatedLabel = updatedLabel,
        compactUpdatedLabel = compactUpdatedLabel,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 14.dp,
                top = 14.dp,
                end = 14.dp,
                bottom = if (spec.isCircular) 14.dp else 22.dp,
            ),
    ) {
        PreviewWidgetHeader(title = spec.title)
        if (spec.isCircular) {
            StandardCircularWidgetPreview(
                model = CircularPreviewModel(
                    percentText = spec.percentText,
                    fillFraction = spec.fillFraction,
                    nearLimit = spec.nearLimit,
                    usageProgressStyle = model.usageProgressStyle,
                    usageResetStyle = model.usageResetStyle,
                    resetAtLabel = spec.resetAtLabel,
                    pillText = spec.pillText,
                    defaultFooter = spec.defaultFooter,
                ),
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        Spacer(modifier = Modifier.weight(1f))
        StandardLinearPreviewTitle(
            percentText = spec.percentText,
            percentFontSize = spec.percentFontSize,
            pillText = spec.pillText,
        )
        if (spec.showProgress && spec.progressUsed != null) {
            Spacer(modifier = Modifier.height(8.dp))
            StandardLinearPreviewProgress(
                usageProgressStyle = model.usageProgressStyle,
                fillFraction = spec.fillFraction,
                nearLimit = spec.nearLimit,
            )
            if (spec.hasFooter) Spacer(modifier = Modifier.height(10.dp))
        } else if (spec.hasFooter) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        StandardLinearPreviewFooter(
            usageResetStyle = model.usageResetStyle,
            resetAtLabel = spec.resetAtLabel,
            defaultFooter = spec.defaultFooter,
        )
    }
}

@Composable
private fun rememberStandardPreviewSpec(
    model: UsageLayoutPreviewModel,
    updatedLabel: String,
    compactUpdatedLabel: String,
): StandardPreviewSpec {
    val context = LocalContext.current
    val resolvedKind = resolvePreviewWindowKind(
        model.windows,
        model.widgetWindowKind,
        model.allowWindowFallback,
    )
    val window = model.windows.find { it.kind == resolvedKind.toQuotaWindowKind() }
    val used = if (model.forceZeroProgressPreview) 0.0 else window?.usedPercent
    val newApi = model.newApiSnapshot
    val percentText = resolveStandardPercentText(newApi, model.usageDisplayMode, model.forceZeroProgressPreview, used)
    val showProgress = model.showProgressStyle && (newApi?.let(::newApiUsageWidgetShowsProgress) ?: (used != null))
    val progressUsed = newApi?.let { newApiUsageProgressUsedPercent(it, model.usageDisplayMode) } ?: used
    val progressMode = when {
        newApi != null -> newApiUsageProgressDisplayMode(newApi, model.usageDisplayMode)
        model.forceZeroProgressPreview -> UsageDisplayMode.USED
        else -> model.usageDisplayMode
    }
    val fillFraction = progressUsed?.let { displayUsageFillFraction(it, progressMode) } ?: 0f
    val nearLimit = progressUsed?.let { isUsageNearLimitForDisplay(it, progressMode) } ?: false
    val isCircular = showProgress && progressUsed != null && model.usageProgressStyle.isCircular
    val nowMs = remember(model.updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetAtMs = if (model.showResetLabels && used != null) {
        resolvePreviewResetEpochMs(window, model.updatedAtEpochMs, nowMs)
    } else {
        null
    }
    val resetAtLabel = resetAtMs
        ?.let { WidgetDateFormatter.resetMoment(context, it, nowMs, circular = isCircular) }
        ?.let { formatResetAtLabel(it, circular = isCircular) }
    val pillText = resetAtMs
        ?.takeIf { model.usageResetStyle == UsageResetStyle.PILL }
        ?.let { formatUsageResetCountdown(it - nowMs) }
    val baseFontSize = balanceTitleFontSize(percentText)
    val defaultFooter = resolveStandardDefaultFooter(
        newApi = newApi,
        updatedLabel = if (isCircular) compactUpdatedLabel else updatedLabel,
    )
    return StandardPreviewSpec(
        title = model.platformName.ifBlank {
            stringResource(usageWindowLabelRes(resolvedKind.toQuotaWindowKind()))
        },
        percentText = percentText,
        percentFontSize = if (pillText != null) minOf(PILL_PERCENT_MAX_SP, baseFontSize.value).sp else baseFontSize,
        showProgress = showProgress,
        progressUsed = progressUsed,
        fillFraction = fillFraction,
        nearLimit = nearLimit,
        isCircular = isCircular,
        resetAtLabel = resetAtLabel,
        pillText = pillText,
        defaultFooter = defaultFooter,
        hasFooter = hasStandardFooter(resetAtLabel, model.usageResetStyle, defaultFooter),
    )
}

private fun hasStandardFooter(
    resetAtLabel: String?,
    usageResetStyle: UsageResetStyle,
    defaultFooter: String,
): Boolean = (resetAtLabel != null &&
    (usageResetStyle == UsageResetStyle.FOOTER || usageResetStyle == UsageResetStyle.FOOTER_STACKED)) ||
    defaultFooter.isNotBlank()

@Composable
private fun resolveStandardDefaultFooter(
    newApi: QuotaSnapshot?,
    updatedLabel: String,
): String = if (newApi != null) {
    formatNewApiWidgetFooter(
        snapshot = newApi,
        expiredLabel = stringResource(R.string.new_api_token_expired),
        updatedAtText = updatedLabel,
    )
} else {
    updatedLabel
}

@Composable
private fun resolveStandardPercentText(
    newApi: QuotaSnapshot?,
    displayMode: UsageDisplayMode,
    forceZeroProgressPreview: Boolean,
    used: Double?,
): String = when {
    newApi != null ->
        formatNewApiUsageWidgetTitle(newApi, displayMode)
            ?: stringResource(R.string.usage_unavailable)
    forceZeroProgressPreview -> formatUsageDisplayPercent(0.0, UsageDisplayMode.USED)
    else -> used?.let { formatUsageDisplayPercent(it, displayMode) }
        ?: stringResource(R.string.usage_unavailable)
}

@Suppress("FunctionNaming")
@Composable
private fun StandardLinearPreviewTitle(
    percentText: String,
    percentFontSize: TextUnit,
    pillText: String?,
) {
    val scheme = MaterialTheme.colorScheme
    if (pillText != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = percentText,
                style = previewTextStyle(percentFontSize, FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(6.dp))
            PreviewResetPill(text = pillText)
        }
    } else {
        Text(
            text = percentText,
            style = previewTextStyle(percentFontSize, FontWeight.Bold),
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StandardLinearPreviewProgress(
    usageProgressStyle: UsageProgressStyle,
    fillFraction: Float,
    nearLimit: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val fillColor = if (nearLimit) scheme.error else scheme.primary
    when (usageProgressStyle.toLinearFallback()) {
        UsageProgressStyle.BAR -> LinearProgressIndicator(
            progress = { fillFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = fillColor,
            trackColor = previewLinearTrackColor(),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        UsageProgressStyle.CAPSULE -> {
            val trackColor = if (nearLimit) scheme.errorContainer else scheme.primaryContainer
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
        UsageProgressStyle.RING,
        UsageProgressStyle.RING_SEGMENTED,
        UsageProgressStyle.GAUGE,
        UsageProgressStyle.GAUGE_SEGMENTED,
        -> Unit
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StandardLinearPreviewFooter(
    usageResetStyle: UsageResetStyle,
    resetAtLabel: String?,
    defaultFooter: String,
) {
    val scheme = MaterialTheme.colorScheme
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
            if (defaultFooter.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = defaultFooter,
                    style = previewTextStyle(10.sp, lineHeight = 12.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        defaultFooter.isNotBlank() -> Text(
            text = defaultFooter,
            style = previewTextStyle(12.sp, lineHeight = 14.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
