package com.kuyermqi.quotawidget

import android.app.Application
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.provider.CodexQuotaProvider
import com.kuyermqi.quotawidget.provider.DeepSeekQuotaProvider
import com.kuyermqi.quotawidget.provider.KimiCodeQuotaProvider
import com.kuyermqi.quotawidget.provider.MagpieQuotaProvider
import com.kuyermqi.quotawidget.provider.NewApiQuotaProvider
import com.kuyermqi.quotawidget.provider.OpenCodeGoQuotaProvider
import com.kuyermqi.quotawidget.provider.ZhipuQuotaProvider
import com.kuyermqi.quotawidget.provider.sessionQuotaProviders
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.settings.AndroidPlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.update.UpdateCheckInteractor
import com.kuyermqi.quotawidget.widget.PeriodicRefreshScheduler
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
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
            val appSettings = settingsRepository.getAppSettings()
            AppLocale.update(appSettings.appLanguage)
            AppNightMode.apply(
                this@QuotaWidgetApp,
                appSettings.darkThemeMode,
            )
        }
        refreshInteractor = BalanceRefreshInteractor(
            settingsRepository = settingsRepository,
            providers = listOf(
                DeepSeekQuotaProvider(),
                OpenCodeGoQuotaProvider(),
                CodexQuotaProvider(),
                NewApiQuotaProvider(),
                MagpieQuotaProvider(),
                KimiCodeQuotaProvider(),
                ZhipuQuotaProvider(),
            ) + sessionQuotaProviders(),
        )
        updateCheckInteractor = UpdateCheckInteractor(
            settingsRepository = settingsRepository,
        )
        // Clear any stuck spinner left by a killed ActionCallback / worker.
        appScope.launch {
            settingsRepository.clearAllRefreshIconPhases()
            android.util.Log.i("QuotaRefresh", "onCreate cleared refresh phases")
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
        appScope.launch {
            settingsRepository.observeAppSettings()
                .map { it.appLanguage }
                .distinctUntilChanged()
                .collect { language ->
                    val changed = withContext(Dispatchers.Main.immediate) {
                        AppLocale.update(language).also { changed ->
                            if (changed) AppLocale.recreateActivities()
                        }
                    }
                    if (changed) {
                        WidgetGlanceState.syncAndUpdate(this@QuotaWidgetApp, "app_language")
                    }
                }
        }
    }

    companion object {
        lateinit var instance: QuotaWidgetApp
            private set
    }
}
