package com.kuyermqi.quotawidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only entry so we can trigger refresh from adb:
 * adb shell am broadcast -a com.kuyermqi.quotawidget.ACTION_DEBUG_REFRESH -n com.kuyermqi.quotawidget/.widget.DebugRefreshReceiver
 */
class DebugRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "debug broadcast received action=${intent?.action}")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRefreshCoordinator.beginUserRefresh(context.applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "debug refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuotaRefresh"
        const val ACTION = "com.kuyermqi.quotawidget.ACTION_DEBUG_REFRESH"
    }
}
