package com.kuyermqi.quotawidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker

/**
 * After unlock, enqueue a background balance refresh so a lock-screen failure can recover
 * without requiring a manual tap.
 */
class UnlockRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return
        Log.i(TAG, "USER_PRESENT; enqueue unlock refresh")
        WorkManager.getInstance(context).enqueueUniqueWork(
            BalanceRefreshWorker.UNIQUE_UNLOCK_REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            BalanceRefreshWork.oneTime(),
        )
    }

    companion object {
        private const val TAG = "QuotaRefresh"
    }
}
