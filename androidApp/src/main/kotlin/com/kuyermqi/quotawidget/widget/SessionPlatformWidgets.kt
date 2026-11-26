package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageDisplayMode
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageProgressStyle
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toSessionUsageResetStyle
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
                    usageResetStyle = prefs.toSessionUsageResetStyle(platformId),
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
                    usageResetStyle = prefs.toSessionUsageResetStyle(platformId),
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
                    usageResetStyle = prefs.toSessionUsageResetStyle(platformId),
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

class WorkBuddyCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyCompactWidget()
}

class WorkBuddyAiCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiCompactWidget()
}

class ClineCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineCompactWidget()
}

class CommandCodeCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeCompactWidget()
}

class AntigravityCompactWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityCompactWidget()
}

class WorkBuddyWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyWidget()
}

class WorkBuddyOverviewWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyOverviewWidget()
}

class WorkBuddyAiWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiWidget()
}

class WorkBuddyAiOverviewWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkBuddyAiOverviewWidget()
}

class ClineWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineWidget()
}

class ClineOverviewWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClineOverviewWidget()
}

class CommandCodeWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeWidget()
}

class CommandCodeOverviewWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommandCodeOverviewWidget()
}

class AntigravityWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityWidget()
}

class AntigravityOverviewWidgetReceiver : QuotaGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AntigravityOverviewWidget()
}
