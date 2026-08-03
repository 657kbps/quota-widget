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
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.OpenCodeWidgetWindowKind
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatRemainingUsagePercent
import com.kuyermqi.quotawidget.domain.isUsageNearLimit
import com.kuyermqi.quotawidget.domain.remainingUsagePercent
import com.kuyermqi.quotawidget.platform.PlatformIds

class OpenCodeGoWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val windowKind = (context.applicationContext as QuotaWidgetApp)
            .settingsRepository
            .getOpenCodeGoSettings()
            .widgetWindowKind
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { state, refreshPhase, openApp, platformTitle ->
            OpenCodeWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                platformTitle = platformTitle,
                windowKind = windowKind,
            )
        }
    }
}

class OpenCodeGoCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val windowKind = (context.applicationContext as QuotaWidgetApp)
            .settingsRepository
            .getOpenCodeGoSettings()
            .widgetWindowKind
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { state, refreshPhase, openApp, _ ->
            OpenCodeCompactWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = windowKind,
            )
        }
    }
}

@Composable
private fun OpenCodeWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    platformTitle: String,
    windowKind: OpenCodeWidgetWindowKind,
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
                    OpenCodeSuccessBlock(
                        snapshot = state.snapshot,
                        windowKind = windowKind,
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
private fun OpenCodeCompactWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    windowKind: OpenCodeWidgetWindowKind,
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
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is WidgetDisplayState.Success -> {
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickableNoRipple(openApp),
                    ) {
                        OpenCodeSuccessBlock(
                            snapshot = state.snapshot,
                            windowKind = windowKind,
                            openApp = openApp,
                            showProgress = false,
                            compact = true,
                        )
                    }
                }
                else -> {
                    val title = when (state) {
                        WidgetDisplayState.NotConfigured ->
                            contextString(R.string.widget_not_configured)
                        WidgetDisplayState.Loading ->
                            contextString(R.string.widget_loading_balance)
                        WidgetDisplayState.NeedsReauth ->
                            contextString(R.string.widget_needs_reauth)
                        is WidgetDisplayState.Error ->
                            contextString(R.string.widget_fetch_failed)
                        is WidgetDisplayState.Success -> ""
                    }
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickableNoRipple(openApp),
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            RefreshButton(
                platformId = PlatformIds.OPENCODE_GO,
                phase = refreshPhase,
                hitSize = 34.dp,
                iconSize = 30.dp,
                spinnerSize = 28.dp,
            )
        }
    }
}

@Composable
private fun OpenCodeSuccessBlock(
    snapshot: QuotaSnapshot,
    windowKind: OpenCodeWidgetWindowKind,
    openApp: Action,
    showProgress: Boolean,
    compact: Boolean = false,
) {
    val used = snapshot.windows
        .find { it.kind == windowKind.toQuotaWindowKind() }
        ?.usedPercent
    val remainingText = if (used != null) {
        formatRemainingUsagePercent(used)
    } else {
        snapshot.primaryDisplay.ifBlank { contextString(R.string.opencode_usage_unavailable) }
    }
    val remainingFraction = used?.let {
        (remainingUsagePercent(it) / 100.0).toFloat().coerceIn(0f, 1f)
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = remainingText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) compactBalanceTitleFontSize(remainingText) else balanceTitleFontSize(remainingText),
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
        if (showProgress) {
            Spacer(GlanceModifier.height(8.dp))
            if (remainingFraction != null && used != null) {
                OpenCodeSegmentProgressBar(
                    fillFraction = remainingFraction,
                    nearLimit = isUsageNearLimit(used),
                )
                Spacer(GlanceModifier.height(10.dp))
            } else {
                Spacer(GlanceModifier.height(4.dp))
            }
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

class OpenCodeGoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenCodeGoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class OpenCodeGoCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenCodeGoCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
