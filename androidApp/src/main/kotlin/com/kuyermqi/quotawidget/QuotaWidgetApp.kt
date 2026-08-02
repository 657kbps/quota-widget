package com.kuyermqi.quotawidget

import android.app.Application
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.settings.AndroidPlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.update.UpdateCheckInteractor
import com.kuyermqi.quotawidget.widget.BalanceRefreshWork
import com.kuyermqi.quotawidget.widget.PeriodicRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class QuotaWidgetApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: PlatformSettingsRepository
        private set

    lateinit var refreshInteractor: BalanceRefreshInteractor
        private set

    lateinit var updateCheckInteractor: UpdateCheckInteractor
        private set

    override fun onCreate() {
        // Prefs first (sync), then DataStore — must finish before any Activity draws.
        AppNightMode.apply(this, AppNightMode.storedMode(this))
        super.onCreate()
        instance = this
        settingsRepository = AndroidPlatformSettingsRepository(this)
        // One-shot bootstrap so upgrades (prefs empty, DataStore has mode) don't flash.
        runBlocking {
            AppNightMode.apply(
                this@QuotaWidgetApp,
                settingsRepository.getAppSettings().darkThemeMode,
            )
        }
        refreshInteractor = BalanceRefreshInteractor(
            settingsRepository = settingsRepository,
            deepSeekClient = DeepSeekBalanceClient(),
        )
        updateCheckInteractor = UpdateCheckInteractor(
            settingsRepository = settingsRepository,
        )
        // Clear any stuck spinner left by a killed ActionCallback / worker.
        appScope.launch {
            val phase = settingsRepository.getRefreshIconPhase()
            if (phase != RefreshIconPhase.Idle) {
                android.util.Log.i("QuotaRefresh", "onCreate clearing stale phase=$phase")
                settingsRepository.setRefreshIconPhase(RefreshIconPhase.Idle)
            }
            val interval = settingsRepository.getAppSettings().refreshIntervalMinutes
            PeriodicRefreshScheduler.schedule(this@QuotaWidgetApp, interval)
        }
        appScope.launch {
            settingsRepository.observeAppSettings()
                .map { it.darkThemeMode }
                .distinctUntilChanged()
                .collect { mode ->
                    withContext(Dispatchers.Main.immediate) {
                        AppNightMode.apply(this@QuotaWidgetApp, mode)
                    }
                }
        }
        WorkManager.getInstance(this).enqueue(BalanceRefreshWork.oneTime())
    }

    companion object {
        lateinit var instance: QuotaWidgetApp
            private set
    }
}
