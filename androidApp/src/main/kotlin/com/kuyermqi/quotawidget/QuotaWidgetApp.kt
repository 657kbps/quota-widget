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
import com.kuyermqi.quotawidget.widget.RefreshScheduleGate
import com.kuyermqi.quotawidget.widget.WidgetSyncCoordinator
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
        // Reset only spinner phases whose owning run is gone (killed process); a live
        // refresh writes a fresh start token and is therefore left spinning.
        appScope.launch {
            settingsRepository.clearStaleRefreshIconPhases()
            android.util.Log.i("QuotaRefresh", "onCreate cleared stale refresh phases")
            RefreshScheduleGate.sync(this@QuotaWidgetApp)
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
                        WidgetSyncCoordinator.syncWidgets(this@QuotaWidgetApp, "app_language")
                    }
                }
        }
    }

    companion object {
        lateinit var instance: QuotaWidgetApp
            private set
    }
}
