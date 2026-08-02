package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.refresh.displayState
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetRefreshCoordinator {
    private const val TAG = "QuotaRefresh"
    private const val STALE_SPIN_TIMEOUT_MS = 45_000L
    private const val MIN_SPINNER_VISIBLE_MS = 450L

    private val widgetUpdateMutex = Mutex()

    /**
     * ActionCallback entry: enqueue work only. Glance updates happen in the worker
     * via [WidgetGlanceState.syncAndUpdate] so recomposition sees fresh Glance state.
     */
    suspend fun beginUserRefresh(context: Context) {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val phase = settings.getRefreshIconPhase()
        Log.i(TAG, "beginUserRefresh phase=$phase")
        if (phase == RefreshIconPhase.Spinning || phase == RefreshIconPhase.Settling) {
            val startedAt = settings.getRefreshStartedAtEpochMs()
            val age = System.currentTimeMillis() - startedAt
            if (startedAt > 0L && age in 0 until STALE_SPIN_TIMEOUT_MS) {
                Log.w(TAG, "beginUserRefresh ignored; still refreshing ageMs=$age")
                return
            }
            Log.w(TAG, "beginUserRefresh stale phase=$phase ageMs=$age; continuing")
        }

        settings.setRefreshStartedAtEpochMs(System.currentTimeMillis())
        settings.setRefreshIconPhase(RefreshIconPhase.Spinning)
        updateWidgetSerialized(context, "spinning-pre-enqueue")
        Log.i(TAG, "beginUserRefresh enqueued user work")

        WorkManager.getInstance(context).enqueueUniqueWork(
            BalanceRefreshWorker.UNIQUE_USER_REFRESH_WORK,
            ExistingWorkPolicy.REPLACE,
            BalanceRefreshWork.oneTime(userInitiated = true, expedited = true),
        )
    }

    suspend fun runUserRefresh(context: Context): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        val settings = app.settingsRepository
        val startedAt = settings.getRefreshStartedAtEpochMs().takeIf { it > 0L }
            ?: System.currentTimeMillis().also { settings.setRefreshStartedAtEpochMs(it) }
        Log.i(TAG, "runUserRefresh start")
        return try {
            settings.setRefreshIconPhase(RefreshIconPhase.Spinning)
            updateWidgetSerialized(context, "spinning")

            val result = app.refreshInteractor.refreshDeepSeek()
            Log.i(
                TAG,
                "runUserRefresh network done result=${result::class.simpleName} " +
                    "state=${result.displayState::class.simpleName}",
            )

            val elapsed = System.currentTimeMillis() - startedAt
            val remain = MIN_SPINNER_VISIBLE_MS - elapsed
            if (remain > 0L) {
                delay(remain)
            }
            result
        } catch (t: Throwable) {
            Log.e(TAG, "runUserRefresh failed", t)
            throw t
        } finally {
            settings.setRefreshIconPhase(RefreshIconPhase.Idle)
            Log.i(TAG, "runUserRefresh finally phase=Idle")
            updateWidgetSerialized(context, "idle")
        }
    }

    suspend fun runBackgroundRefresh(context: Context): BalanceRefreshResult {
        val app = context.applicationContext as QuotaWidgetApp
        Log.i(TAG, "runBackgroundRefresh start")
        return try {
            val result = app.refreshInteractor.refreshDeepSeek()
            Log.i(
                TAG,
                "runBackgroundRefresh done result=${result::class.simpleName} " +
                    "state=${result.displayState::class.simpleName}",
            )
            result
        } finally {
            // Do not force Idle: a widget-initiated refresh may still own the spinner.
            val phase = app.settingsRepository.getRefreshIconPhase()
            if (phase == RefreshIconPhase.Spinning || phase == RefreshIconPhase.Settling) {
                updateWidgetSerialized(context, "background-refresh-keep-phase")
            } else {
                updateWidgetSerialized(context, "background-refresh")
            }
        }
    }

    suspend fun forceIdle(context: Context) {
        val app = context.applicationContext as QuotaWidgetApp
        app.settingsRepository.setRefreshIconPhase(RefreshIconPhase.Idle)
        Log.i(TAG, "forceIdle")
        updateWidgetSerialized(context, "forceIdle")
    }

    private suspend fun updateWidgetSerialized(context: Context, reason: String) {
        widgetUpdateMutex.withLock {
            WidgetGlanceState.syncAndUpdate(context, reason)
        }
    }
}
