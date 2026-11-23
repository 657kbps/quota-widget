package com.kuyermqi.quotawidget.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only: seeds fake usage snapshots so widget layouts can be inspected
 * without live credentials.
 *
 * adb shell am broadcast -a com.kuyermqi.quotawidget.debug.ACTION_SEED \
 *   -n com.kuyermqi.quotawidget.debug/com.kuyermqi.quotawidget.debug.SeedWidgetsReceiver
 */
class SeedWidgetsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as QuotaWidgetApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DebugWidgetSeed.seed(app)
                WidgetGlanceState.syncAndUpdate(app, "debug_seed")
                Log.i(TAG, "seed done")
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuotaSeed"
        const val ACTION = "com.kuyermqi.quotawidget.debug.ACTION_SEED"
    }
}
