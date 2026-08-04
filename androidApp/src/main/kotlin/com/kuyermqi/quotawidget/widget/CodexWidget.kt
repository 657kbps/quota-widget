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
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toCodexUsageWindowKind
import com.kuyermqi.quotawidget.widget.usage.UsagePercentCompactContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentWidgetContent

class CodexWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.CODEX) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsagePercentWidgetContent(
                platformId = PlatformIds.CODEX,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toCodexUsageWindowKind(),
                usageDisplayMode = prefs.toCodexUsageDisplayMode(),
                usageProgressStyle = prefs.toCodexUsageProgressStyle(),
            )
        }
    }
}

class CodexCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.CODEX) { state, refreshPhase, openApp, _ ->
            val prefs = currentState<Preferences>()
            UsagePercentCompactContent(
                platformId = PlatformIds.CODEX,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toCodexUsageWindowKind(),
                usageDisplayMode = prefs.toCodexUsageDisplayMode(),
                usageProgressStyle = prefs.toCodexUsageProgressStyle(),
            )
        }
    }
}

class CodexWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodexWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class CodexCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CodexCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
