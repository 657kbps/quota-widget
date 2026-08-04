package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.widget.BalanceBlock
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.WidgetHeader
import com.kuyermqi.quotawidget.widget.balanceTitleFontSize
import com.kuyermqi.quotawidget.widget.clickableNoRipple
import com.kuyermqi.quotawidget.widget.compactBalanceTitleFontSize
import com.kuyermqi.quotawidget.widget.contextString
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
    openApp: Action,
    showProgress: Boolean,
    compact: Boolean = false,
) {
    val used = snapshot.windows
        .find { it.kind == windowKind.toQuotaWindowKind() }
        ?.usedPercent
    val usageText = if (used != null) {
        formatUsageDisplayPercent(used, usageDisplayMode)
    } else {
        snapshot.primaryDisplay.ifBlank { contextString(R.string.usage_unavailable) }
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = usageText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) {
                    compactBalanceTitleFontSize(usageText)
                } else {
                    balanceTitleFontSize(usageText)
                },
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
        if (showProgress && used != null) {
            Spacer(GlanceModifier.height(8.dp))
            when (usageProgressStyle) {
                UsageProgressStyle.CAPSULE ->
                    UsageCapsuleProgressTrack(
                        usedPercent = used,
                        usageDisplayMode = usageDisplayMode,
                        height = if (compact) 10.dp else 12.dp,
                    )
                UsageProgressStyle.BAR ->
                    UsageProgressBar(
                        usedPercent = used,
                        usageDisplayMode = usageDisplayMode,
                    )
            }
            Spacer(GlanceModifier.height(10.dp))
        } else {
            Spacer(GlanceModifier.height(4.dp))
        }
        Text(
            text = "更新于 ${WidgetDateFormatter.formatUpdatedAt(snapshot.updatedAtEpochMs)}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compact) 11.sp else 12.sp,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
    }
}
