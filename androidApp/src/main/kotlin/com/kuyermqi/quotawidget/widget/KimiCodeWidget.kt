package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageResetStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toKimiCodeUsageWindowKind
import com.kuyermqi.quotawidget.widget.usage.UsagePercentCompactContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentWidgetContent

class KimiCodeWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.KIMI_CODE) { state, refreshPhase, openApp, platformTitle ->
            val prefs = currentState<Preferences>()
            UsagePercentWidgetContent(
                platformId = PlatformIds.KIMI_CODE,
                platformTitle = platformTitle,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toKimiCodeUsageWindowKind(),
                usageDisplayMode = prefs.toKimiCodeUsageDisplayMode(),
                usageProgressStyle = prefs.toKimiCodeUsageProgressStyle(),
                usageResetStyle = prefs.toKimiCodeUsageResetStyle(),
            )
        }
    }
}

class KimiCodeCompactWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, PlatformIds.KIMI_CODE) { state, refreshPhase, openApp, _ ->
            val prefs = currentState<Preferences>()
            UsagePercentCompactContent(
                platformId = PlatformIds.KIMI_CODE,
                state = state,
                refreshPhase = refreshPhase,
                openApp = openApp,
                windowKind = prefs.toKimiCodeUsageWindowKind(),
                usageDisplayMode = prefs.toKimiCodeUsageDisplayMode(),
                usageProgressStyle = prefs.toKimiCodeUsageProgressStyle(),
                usageResetStyle = prefs.toKimiCodeUsageResetStyle(),
            )
        }
    }
}

class KimiCodeWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KimiCodeWidget()
}

class KimiCodeCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KimiCodeCompactWidget()
}
