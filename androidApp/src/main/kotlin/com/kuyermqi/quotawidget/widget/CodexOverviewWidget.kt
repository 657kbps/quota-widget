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
import com.kuyermqi.quotawidget.domain.presentCodexOverviewWindowKinds
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageDisplayMode
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeComfortable
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeCompact
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewWidgetContent

/**
 * Codex overview: shows available windows (weekly / monthly; 5h if present).
 */
class CodexOverviewWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SizeCompact, SizeComfortable),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.CODEX) { state, refreshPhase, openApp, platformTitle ->
            val overviewKinds = when (state) {
                is WidgetDisplayState.Success ->
                    presentCodexOverviewWindowKinds(state.snapshot.windows)
                else -> emptyList()
            }
            UsageOverviewWidgetContent(
                platformId = PlatformIds.CODEX,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                usageDisplayMode = currentState<Preferences>().toCodexUsageDisplayMode(),
                overviewKinds = overviewKinds,
            )
        }
    }

    companion object {
        val SizeCompact: DpSize = UsageOverviewSizeCompact
        val SizeComfortable: DpSize = UsageOverviewSizeComfortable
    }
}

class CodexOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodexOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
