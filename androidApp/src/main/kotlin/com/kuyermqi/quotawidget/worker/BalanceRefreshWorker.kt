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
        val platformId = inputData.getString(KEY_PLATFORM_ID)
        android.util.Log.i(
            TAG,
            "doWork start userInitiated=$userInitiated platformId=$platformId id=$id",
        )
        return try {
            val refreshResult = if (userInitiated && !platformId.isNullOrBlank()) {
                WidgetRefreshCoordinator.runUserRefresh(applicationContext, platformId)
            } else {
                WidgetRefreshCoordinator.runBackgroundRefresh(applicationContext)
            }
            when (refreshResult) {
                is BalanceRefreshResult.Completed -> {
                    android.util.Log.i(
                        TAG,
                        "doWork success userInitiated=$userInitiated platformId=$platformId",
                    )
                    Result.success()
                }
                is BalanceRefreshResult.TransientFailure -> {
                    android.util.Log.w(
                        TAG,
                        "doWork transient failure userInitiated=$userInitiated " +
                            "platformId=$platformId retained=${refreshResult.retained::class.simpleName}",
                    )
                    Result.retry()
                }
            }
        } catch (t: Exception) {
            android.util.Log.e(TAG, "doWork failed userInitiated=$userInitiated platformId=$platformId", t)
            try {
                WidgetRefreshCoordinator.forceIdle(applicationContext, platformId)
            } catch (_: Exception) {
                // ignore cleanup failures
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "QuotaRefresh"
        const val UNIQUE_WORK_NAME = "quota_balance_refresh"
        const val KEY_USER_INITIATED = "user_initiated"
        const val KEY_PLATFORM_ID = "platform_id"

        fun userRefreshWorkName(platformId: String): String =
            "quota_balance_user_refresh_$platformId"

        fun input(
            userInitiated: Boolean = false,
            platformId: String? = null,
        ): Data =
            Data.Builder()
                .putBoolean(KEY_USER_INITIATED, userInitiated)
                .apply {
                    if (!platformId.isNullOrBlank()) {
                        putString(KEY_PLATFORM_ID, platformId)
                    }
                }
                .build()
    }
}
