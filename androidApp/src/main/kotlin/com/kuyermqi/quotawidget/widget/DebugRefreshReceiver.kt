package com.kuyermqi.quotawidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only entry so we can trigger refresh from adb:
 * adb shell am broadcast -a com.kuyermqi.quotawidget.ACTION_DEBUG_REFRESH \
 *   -n com.kuyermqi.quotawidget.debug/.widget.DebugRefreshReceiver \
 *   --es platform_id opencode_go
 */
class DebugRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val platformId = intent?.getStringExtra(EXTRA_PLATFORM_ID)
            ?: PlatformIds.OPENCODE_GO
        Log.i(TAG, "debug broadcast received action=${intent?.action} platform=$platformId")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRefreshCoordinator.beginUserRefresh(
                    context.applicationContext,
                    platformId,
                )
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
        const val EXTRA_PLATFORM_ID = "platform_id"
    }
}
