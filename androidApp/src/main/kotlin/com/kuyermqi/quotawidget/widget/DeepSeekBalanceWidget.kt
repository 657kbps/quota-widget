package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.currentState
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
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleyWidgetLayout
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.DeepSeekPeakValleyGlanceState.peakValleySnapshot

class DeepSeekBalanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(
            context = context,
            id = id,
            platformId = PlatformIds.DEEPSEEK,
            showsPeakValley = true,
        ) { state, refreshPhase, openApp, platformTitle ->
            DeepSeekWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                platformTitle = platformTitle,
            )
        }
    }
}

class DeepSeekBalanceCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.DEEPSEEK) {
                state,
                refreshPhase,
                openApp,
                _,
            ->
            DeepSeekCompactWidgetContent(
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
            )
        }
    }
}

@Composable
private fun DeepSeekWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
    platformTitle: String,
) {
    val prefs = currentState<Preferences>()
    val nowEpochMs = System.currentTimeMillis()
    val snapshot = prefs.peakValleySnapshot(nowEpochMs)
    val size = LocalSize.current
    val small = snapshot != null && PeakValleyWidgetLayout.useCompactLayout(size.height.value)
    val peakValleyVisible = snapshot != null
    val horizontalPadding = if (small) 12.dp else 14.dp
    val topPadding = when {
        !peakValleyVisible -> 14.dp
        small -> 8.dp
        else -> 12.dp
    }
    val bottomPadding = when {
        !peakValleyVisible -> 22.dp
        small -> 8.dp
        else -> 18.dp
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
                    start = horizontalPadding,
                    top = topPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding,
                ),
        ) {
            WidgetHeader(PlatformIds.DEEPSEEK, platformTitle, refreshPhase, openApp)
            if (small) Spacer(GlanceModifier.height(2.dp))
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
                        titleSize = if (small) 26.sp else 32.sp,
                        openApp = openApp,
                    )
                WidgetDisplayState.Loading ->
                    BalanceBlock(
                        title = contextString(R.string.widget_loading_balance),
                        subtitle = null,
                        titleSize = if (small) 26.sp else 32.sp,
                        openApp = openApp,
                    )
                WidgetDisplayState.NeedsReauth ->
                    BalanceBlock(
                        title = contextString(R.string.widget_needs_reauth),
                        subtitle = null,
                        titleSize = if (small) 26.sp else 32.sp,
                        openApp = openApp,
                    )
                is WidgetDisplayState.Success -> {
                    val balance = state.snapshot.primaryDisplay
                    BalanceBlock(
                        title = balance,
                        subtitle = if (small) {
                            null
                        } else {
                            "更新于 ${WidgetDateFormatter.formatUpdatedAt(state.snapshot.updatedAtEpochMs)}"
                        },
                        titleSize = if (small) {
                            compactBalanceTitleFontSize(balance)
                        } else {
                            balanceTitleFontSize(balance)
                        },
                        openApp = openApp,
                    )
                }
                is WidgetDisplayState.Error ->
                    BalanceBlock(
                        title = contextString(R.string.widget_fetch_failed),
                        subtitle = null,
                        titleSize = if (small) 26.sp else 32.sp,
                        openApp = openApp,
                    )
            }
            if (snapshot != null) {
                Spacer(GlanceModifier.height(if (small) 2.dp else 6.dp))
                DeepSeekPeakValleyWidgetSection(
                    snapshot = snapshot,
                    showTarget = PeakValleyWidgetLayout.showTarget(size.width.value),
                    showCountdown = PeakValleyWidgetLayout.showCountdown(size.height.value),
                    compact = small,
                    openApp = openApp,
                )
            }
        }
    }
}

@Composable
private fun DeepSeekCompactWidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
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
            val (title, subtitle, titleSize) = when (state) {
                WidgetDisplayState.NotConfigured ->
                    Triple(contextString(R.string.widget_not_configured), null, 26.sp)
                WidgetDisplayState.Loading ->
                    Triple(contextString(R.string.widget_loading_balance), null, 26.sp)
                WidgetDisplayState.NeedsReauth ->
                    Triple(contextString(R.string.widget_needs_reauth), null, 26.sp)
                is WidgetDisplayState.Success -> {
                    val primary = state.snapshot.primaryDisplay
                    Triple(
                        primary,
                        "更新于 ${WidgetDateFormatter.formatUpdatedAt(state.snapshot.updatedAtEpochMs)}",
                        compactBalanceTitleFontSize(primary),
                    )
                }
                is WidgetDisplayState.Error ->
                    Triple(contextString(R.string.widget_fetch_failed), null, 26.sp)
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
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickableNoRipple(openApp),
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.clickableNoRipple(openApp),
                    )
                }
            }
            RefreshButton(
                platformId = PlatformIds.DEEPSEEK,
                phase = refreshPhase,
                hitSize = 34.dp,
                iconSize = 30.dp,
                spinnerSize = 28.dp,
            )
        }
    }
}

class DeepSeekBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeepSeekBalanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
        DeepSeekPeakValleyCoordinator.requestRefresh(context, "widget_enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DeepSeekPeakValleyCoordinator.requestRefresh(context, "widget_disabled")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        DeepSeekPeakValleyCoordinator.requestRefresh(context, "widget_deleted")
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        DeepSeekPeakValleyCoordinator.requestRefresh(context, "widget_resized")
    }
}

class DeepSeekBalanceCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeepSeekBalanceCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
