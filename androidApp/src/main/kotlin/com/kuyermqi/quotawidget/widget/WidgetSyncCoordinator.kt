package com.kuyermqi.quotawidget.widget

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single funnel for mirroring repository state into Glance preferences: concurrent
 * callers must not interleave writes into the same widget state, so every sync takes
 * [widgetUpdateMutex] here instead of touching [WidgetGlanceState] directly.
 */
object WidgetSyncCoordinator {
    private val widgetUpdateMutex = Mutex()

    suspend fun syncWidgets(context: Context, reason: String, platformId: String? = null) {
        val appContext = context.applicationContext
        widgetUpdateMutex.withLock {
            WidgetGlanceState.syncAndUpdate(appContext, reason, platformId)
        }
        RefreshScheduleGate.sync(appContext)
    }
}
