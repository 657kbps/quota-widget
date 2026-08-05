package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.refresh.SingleFlight
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetRefreshCoordinator {
    private const val TAG = "QuotaRefresh"
    private const val STALE_SPIN_TIMEOUT_MS = 45_000L
    private const val MIN_SPINNER_VISIBLE_MS = 450L

    private val widgetUpdateMutex = Mutex()
    private val backgroundRefreshFlight = SingleFlight<BalanceRefreshResult>()

    /**
     * ActionCallback entry: enqueue work only. Glance updates happen in the worker
     * via [WidgetGlanceState.syncAndUpdate] so recomposition sees fresh Glance state.
     */
    suspend fun beginUserRefresh(context: Context, platformId: String) {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val phase = settings.getRefreshIconPhase(platformId)
        Log.i(TAG, "beginUserRefresh platform=$platformId phase=$phase")
        if (phase == RefreshIconPhase.Spinning || phase == RefreshIconPhase.Settling) {
            val startedAt = settings.getRefreshStartedAtEpochMs(platformId)
            val age = System.currentTimeMillis() - startedAt
            if (startedAt > 0L && age in 0 until STALE_SPIN_TIMEOUT_MS) {
                Log.w(TAG, "beginUserRefresh ignored; still refreshing platform=$platformId ageMs=$age")
                return
            }
            Log.w(TAG, "beginUserRefresh stale phase=$phase platform=$platformId ageMs=$age; continuing")
        }

        settings.setRefreshStartedAtEpochMs(platformId, System.currentTimeMillis())
        settings.setRefreshIconPhase(platformId, RefreshIconPhase.Spinning)
        updateWidgetSerialized(context, "spinning-pre-enqueue")
        Log.i(TAG, "beginUserRefresh enqueued user work platform=$platformId")

        WorkManager.getInstance(context).enqueueUniqueWork(
            BalanceRefreshWorker.userRefreshWorkName(platformId),
            ExistingWorkPolicy.REPLACE,
            BalanceRefreshWork.oneTime(
                userInitiated = true,
                expedited = true,
                platformId = platformId,
            ),
        )
    }

    suspend fun runUserRefresh(context: Context, platformId: String): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val startedAt = settings.getRefreshStartedAtEpochMs(platformId).takeIf { it > 0L }
            ?: System.currentTimeMillis().also {
                settings.setRefreshStartedAtEpochMs(platformId, it)
            }
        Log.i(TAG, "runUserRefresh start platform=$platformId")
        return try {
            settings.setRefreshIconPhase(platformId, RefreshIconPhase.Spinning)
            updateWidgetSerialized(context, "spinning")

            val result = app.refreshInteractor.refresh(platformId)
            logResult(platformId, result)
            Log.i(TAG, "runUserRefresh network done platform=$platformId")

            val elapsed = System.currentTimeMillis() - startedAt
            val remain = MIN_SPINNER_VISIBLE_MS - elapsed
            if (remain > 0L) {
                delay(remain)
            }
            result
        } catch (t: Throwable) {
            Log.e(TAG, "runUserRefresh failed platform=$platformId", t)
            throw t
        } finally {
            settings.setRefreshIconPhase(platformId, RefreshIconPhase.Idle)
            Log.i(TAG, "runUserRefresh finally phase=Idle platform=$platformId")
            updateWidgetSerialized(context, "idle")
        }
    }

    suspend fun runBackgroundRefresh(context: Context): BalanceRefreshResult =
        backgroundRefreshFlight.run {
            runBackgroundRefreshOnce(context.applicationContext)
        }

    private suspend fun runBackgroundRefreshOnce(context: Context): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        Log.i(TAG, "runBackgroundRefresh start")
        return try {
            val results = app.refreshInteractor.refreshAllConfigured()
            results.forEachIndexed { index, result ->
                // Prefer logging OpenCode / DeepSeek errors for diagnosis.
                logResult("configured[$index]", result)
            }
            Log.i(TAG, "runBackgroundRefresh done count=${results.size}")
            results.firstOrNull { result ->
                result is BalanceRefreshResult.TransientFailure && result.retryable
            } ?: results.lastOrNull()
                ?: BalanceRefreshResult.Completed(WidgetDisplayState.NotConfigured)
        } finally {
            updateWidgetSerialized(context, "background-refresh")
        }
    }

    suspend fun forceIdle(context: Context, platformId: String? = null) {
        val app = context.applicationContext as QuotaWidgetApp
        if (platformId == null) {
            app.settingsRepository.clearAllRefreshIconPhases()
            Log.i(TAG, "forceIdle all platforms")
        } else {
            app.settingsRepository.setRefreshIconPhase(platformId, RefreshIconPhase.Idle)
            Log.i(TAG, "forceIdle platform=$platformId")
        }
        updateWidgetSerialized(context, "forceIdle")
    }

    private fun logResult(platformId: String, result: BalanceRefreshResult) {
        when (val state = when (result) {
            is BalanceRefreshResult.Completed -> result.state
            is BalanceRefreshResult.TransientFailure -> result.retained
        }) {
            is WidgetDisplayState.Error ->
                Log.e(TAG, "refresh error platform=$platformId message=${state.message}")
            WidgetDisplayState.NeedsReauth ->
                Log.w(TAG, "refresh needsReauth platform=$platformId")
            is WidgetDisplayState.Success ->
                Log.i(TAG, "refresh success platform=$platformId display=${state.snapshot.primaryDisplay}")
            else ->
                Log.i(TAG, "refresh done platform=$platformId state=${state::class.simpleName}")
        }
    }

    private suspend fun updateWidgetSerialized(context: Context, reason: String) {
        widgetUpdateMutex.withLock {
            WidgetGlanceState.syncAndUpdate(context, reason)
        }
    }
}
