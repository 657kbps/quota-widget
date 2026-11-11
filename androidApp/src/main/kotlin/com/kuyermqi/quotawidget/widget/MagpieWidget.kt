package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toMagpieUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toMagpieUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toMagpieUsageWindowKind
import com.kuyermqi.quotawidget.widget.usage.UsagePercentCompactContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentWidgetContent

class MagpieWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.MAGPIE) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsagePercentWidgetContent(
                platformId = PlatformIds.MAGPIE,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toMagpieUsageWindowKind(),
                usageDisplayMode = prefs.toMagpieUsageDisplayMode(),
                usageProgressStyle = prefs.toMagpieUsageProgressStyle(),
            )
        }
    }
}

class MagpieCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.MAGPIE) { state, refreshPhase, openApp, _ ->
            val prefs = currentState<Preferences>()
            UsagePercentCompactContent(
                platformId = PlatformIds.MAGPIE,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toMagpieUsageWindowKind(),
                usageDisplayMode = prefs.toMagpieUsageDisplayMode(),
                usageProgressStyle = prefs.toMagpieUsageProgressStyle(),
            )
        }
    }
}

class MagpieWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MagpieWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class MagpieCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MagpieCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
