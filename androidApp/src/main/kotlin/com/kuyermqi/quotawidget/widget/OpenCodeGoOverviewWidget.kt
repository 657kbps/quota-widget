package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatUsagePercent
import com.kuyermqi.quotawidget.platform.PlatformIds

/** 2×2 OpenCode widget showing rolling / weekly / monthly used percent at once. */
class OpenCodeGoOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { state, refreshPhase, openApp, platformTitle ->
            OpenCodeOverviewWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                platformTitle = platformTitle,
            )
        }
    }
}

@Composable
private fun OpenCodeOverviewWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    platformTitle: String,
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
            WidgetHeader(PlatformIds.OPENCODE_GO, platformTitle, refreshPhase, openApp)
            Spacer(modifier = GlanceModifier.height(10.dp))
            when (state) {
                WidgetDisplayState.NotConfigured,
                WidgetDisplayState.Loading,
                WidgetDisplayState.NeedsReauth,
                is WidgetDisplayState.Error,
                -> {
                    Spacer(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .clickableNoRipple(openApp),
                    )
                    BalanceBlock(
                        title = when (state) {
                            WidgetDisplayState.NotConfigured ->
                                contextString(R.string.widget_not_configured)
                            WidgetDisplayState.Loading ->
                                contextString(R.string.widget_loading_balance)
                            WidgetDisplayState.NeedsReauth ->
                                contextString(R.string.widget_needs_reauth)
                            is WidgetDisplayState.Error ->
                                contextString(R.string.widget_fetch_failed)
                            else -> ""
                        },
                        subtitle = null,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                }
                is WidgetDisplayState.Success ->
                    OpenCodeOverviewSuccessBlock(
                        snapshot = state.snapshot,
                        openApp = openApp,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                    )
            }
        }
    }
}

@Composable
private fun OpenCodeOverviewSuccessBlock(
    snapshot: QuotaSnapshot,
    openApp: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier.clickableNoRipple(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverviewUsageRow(
            label = contextString(R.string.opencode_window_rolling),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.FIVE_HOUR },
            openApp = openApp,
        )
        Spacer(GlanceModifier.height(10.dp))
        OverviewUsageRow(
            label = contextString(R.string.opencode_window_weekly),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.WEEKLY },
            openApp = openApp,
        )
        Spacer(GlanceModifier.height(10.dp))
        OverviewUsageRow(
            label = contextString(R.string.opencode_window_monthly),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.MONTHLY },
            openApp = openApp,
        )
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = "更新于 ${WidgetDateFormatter.formatUpdatedAt(snapshot.updatedAtEpochMs)}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
    }
}

@Composable
private fun OverviewUsageRow(
    label: String,
    window: QuotaWindow?,
    openApp: Action,
) {
    val used = window?.usedPercent
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickableNoRipple(openApp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = used?.let { formatUsagePercent(it) }
                    ?: contextString(R.string.opencode_usage_unavailable),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (used != null) {
            OpenCodeUsedProgressBar(usedPercent = used)
        } else {
            OpenCodeSegmentProgressBar(fillFraction = 0f, nearLimit = false)
        }
    }
}

class OpenCodeGoOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenCodeGoOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
