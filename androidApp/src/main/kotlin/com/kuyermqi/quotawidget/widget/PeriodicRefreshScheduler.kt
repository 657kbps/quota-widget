package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import java.util.concurrent.TimeUnit

object PeriodicRefreshScheduler {
    fun schedule(context: Context, intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(BalanceRefreshWork.networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BalanceRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
