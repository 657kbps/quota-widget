package com.kuyermqi.quotawidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import com.kuyermqi.quotawidget.QuotaWidgetApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Periodic refresh only earns its wakeups while a widget is placed on a home screen and at
 * least one platform has credentials; otherwise the unique periodic work is cancelled.
 */
object RefreshScheduleGate {
    private const val TAG = "QuotaRefresh"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun syncAsync(context: Context, intervalMinutes: Int? = null) {
        val appContext = context.applicationContext
        scope.launch { sync(appContext, intervalMinutes) }
    }

    suspend fun sync(context: Context, intervalMinutes: Int? = null) {
        mutex.withLock {
            try {
                apply(context.applicationContext, intervalMinutes)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "refresh schedule gate failed", t)
            }
        }
    }

    private suspend fun apply(context: Context, intervalMinutes: Int?) {
        val app = context as? QuotaWidgetApp
        if (app == null) {
            Log.w(TAG, "refresh schedule gate skipped; unexpected application context")
            return
        }
        if (!hasWidgetInstances(context) || app.refreshInteractor.configuredPlatformIds().isEmpty()) {
            PeriodicRefreshScheduler.cancel(context)
            Log.i(TAG, "refresh schedule gated off")
            return
        }
        val interval = intervalMinutes
            ?: app.settingsRepository.getAppSettings().refreshIntervalMinutes
        PeriodicRefreshScheduler.schedule(context, interval)
        Log.i(TAG, "refresh schedule gated on intervalMinutes=$interval")
    }

    private fun hasWidgetInstances(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val packageName = context.packageName
        return manager.installedProviders
            .asSequence()
            .filter { provider -> provider.provider.packageName == packageName }
            .any { provider -> manager.getAppWidgetIds(provider.provider).isNotEmpty() }
    }
}
