package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.decodeQuotaWindows
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.NewApiSettings
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

/**
 * Glance [update] often recomposes without re-entering [GlanceAppWidget.provideGlance].
 * Widget UI must therefore read from Glance state ([currentState]), which we sync from
 * the app repository before each update.
 */
object WidgetGlanceState {
    private const val TAG = "QuotaRefresh"

    val refreshPhaseKey = stringPreferencesKey("qw_refresh_phase")
    val statusKey = stringPreferencesKey("qw_status")
    val errorKey = stringPreferencesKey("qw_error")
    val platformIdKey = stringPreferencesKey("qw_platform_id")
    val platformNameKey = stringPreferencesKey("qw_platform_name")
    val currencyKey = stringPreferencesKey("qw_currency")
    val totalKey = stringPreferencesKey("qw_total")
    val formattedKey = stringPreferencesKey("qw_formatted")
    val windowsKey = stringPreferencesKey("qw_windows")
    val updatedAtKey = longPreferencesKey("qw_updated_at")
    val unlimitedKey = booleanPreferencesKey("qw_unlimited")
    val usedDisplayKey = stringPreferencesKey("qw_used_display")
    val emptyLimitedQuotaKey = booleanPreferencesKey("qw_empty_limited_quota")
    val tokenExpiredKey = booleanPreferencesKey("qw_token_expired")
    val quotaOverspentKey = booleanPreferencesKey("qw_quota_overspent")
    val darkThemeModeKey = stringPreferencesKey("qw_dark_theme_mode")
    val themeColorModeKey = stringPreferencesKey("qw_theme_color_mode")
    val seedColorKey = intPreferencesKey("qw_seed_color")
    val openCodeWindowKindKey = stringPreferencesKey("qw_opencode_window_kind")
    val openCodeUsageDisplayModeKey = stringPreferencesKey("qw_opencode_usage_display")
    val openCodeUsageProgressStyleKey = stringPreferencesKey("qw_opencode_usage_progress_style")
    val codexWindowKindKey = stringPreferencesKey("qw_codex_window_kind")
    val codexUsageDisplayModeKey = stringPreferencesKey("qw_codex_usage_display")
    val codexUsageProgressStyleKey = stringPreferencesKey("qw_codex_usage_progress_style")
    val newApiUsageDisplayModeKey = stringPreferencesKey("qw_new_api_usage_display")
    val newApiUsageProgressStyleKey = stringPreferencesKey("qw_new_api_usage_progress_style")

    private object Status {
        const val NOT_CONFIGURED = "not_configured"
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val NEEDS_REAUTH = "needs_reauth"
    }

    private data class Target(
        val widget: GlanceAppWidget,
        val clazz: Class<out GlanceAppWidget>,
        val platformId: String,
    )

    private fun targets(): List<Target> = listOf(
        Target(DeepSeekBalanceWidget(), DeepSeekBalanceWidget::class.java, PlatformIds.DEEPSEEK),
        Target(
            DeepSeekBalanceCompactWidget(),
            DeepSeekBalanceCompactWidget::class.java,
            PlatformIds.DEEPSEEK,
        ),
        Target(OpenCodeGoWidget(), OpenCodeGoWidget::class.java, PlatformIds.OPENCODE_GO),
        Target(
            OpenCodeGoCompactWidget(),
            OpenCodeGoCompactWidget::class.java,
            PlatformIds.OPENCODE_GO,
        ),
        Target(
            OpenCodeGoOverviewWidget(),
            OpenCodeGoOverviewWidget::class.java,
            PlatformIds.OPENCODE_GO,
        ),
        Target(CodexWidget(), CodexWidget::class.java, PlatformIds.CODEX),
        Target(CodexCompactWidget(), CodexCompactWidget::class.java, PlatformIds.CODEX),
        Target(CodexOverviewWidget(), CodexOverviewWidget::class.java, PlatformIds.CODEX),
        Target(NewApiBalanceWidget(), NewApiBalanceWidget::class.java, PlatformIds.NEW_API),
        Target(
            NewApiBalanceCompactWidget(),
            NewApiBalanceCompactWidget::class.java,
            PlatformIds.NEW_API,
        ),
        Target(NewApiUsageWidget(), NewApiUsageWidget::class.java, PlatformIds.NEW_API),
    )

    suspend fun syncAndUpdate(context: Context, reason: String) {
        val app = context.applicationContext as QuotaWidgetApp
        val repo = app.settingsRepository
        val appSettings = repo.getAppSettings()
        val openCodeSettings = repo.getOpenCodeGoSettings()
        val codexSettings = repo.getCodexSettings()
        val newApiSettings = repo.getNewApiSettings()
        Log.i(TAG, "syncAndUpdate reason=$reason")

        val manager = GlanceAppWidgetManager(context)
        var updated = 0
        for (target in targets()) {
            val display = repo.getWidgetState(target.platformId)
            val phase = repo.getRefreshIconPhase(target.platformId)
            val ids = manager.getGlanceIds(target.clazz)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        write(
                            phase = phase,
                            display = display,
                            appSettings = appSettings,
                            openCodeSettings = openCodeSettings.takeIf {
                                target.platformId == PlatformIds.OPENCODE_GO
                            },
                            codexSettings = codexSettings.takeIf {
                                target.platformId == PlatformIds.CODEX
                            },
                            newApiSettings = newApiSettings.takeIf {
                                target.platformId == PlatformIds.NEW_API
                            },
                        )
                    }
                }
                target.widget.update(context, id)
                updated++
                Log.i(
                    TAG,
                    "syncAndUpdate applied id=$id type=${target.clazz.simpleName} " +
                        "platform=${target.platformId} phase=$phase reason=$reason",
                )
            }
        }
        if (updated == 0) {
            Log.w(TAG, "syncAndUpdate no glance ids")
        }
    }

    /**
     * Always copy the latest repository state into this Glance id.
     * Returns the display state that was written (from the app repository).
     */
    suspend fun syncFromRepository(
        context: Context,
        id: GlanceId,
        repo: PlatformSettingsRepository,
        platformId: String,
    ): WidgetDisplayState {
        val phase = repo.getRefreshIconPhase(platformId)
        val display = repo.getWidgetState(platformId)
        val appSettings = repo.getAppSettings()
        val openCodeSettings = if (platformId == PlatformIds.OPENCODE_GO) {
            repo.getOpenCodeGoSettings()
        } else {
            null
        }
        val codexSettings = if (platformId == PlatformIds.CODEX) {
            repo.getCodexSettings()
        } else {
            null
        }
        val newApiSettings = if (platformId == PlatformIds.NEW_API) {
            repo.getNewApiSettings()
        } else {
            null
        }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                write(phase, display, appSettings, openCodeSettings, codexSettings, newApiSettings)
            }
        }
        val verify = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        Log.i(
            TAG,
            "syncFromRepository id=$id platform=$platformId repo=${display::class.simpleName} " +
                "glanceStatus=${verify[statusKey]} glanceFormatted=${verify[formattedKey]}",
        )
        return display
    }

    fun Preferences.toRefreshPhase(): RefreshIconPhase =
        RefreshIconPhase.fromStorage(this[refreshPhaseKey])

    fun Preferences.toAppThemeSettings(): AppSettings = AppSettings(
        darkThemeMode = DarkThemeMode.fromStorage(this[darkThemeModeKey]),
        themeColorMode = ThemeColorMode.fromStorage(this[themeColorModeKey]),
        customSeedColorArgb = this[seedColorKey] ?: DEFAULT_CUSTOM_SEED_COLOR_ARGB,
        refreshIntervalMinutes = DEFAULT_REFRESH_INTERVAL_MINUTES,
    )

    fun Preferences.toOpenCodeUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[openCodeWindowKindKey])

    fun Preferences.toOpenCodeUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[openCodeUsageDisplayModeKey])

    fun Preferences.toOpenCodeUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[openCodeUsageProgressStyleKey])

    fun Preferences.toCodexUsageWindowKind(): UsageWindowKind =
        this[codexWindowKindKey]
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: UsageWindowKind.WEEKLY

    fun Preferences.toCodexUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[codexUsageDisplayModeKey])

    fun Preferences.toCodexUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[codexUsageProgressStyleKey])

    fun Preferences.toNewApiUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[newApiUsageDisplayModeKey])

    fun Preferences.toNewApiUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[newApiUsageProgressStyleKey])

    fun Preferences.toDisplayState(): WidgetDisplayState {
        return when (this[statusKey]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(this[errorKey] ?: "刷新失败")
            Status.SUCCESS -> {
                val platformId = this[platformIdKey] ?: PlatformIds.DEEPSEEK
                val currency = CurrencyPreference.fromStorage(this[currencyKey])
                val total = this[totalKey] ?: "0"
                val primary = this[formattedKey]
                    ?: if (platformId == PlatformIds.DEEPSEEK) {
                        formatBalance(currency, total)
                    } else {
                        ""
                    }
                val windows = decodeQuotaWindows(this[windowsKey])
                    ?: if (platformId == PlatformIds.DEEPSEEK) {
                        listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE))
                    } else {
                        emptyList()
                    }
                WidgetDisplayState.Success(
                    QuotaSnapshot(
                        platformId = platformId,
                        platformName = this[platformNameKey]
                            ?: PlatformRegistry.displayName(platformId),
                        windows = windows,
                        primaryDisplay = primary,
                        updatedAtEpochMs = this[updatedAtKey] ?: 0L,
                        currency = currency,
                        totalBalance = total,
                        unlimitedQuota = this[unlimitedKey] == true,
                        usedDisplay = this[usedDisplayKey].orEmpty(),
                        emptyLimitedQuota = this[emptyLimitedQuotaKey] == true,
                        tokenExpired = this[tokenExpiredKey] == true,
                        quotaOverspent = this[quotaOverspentKey] == true,
                    ),
                )
            }
            else -> WidgetDisplayState.NotConfigured
        }
    }

    private fun MutablePreferences.write(
        phase: RefreshIconPhase,
        display: WidgetDisplayState,
        appSettings: AppSettings,
        openCodeSettings: OpenCodeGoSettings?,
        codexSettings: CodexSettings?,
        newApiSettings: NewApiSettings?,
    ) {
        this[refreshPhaseKey] = phase.name
        this[darkThemeModeKey] = appSettings.darkThemeMode.name
        this[themeColorModeKey] = appSettings.themeColorMode.name
        this[seedColorKey] = appSettings.customSeedColorArgb
        if (openCodeSettings != null) {
            this[openCodeWindowKindKey] = openCodeSettings.widgetWindowKind.name
            this[openCodeUsageDisplayModeKey] = openCodeSettings.usageDisplayMode.name
            this[openCodeUsageProgressStyleKey] = openCodeSettings.usageProgressStyle.name
        }
        if (codexSettings != null) {
            this[codexWindowKindKey] = codexSettings.widgetWindowKind.name
            this[codexUsageDisplayModeKey] = codexSettings.usageDisplayMode.name
            this[codexUsageProgressStyleKey] = codexSettings.usageProgressStyle.name
        }
        if (newApiSettings != null) {
            this[newApiUsageDisplayModeKey] = newApiSettings.usageDisplayMode.name
            this[newApiUsageProgressStyleKey] = newApiSettings.usageProgressStyle.name
        }
        when (display) {
            WidgetDisplayState.NotConfigured -> {
                this[statusKey] = Status.NOT_CONFIGURED
                remove(errorKey)
            }
            WidgetDisplayState.Loading -> {
                this[statusKey] = Status.LOADING
                remove(errorKey)
            }
            WidgetDisplayState.NeedsReauth -> {
                this[statusKey] = Status.NEEDS_REAUTH
                remove(errorKey)
            }
            is WidgetDisplayState.Error -> {
                this[statusKey] = Status.ERROR
                this[errorKey] = display.message
            }
            is WidgetDisplayState.Success -> {
                this[statusKey] = Status.SUCCESS
                this[platformIdKey] = display.snapshot.platformId
                this[platformNameKey] = display.snapshot.platformName
                this[currencyKey] = display.snapshot.currency.name
                this[totalKey] = display.snapshot.totalBalance
                this[formattedKey] = display.snapshot.primaryDisplay
                this[windowsKey] = encodeQuotaWindows(display.snapshot.windows)
                this[updatedAtKey] = display.snapshot.updatedAtEpochMs
                this[unlimitedKey] = display.snapshot.unlimitedQuota
                this[emptyLimitedQuotaKey] = display.snapshot.emptyLimitedQuota
                this[tokenExpiredKey] = display.snapshot.tokenExpired
                this[quotaOverspentKey] = display.snapshot.quotaOverspent
                if (display.snapshot.usedDisplay.isBlank()) {
                    remove(usedDisplayKey)
                } else {
                    this[usedDisplayKey] = display.snapshot.usedDisplay
                }
                remove(errorKey)
            }
        }
    }
}
