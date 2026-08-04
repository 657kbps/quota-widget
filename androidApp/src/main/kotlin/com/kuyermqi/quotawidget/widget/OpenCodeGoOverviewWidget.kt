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
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toOpenCodeUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toOpenCodeUsageProgressStyle
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeComfortable
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeCompact
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewWidgetContent

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
        providePlatformGlance(context, id, PlatformIds.OPENCODE_GO) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsageOverviewWidgetContent(
                platformId = PlatformIds.OPENCODE_GO,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                usageDisplayMode = prefs.toOpenCodeUsageDisplayMode(),
                usageProgressStyle = prefs.toOpenCodeUsageProgressStyle(),
                overviewKinds = listOf(
                    QuotaWindowKind.FIVE_HOUR,
                    QuotaWindowKind.WEEKLY,
                    QuotaWindowKind.MONTHLY,
                ),
            )
        }
    }

    companion object {
        val SizeCompact: DpSize = UsageOverviewSizeCompact
        val SizeComfortable: DpSize = UsageOverviewSizeComfortable
    }
}

class OpenCodeGoOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpenCodeGoOverviewWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
