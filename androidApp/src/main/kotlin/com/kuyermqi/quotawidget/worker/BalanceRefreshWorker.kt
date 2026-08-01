package com.kuyermqi.quotawidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.kuyermqi.quotawidget.widget.WidgetRefreshCoordinator

class BalanceRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userInitiated = inputData.getBoolean(KEY_USER_INITIATED, false)
        android.util.Log.i(TAG, "doWork start userInitiated=$userInitiated id=$id")
        return try {
            if (userInitiated) {
                WidgetRefreshCoordinator.runUserRefresh(applicationContext)
            } else {
                WidgetRefreshCoordinator.runBackgroundRefresh(applicationContext)
            }
            android.util.Log.i(TAG, "doWork success userInitiated=$userInitiated")
            Result.success()
        } catch (t: Exception) {
            android.util.Log.e(TAG, "doWork failed userInitiated=$userInitiated", t)
            try {
                WidgetRefreshCoordinator.forceIdle(applicationContext)
            } catch (_: Exception) {
                // ignore cleanup failures
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "QuotaRefresh"
        const val UNIQUE_WORK_NAME = "deepseek_balance_refresh"
        const val UNIQUE_USER_REFRESH_WORK = "deepseek_balance_user_refresh"
        const val KEY_USER_INITIATED = "user_initiated"

        fun userRefreshInput(): Data =
            Data.Builder().putBoolean(KEY_USER_INITIATED, true).build()
    }
}
