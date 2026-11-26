package com.kuyermqi.quotawidget

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kuyermqi.quotawidget.antigravity.AntigravityClient
import com.kuyermqi.quotawidget.cline.ClineClient
import com.kuyermqi.quotawidget.codex.CodexOAuth
import com.kuyermqi.quotawidget.codex.CodexUsageClient
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import com.kuyermqi.quotawidget.debug.shouldSkipRefreshForDebugFake
import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.kimi.KimiCodeUsageClient
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.magpie.MagpieQuotaClient
import com.kuyermqi.quotawidget.newapi.NewApiUsageClient
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
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
import com.kuyermqi.quotawidget.ui.home.HomeViewModel
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.update.GitHubReleaseClient
import com.kuyermqi.quotawidget.update.UpdateCheckInteractor
import com.kuyermqi.quotawidget.widget.RefreshScheduleGate
import com.kuyermqi.quotawidget.widget.WidgetSyncCoordinator
import com.kuyermqi.quotawidget.zhipu.ZhipuUsageClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuotaWidgetApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The single Ktor client of this process: every provider, login flow and update check
     * borrows it, so nobody may close it — its lifetime is the process lifetime.
     */
    val httpClient: HttpClient = createHttpClient()

    lateinit var settingsRepository: PlatformSettingsRepository
        private set

    lateinit var refreshInteractor: BalanceRefreshInteractor
        private set

    lateinit var updateCheckInteractor: UpdateCheckInteractor
        private set

    /**
     * Home edit state lives in a view model that survives configuration changes; the factory is
     * built here because this class is the manual dependency graph.
     */
    val homeViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    settingsRepository = settingsRepository,
                    openCodeClient = OpenCodeGoClient(httpClient),
                )
            }
        }
    }

    override fun onCreate() {
        // SharedPreferences mirrors first (synchronous), then DataStore: the observers below
        // correct a mirror that a killed process left behind before its collector ran.
        AppNightMode.apply(this, AppNightMode.storedMode(this))
        AppLocale.apply(this, AppLocale.storedLanguage(this))
        super.onCreate()
        instance = this
        settingsRepository = AndroidPlatformSettingsRepository(this)
        refreshInteractor = BalanceRefreshInteractor(
            settingsRepository = settingsRepository,
            providers = listOf(
                DeepSeekQuotaProvider(DeepSeekBalanceClient(httpClient)),
                OpenCodeGoQuotaProvider(OpenCodeGoClient(httpClient)),
                CodexQuotaProvider(CodexOAuth(httpClient), CodexUsageClient(httpClient)),
                NewApiQuotaProvider(NewApiUsageClient(httpClient)),
                MagpieQuotaProvider(MagpieQuotaClient(httpClient)),
                KimiCodeQuotaProvider(KimiCodeUsageClient(httpClient)),
                ZhipuQuotaProvider(ZhipuUsageClient(httpClient)),
            ) + sessionQuotaProviders(httpClient),
            shouldSkipRefresh = { platformId ->
                shouldSkipRefreshForDebugFake(settingsRepository, platformId)
            },
        )
        updateCheckInteractor = UpdateCheckInteractor(
            settingsRepository = settingsRepository,
            releaseClient = GitHubReleaseClient(httpClient),
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
                        AppLocale.apply(this@QuotaWidgetApp, language).also { changed ->
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
