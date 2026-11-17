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
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageWindowKind
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeComfortable
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewSizeCompact
import com.kuyermqi.quotawidget.widget.usage.UsageOverviewWidgetContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentCompactContent
import com.kuyermqi.quotawidget.widget.usage.UsagePercentWidgetContent

/** Shared Glance implementation for every session-based usage provider. */
abstract class SessionUsageWidgetBase(
    private val platformId: String,
    private val overview: Boolean,
    private val compact: Boolean = false,
) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = if (overview) {
        SizeMode.Responsive(setOf(UsageOverviewSizeCompact, UsageOverviewSizeComfortable))
    } else {
        SizeMode.Exact
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        providePlatformGlance(context, id, platformId) { state, refreshPhase, openApp, title ->
            val prefs = currentState<Preferences>()
            if (compact) {
                UsagePercentCompactContent(
                    platformId = platformId,
                    state = state,
                    refreshPhase = refreshPhase,
                    openApp = openApp,
                    windowKind = prefs.toSessionUsageWindowKind(platformId),
                    usageDisplayMode = prefs.toSessionUsageDisplayMode(platformId),
                    usageProgressStyle = prefs.toSessionUsageProgressStyle(platformId),
                )
            } else if (overview) {
                UsageOverviewWidgetContent(
                    platformId = platformId,
                    platformTitle = title,
                    state = state,
                    refreshPhase = refreshPhase,
                    openApp = openApp,
                    usageDisplayMode = prefs.toSessionUsageDisplayMode(platformId),
                    usageProgressStyle = prefs.toSessionUsageProgressStyle(platformId),
                    overviewKinds = overviewKindsFor(platformId),
                )
            } else {
                UsagePercentWidgetContent(
                    platformId = platformId,
                    platformTitle = title,
                    state = state,
                    refreshPhase = refreshPhase,
                    openApp = openApp,
                    windowKind = prefs.toSessionUsageWindowKind(platformId),
                    usageDisplayMode = prefs.toSessionUsageDisplayMode(platformId),
                    usageProgressStyle = prefs.toSessionUsageProgressStyle(platformId),
                )
            }
        }
    }
}

private fun overviewKindsFor(platformId: String): List<QuotaWindowKind> = when (platformId) {
    PlatformIds.WORKBUDDY, PlatformIds.WORKBUDDY_AI -> listOf(QuotaWindowKind.MONTHLY)
    PlatformIds.ANTIGRAVITY -> listOf(QuotaWindowKind.FIVE_HOUR, QuotaWindowKind.WEEKLY)
    else -> listOf(
        QuotaWindowKind.FIVE_HOUR,
        QuotaWindowKind.WEEKLY,
        QuotaWindowKind.MONTHLY,
    )
}

class WorkBuddyWidget : SessionUsageWidgetBase(PlatformIds.WORKBUDDY, overview = false)
class WorkBuddyCompactWidget :
    SessionUsageWidgetBase(PlatformIds.WORKBUDDY, overview = false, compact = true)
class WorkBuddyOverviewWidget : SessionUsageWidgetBase(PlatformIds.WORKBUDDY, overview = true)
class WorkBuddyAiWidget : SessionUsageWidgetBase(PlatformIds.WORKBUDDY_AI, overview = false)
class WorkBuddyAiCompactWidget :
    SessionUsageWidgetBase(PlatformIds.WORKBUDDY_AI, overview = false, compact = true)
class WorkBuddyAiOverviewWidget :
    SessionUsageWidgetBase(PlatformIds.WORKBUDDY_AI, overview = true)
class ClineWidget : SessionUsageWidgetBase(PlatformIds.CLINE, overview = false)
class ClineCompactWidget :
    SessionUsageWidgetBase(PlatformIds.CLINE, overview = false, compact = true)
class ClineOverviewWidget : SessionUsageWidgetBase(PlatformIds.CLINE, overview = true)
class CommandCodeWidget : SessionUsageWidgetBase(PlatformIds.COMMAND_CODE, overview = false)
class CommandCodeCompactWidget :
    SessionUsageWidgetBase(PlatformIds.COMMAND_CODE, overview = false, compact = true)
class CommandCodeOverviewWidget : SessionUsageWidgetBase(PlatformIds.COMMAND_CODE, overview = true)
class AntigravityWidget : SessionUsageWidgetBase(PlatformIds.ANTIGRAVITY, overview = false)
class AntigravityCompactWidget :
    SessionUsageWidgetBase(PlatformIds.ANTIGRAVITY, overview = false, compact = true)
class AntigravityOverviewWidget : SessionUsageWidgetBase(PlatformIds.ANTIGRAVITY, overview = true)

class WorkBuddyCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyCompactWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class WorkBuddyAiCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiCompactWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class ClineCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineCompactWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class CommandCodeCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeCompactWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class AntigravityCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityCompactWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class WorkBuddyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class WorkBuddyOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyOverviewWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class WorkBuddyAiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class WorkBuddyAiOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiOverviewWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class ClineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class ClineOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineOverviewWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class CommandCodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class CommandCodeOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeOverviewWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class AntigravityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}

class AntigravityOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityOverviewWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
    }
}
