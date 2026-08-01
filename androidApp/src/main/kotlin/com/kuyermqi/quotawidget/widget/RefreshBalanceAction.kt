package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Must stay lightweight: Glance invokes this via a short-lived trampoline.
 * Heavy work is delegated to WorkManager.
 */
class RefreshBalanceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetRefreshCoordinator.beginUserRefresh(context)
    }
}
