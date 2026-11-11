package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.presentUsageOverviewWindowKinds
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toZhipuUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toZhipuUsageProgressStyle
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeComfortable
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeCompact
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewWidgetContent

/**
 * Zhipu Coding Plan overview: 5h and weekly windows.
 */
class ZhipuOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SizeCompact, SizeComfortable),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.ZHIPU) { state, refreshPhase, openApp, platformTitle ->
            val overviewKinds = when (state) {
                is WidgetDisplayState.Success ->
                    presentUsageOverviewWindowKinds(state.snapshot.windows)
                else -> emptyList()
            }
            val prefs = currentState<Preferences>()
            UsageOverviewWidgetContent(
                platformId = PlatformIds.ZHIPU,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                usageDisplayMode = prefs.toZhipuUsageDisplayMode(),
                usageProgressStyle = prefs.toZhipuUsageProgressStyle(),
                overviewKinds = overviewKinds,
            )
        }
    }

    companion object {
        val SizeCompact: DpSize = UsageOverviewSizeCompact
        val SizeComfortable: DpSize = UsageOverviewSizeComfortable
    }
}

class ZhipuOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZhipuOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
