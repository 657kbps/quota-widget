package com.kuyermqi.quotawidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "QuotaRefresh"

class DeepSeekPeakValleyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DeepSeekPeakValleyCoordinator.refresh(appContext, "alarm")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "peak-valley alarm refresh failed", error)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Recalculates after boot, package replacement, manual clock changes and time-zone changes. */
class WidgetSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val reason = "system:${intent.action.orEmpty()}"
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DeepSeekPeakValleyCoordinator.refresh(appContext, reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "peak-valley system refresh failed reason=$reason", error)
            } finally {
                pending.finish()
            }
        }
    }
}
