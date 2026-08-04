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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.widget.RefreshButton
import com.kuyermqi.quotawidget.widget.clickableNoRipple
import com.kuyermqi.quotawidget.widget.contextString
import com.kuyermqi.quotawidget.widget.systemWidgetBackgroundCornerRadius

@Composable
fun UsagePercentCompactContent(
    platformId: String,
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
                        UsagePercentSuccessBlock(
                            snapshot = state.snapshot,
                            windowKind = windowKind,
                            usageDisplayMode = usageDisplayMode,
                            usageProgressStyle = usageProgressStyle,
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
                platformId = platformId,
                phase = refreshPhase,
                hitSize = 34.dp,
                iconSize = 30.dp,
                spinnerSize = 28.dp,
            )
        }
    }
}
