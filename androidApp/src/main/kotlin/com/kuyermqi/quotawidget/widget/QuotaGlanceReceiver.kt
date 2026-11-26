package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Shared receiver base: adding or removing any widget reconciles the periodic refresh
 * schedule, so a device without widgets stops waking up for refresh work.
 */
abstract class QuotaGlanceReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueBootstrapRefresh(context)
        RefreshScheduleGate.syncAsync(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        RefreshScheduleGate.syncAsync(context)
    }
}
