package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatNewApiUsageWidgetTitle
import com.kuyermqi.quotawidget.domain.formatNewApiWidgetFooter
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.newApiUsageProgressDisplayMode
import com.kuyermqi.quotawidget.domain.newApiUsageProgressUsedPercent
import com.kuyermqi.quotawidget.domain.newApiUsageWidgetShowsProgress
import com.kuyermqi.quotawidget.domain.resolveCodexUsageSummaryWindowKind
import com.kuyermqi.quotawidget.domain.upcomingResetAtEpochMs
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.util.currentTimeMillis
import com.kuyermqi.quotawidget.widget.BalanceBlock
import com.kuyermqi.quotawidget.widget.WidgetHeader
import com.kuyermqi.quotawidget.widget.clickableNoRipple
import com.kuyermqi.quotawidget.widget.contextString
import com.kuyermqi.quotawidget.widget.fittedTitleFontSize
import com.kuyermqi.quotawidget.widget.updatedAtLabel
import com.kuyermqi.quotawidget.widget.systemWidgetBackgroundCornerRadius

@Composable
fun UsagePercentWidgetContent(
    platformId: String,
    platformTitle: String,
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .systemWidgetBackgroundCornerRadius()
            .clickableNoRipple(openApp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_rounded_bg),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
        ) {
            WidgetHeader(platformId, platformTitle, refreshPhase, openApp)
            Spacer(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .clickableNoRipple(openApp),
            )
            when (state) {
                WidgetDisplayState.NotConfigured ->
                    BalanceBlock(
                        title = contextString(R.string.widget_not_configured),
                        subtitle = null,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                WidgetDisplayState.Loading ->
                    BalanceBlock(
                        title = contextString(R.string.widget_loading_balance),
                        subtitle = null,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                WidgetDisplayState.NeedsReauth ->
                    BalanceBlock(
                        title = contextString(R.string.widget_needs_reauth),
                        subtitle = null,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                is WidgetDisplayState.Success ->
                    UsagePercentSuccessBlock(
                        snapshot = state.snapshot,
                        windowKind = windowKind,
                        usageDisplayMode = usageDisplayMode,
                        usageProgressStyle = usageProgressStyle,
                        usageResetStyle = usageResetStyle,
                        openApp = openApp,
                        showProgress = true,
                    )
                is WidgetDisplayState.Error ->
                    BalanceBlock(
                        title = contextString(R.string.widget_fetch_failed),
                        subtitle = null,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
            }
        }
    }
}

@Composable
internal fun UsagePercentSuccessBlock(
    snapshot: QuotaSnapshot,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
    openApp: Action,
    showProgress: Boolean,
    compact: Boolean = false,
) {
    val effectiveWindowKind = when (snapshot.platformId) {
        PlatformIds.CODEX,
        PlatformIds.KIMI_CODE,
        PlatformIds.ZHIPU,
        -> resolveCodexUsageSummaryWindowKind(snapshot.windows, windowKind) ?: windowKind
        in PlatformIds.sessionPlatforms ->
            resolveCodexUsageSummaryWindowKind(snapshot.windows, windowKind) ?: windowKind
        else -> windowKind
    }
    val window = snapshot.windows.find { it.kind == effectiveWindowKind.toQuotaWindowKind() }
    val used = window?.usedPercent
    val usageText = when {
        snapshot.platformId == PlatformIds.NEW_API ->
            formatNewApiUsageWidgetTitle(snapshot, usageDisplayMode)
                ?: contextString(R.string.usage_unavailable)
        used != null -> formatUsageDisplayPercent(used, usageDisplayMode)
        snapshot.platformId in PlatformIds.sessionPlatforms && snapshot.primaryDisplay.isNotBlank() ->
            snapshot.primaryDisplay
        else -> contextString(R.string.usage_unavailable)
    }
    val showUsageProgress = showProgress &&
        if (snapshot.platformId == PlatformIds.NEW_API) {
            newApiUsageWidgetShowsProgress(snapshot)
        } else {
            used != null
        }
    val progressUsed = if (snapshot.platformId == PlatformIds.NEW_API) {
        newApiUsageProgressUsedPercent(snapshot, usageDisplayMode)
    } else {
        used
    }
    val progressMode = if (snapshot.platformId == PlatformIds.NEW_API) {
        newApiUsageProgressDisplayMode(snapshot, usageDisplayMode)
    } else {
        usageDisplayMode
    }
    val nowEpochMs = currentTimeMillis()
    // Only advertise a reset for a window that actually has data to show.
    val resetAtEpochMs = if (used != null) {
        snapshot.upcomingResetAtEpochMs(
            kind = effectiveWindowKind.toQuotaWindowKind(),
            nowEpochMs = nowEpochMs,
        )
    } else {
        null
    }
    // A 2×1 widget has no room for a pill beside the value.
    val pillText = resetAtEpochMs
        ?.takeIf { usageResetStyle == UsageResetStyle.PILL && !compact }
        ?.let { formatUsageResetCountdown(it - nowEpochMs) }
    // A 2×1 widget has no room for a second footer line either.
    val resetStyle = when {
        compact && usageResetStyle == UsageResetStyle.FOOTER_STACKED -> UsageResetStyle.FOOTER
        compact && usageResetStyle == UsageResetStyle.PILL -> UsageResetStyle.FOOTER
        else -> usageResetStyle
    }
    val reservedHeight =
        (if (compact) 0.dp else 60.dp) +
            (if (showUsageProgress) 30.dp else 4.dp) +
            (if (resetStyle == UsageResetStyle.FOOTER_STACKED) 28.dp else 16.dp)
    val usageTextSize = usageTitleSize(
        text = usageText,
        compact = compact,
        reservedHeight = reservedHeight,
        extraWidth = if (pillText != null) 60.dp else 0.dp,
    )
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (pillText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = usageText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = usageTextSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickableNoRipple(openApp),
                )
                Spacer(GlanceModifier.width(6.dp))
                UsageResetPill(text = pillText, openApp = openApp)
            }
        } else {
            Text(
                text = usageText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = usageTextSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        }
        if (showUsageProgress && progressUsed != null) {
            Spacer(GlanceModifier.height(8.dp))
            when (usageProgressStyle) {
                UsageProgressStyle.CAPSULE ->
                    UsageCapsuleProgressTrack(
                        usedPercent = progressUsed,
                        usageDisplayMode = progressMode,
                        height = if (compact) 10.dp else 12.dp,
                    )
                UsageProgressStyle.BAR ->
                    UsageProgressBar(
                        usedPercent = progressUsed,
                        usageDisplayMode = progressMode,
                    )
            }
            Spacer(GlanceModifier.height(10.dp))
        } else {
            Spacer(GlanceModifier.height(4.dp))
        }
        val updatedText = updatedAtLabel(snapshot.updatedAtEpochMs)
        val updatedFooter = if (snapshot.platformId == PlatformIds.NEW_API) {
            formatNewApiWidgetFooter(
                snapshot = snapshot,
                expiredLabel = contextString(R.string.new_api_token_expired),
                updatedAtText = updatedText,
            )
        } else {
            updatedText
        }
        when {
            resetAtEpochMs != null && resetStyle == UsageResetStyle.FOOTER -> Text(
                text = resetAtText(resetAtEpochMs, nowEpochMs),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    // Absolute dates are long; 2×1 keeps them inside the value column.
                    fontSize = if (compact) 10.sp else 12.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
            resetAtEpochMs != null && resetStyle == UsageResetStyle.FOOTER_STACKED -> {
                Text(
                    text = resetAtText(resetAtEpochMs, nowEpochMs),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickableNoRipple(openApp),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = updatedFooter,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickableNoRipple(openApp),
                )
            }
            else -> Text(
                text = updatedFooter,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 11.sp else 12.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.clickableNoRipple(openApp),
            )
        }
    }
}

/** Small countdown capsule shown next to the large percent value (reset style C). */
@Composable
private fun UsageResetPill(text: String, openApp: Action) {
    Box(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(7.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clickableNoRipple(openApp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun usageTitleSize(
    text: String,
    compact: Boolean,
    reservedHeight: Dp,
    extraWidth: Dp = 0.dp,
): TextUnit {
    val size = LocalSize.current
    val horizontalReserve = if (compact) 64.dp else 28.dp
    return fittedTitleFontSize(
        text = text,
        maxWidth = (size.width - horizontalReserve - extraWidth).coerceAtLeast(40.dp),
        maxHeight = (size.height - reservedHeight).coerceAtLeast(24.dp),
        minSp = if (compact) 20f else 24f,
        maxSp = if (compact) 32f else 44f,
    )
}
