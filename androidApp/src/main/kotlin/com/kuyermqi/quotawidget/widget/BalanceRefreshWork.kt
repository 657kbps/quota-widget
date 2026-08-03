package com.kuyermqi.quotawidget.widget

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker

object BalanceRefreshWork {
    val networkConstraints: Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun oneTime(
        userInitiated: Boolean = false,
        expedited: Boolean = false,
        platformId: String? = null,
    ): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<BalanceRefreshWorker>()
            .setConstraints(networkConstraints)
            .setInputData(
                BalanceRefreshWorker.input(
                    userInitiated = userInitiated,
                    platformId = platformId,
                ),
            )
        if (expedited) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        return builder.build()
    }
}
