package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.OpenCodeUsageDisplayMode
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatOpenCodeUsagePercent
import com.kuyermqi.quotawidget.domain.opencode.openCodeWindowLabelRes
import com.kuyermqi.quotawidget.platform.PlatformIds

/**
 * OpenCode overview widget: rolling / weekly / monthly usage.
 *
 * [SizeCompact] — tighter type and gaps for default 2×2.
 * [SizeComfortable] — original spacing when the widget is taller.
 */
class OpenCodeGoOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SizeCompact, SizeComfortable),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val usageDisplayMode = (context.applicationContext as QuotaWidgetApp)
            .settingsRepository
            .getOpenCodeGoSettings()
            .usageDisplayMode
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { state, refreshPhase, openApp, platformTitle ->
            OpenCodeOverviewWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                platformTitle = platformTitle,
                usageDisplayMode = usageDisplayMode,
            )
        }
    }

    companion object {
        val SizeCompact = DpSize(110.dp, 110.dp)
        val SizeComfortable = DpSize(110.dp, 180.dp)
    }
}

private data class OverviewDensity(
    val paddingStart: Dp,
    val paddingTop: Dp,
    val paddingEnd: Dp,
    val paddingBottom: Dp,
    val headerGap: Dp,
    val rowGap: Dp,
    val labelBarGap: Dp,
    val updatedGap: Dp,
    val labelSize: TextUnit,
    val updatedSize: TextUnit,
)

private val CompactDensity = OverviewDensity(
    paddingStart = 12.dp,
    paddingTop = 10.dp,
    paddingEnd = 12.dp,
    paddingBottom = 14.dp,
    headerGap = 0.dp,
    rowGap = 6.dp,
    labelBarGap = 2.dp,
    updatedGap = 6.dp,
    labelSize = 11.sp,
    updatedSize = 10.sp,
)

private val ComfortableDensity = OverviewDensity(
    paddingStart = 14.dp,
    paddingTop = 14.dp,
    paddingEnd = 14.dp,
    paddingBottom = 22.dp,
    headerGap = 10.dp,
    rowGap = 10.dp,
    labelBarGap = 4.dp,
    updatedGap = 10.dp,
    labelSize = 12.sp,
    updatedSize = 12.sp,
)

@Composable
private fun OpenCodeOverviewWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    platformTitle: String,
    usageDisplayMode: OpenCodeUsageDisplayMode,
) {
    val density = if (LocalSize.current.height >= OpenCodeGoOverviewWidget.SizeComfortable.height) {
        ComfortableDensity
    } else {
        CompactDensity
    }
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
                .padding(
                    start = density.paddingStart,
                    top = density.paddingTop,
                    end = density.paddingEnd,
                    bottom = density.paddingBottom,
                ),
        ) {
            WidgetHeader(PlatformIds.OPENCODE_GO, platformTitle, refreshPhase, openApp)
            if (density.headerGap > 0.dp) {
                Spacer(modifier = GlanceModifier.height(density.headerGap))
            }
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
                is WidgetDisplayState.Success -> {
                    Spacer(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .clickableNoRipple(openApp),
                    )
                    OpenCodeOverviewSuccessBlock(
                        snapshot = state.snapshot,
                        openApp = openApp,
                        density = density,
                        usageDisplayMode = usageDisplayMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenCodeOverviewSuccessBlock(
    snapshot: QuotaSnapshot,
    openApp: Action,
    density: OverviewDensity,
    usageDisplayMode: OpenCodeUsageDisplayMode,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickableNoRipple(openApp),
    ) {
        OverviewUsageRow(
            label = contextString(
                openCodeWindowLabelRes(QuotaWindowKind.FIVE_HOUR, usageDisplayMode),
            ),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.FIVE_HOUR },
            openApp = openApp,
            density = density,
            usageDisplayMode = usageDisplayMode,
        )
        Spacer(GlanceModifier.height(density.rowGap))
        OverviewUsageRow(
            label = contextString(
                openCodeWindowLabelRes(QuotaWindowKind.WEEKLY, usageDisplayMode),
            ),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.WEEKLY },
            openApp = openApp,
            density = density,
            usageDisplayMode = usageDisplayMode,
        )
        Spacer(GlanceModifier.height(density.rowGap))
        OverviewUsageRow(
            label = contextString(
                openCodeWindowLabelRes(QuotaWindowKind.MONTHLY, usageDisplayMode),
            ),
            window = snapshot.windows.find { it.kind == QuotaWindowKind.MONTHLY },
            openApp = openApp,
            density = density,
            usageDisplayMode = usageDisplayMode,
        )
        Spacer(GlanceModifier.height(density.updatedGap))
        Text(
            text = "更新于 ${WidgetDateFormatter.formatUpdatedAt(snapshot.updatedAtEpochMs)}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = density.updatedSize,
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
    density: OverviewDensity,
    usageDisplayMode: OpenCodeUsageDisplayMode,
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
                    fontSize = density.labelSize,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = used?.let { formatOpenCodeUsagePercent(it, usageDisplayMode) }
                    ?: contextString(R.string.opencode_usage_unavailable),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = density.labelSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(density.labelBarGap))
        if (used != null) {
            OpenCodeUsageProgressBar(
                usedPercent = used,
                usageDisplayMode = usageDisplayMode,
            )
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
