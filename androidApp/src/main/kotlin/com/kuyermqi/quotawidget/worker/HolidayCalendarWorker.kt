package com.kuyermqi.quotawidget.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.widget.DeepSeekPeakValleyCoordinator
import kotlinx.coroutines.CancellationException

class HolidayCalendarWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as QuotaWidgetApp
        if (!app.settingsRepository.isDeepSeekPeakValleyEnabled()) {
            return Result.success()
        }
        val result = try {
            app.holidayCalendarRepository.update()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.w(TAG, "holiday calendar update failed", error)
            return Result.success()
        }
        android.util.Log.i(
            TAG,
            "holiday calendar updated=${result.updatedYears} " +
                "notPublished=${result.notPublishedYears} failed=${result.failedYears}",
        )
        DeepSeekPeakValleyCoordinator.refresh(applicationContext, "holiday_calendar_worker")
        return Result.success()
    }

    private companion object {
        const val TAG = "QuotaRefresh"
    }
}
