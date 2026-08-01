package com.kuyermqi.quotawidget

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.settings.AndroidPlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class QuotaWidgetApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: PlatformSettingsRepository
        private set

    lateinit var refreshInteractor: BalanceRefreshInteractor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = AndroidPlatformSettingsRepository(this)
        refreshInteractor = BalanceRefreshInteractor(
            settingsRepository = settingsRepository,
            deepSeekClient = DeepSeekBalanceClient(),
        )
        // Clear any stuck spinner left by a killed ActionCallback / worker.
        appScope.launch {
            val phase = settingsRepository.getRefreshIconPhase()
            if (phase != RefreshIconPhase.Idle) {
                android.util.Log.i("QuotaRefresh", "onCreate clearing stale phase=$phase")
                settingsRepository.setRefreshIconPhase(RefreshIconPhase.Idle)
            }
        }
        schedulePeriodicRefresh()
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<BalanceRefreshWorker>().build(),
        )
    }

    private fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BalanceRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        lateinit var instance: QuotaWidgetApp
            private set
    }
}
