package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.widget.CompactStatusTitle
import com.kuyermqi.quotawidget.widget.RefreshButton
import com.kuyermqi.quotawidget.widget.clickableNoRipple
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
                            usageResetStyle = usageResetStyle,
                            openApp = openApp,
                            showProgress = false,
                            compact = true,
                        )
                    }
                }
                else -> CompactStatusTitle(
                    state = state,
                    openApp = openApp,
                    modifier = GlanceModifier.defaultWeight(),
                )
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
