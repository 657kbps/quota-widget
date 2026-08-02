package com.kuyermqi.quotawidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.widget.WidgetRefreshCoordinator

class BalanceRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userInitiated = inputData.getBoolean(KEY_USER_INITIATED, false)
        android.util.Log.i(TAG, "doWork start userInitiated=$userInitiated id=$id")
        return try {
            val refreshResult = if (userInitiated) {
                WidgetRefreshCoordinator.runUserRefresh(applicationContext)
            } else {
                WidgetRefreshCoordinator.runBackgroundRefresh(applicationContext)
            }
            when (refreshResult) {
                is BalanceRefreshResult.Completed -> {
                    android.util.Log.i(TAG, "doWork success userInitiated=$userInitiated")
                    Result.success()
                }
                is BalanceRefreshResult.TransientFailure -> {
                    android.util.Log.w(
                        TAG,
                        "doWork transient failure userInitiated=$userInitiated " +
                            "retained=${refreshResult.retained::class.simpleName}",
                    )
                    Result.retry()
                }
            }
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
        const val UNIQUE_UNLOCK_REFRESH_WORK = "deepseek_balance_unlock_refresh"
        const val KEY_USER_INITIATED = "user_initiated"

        fun userRefreshInput(): Data =
            Data.Builder().putBoolean(KEY_USER_INITIATED, true).build()
    }
}
