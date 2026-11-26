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
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
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
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.settings.WidgetCardSettings

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
    val appLanguageKey = stringPreferencesKey("qw_app_language")
    val openCodeWindowKindKey = stringPreferencesKey("qw_opencode_window_kind")
    val openCodeUsageDisplayModeKey = stringPreferencesKey("qw_opencode_usage_display")
    val openCodeUsageProgressStyleKey = stringPreferencesKey("qw_opencode_usage_progress_style")
    val openCodeUsageResetStyleKey = stringPreferencesKey("qw_opencode_usage_reset_style")
    val codexWindowKindKey = stringPreferencesKey("qw_codex_window_kind")
    val codexUsageDisplayModeKey = stringPreferencesKey("qw_codex_usage_display")
    val codexUsageProgressStyleKey = stringPreferencesKey("qw_codex_usage_progress_style")
    val codexUsageResetStyleKey = stringPreferencesKey("qw_codex_usage_reset_style")
    val newApiUsageDisplayModeKey = stringPreferencesKey("qw_new_api_usage_display")
    val newApiUsageProgressStyleKey = stringPreferencesKey("qw_new_api_usage_progress_style")
    val magpieWindowKindKey = stringPreferencesKey("qw_magpie_window_kind")
    val magpieUsageDisplayModeKey = stringPreferencesKey("qw_magpie_usage_display")
    val magpieUsageProgressStyleKey = stringPreferencesKey("qw_magpie_usage_progress_style")
    val magpieUsageResetStyleKey = stringPreferencesKey("qw_magpie_usage_reset_style")
    val kimiCodeWindowKindKey = stringPreferencesKey("qw_kimi_code_window_kind")
    val kimiCodeUsageDisplayModeKey = stringPreferencesKey("qw_kimi_code_usage_display")
    val kimiCodeUsageProgressStyleKey = stringPreferencesKey("qw_kimi_code_usage_progress_style")
    val kimiCodeUsageResetStyleKey = stringPreferencesKey("qw_kimi_code_usage_reset_style")
    val zhipuWindowKindKey = stringPreferencesKey("qw_zhipu_window_kind")
    val zhipuUsageDisplayModeKey = stringPreferencesKey("qw_zhipu_usage_display")
    val zhipuUsageProgressStyleKey = stringPreferencesKey("qw_zhipu_usage_progress_style")
    val zhipuUsageResetStyleKey = stringPreferencesKey("qw_zhipu_usage_reset_style")

    private fun sessionWindowKindKey(platformId: String) =
        stringPreferencesKey("qw_${platformId}_window_kind")

    private fun sessionUsageDisplayModeKey(platformId: String) =
        stringPreferencesKey("qw_${platformId}_usage_display")

    private fun sessionUsageProgressStyleKey(platformId: String) =
        stringPreferencesKey("qw_${platformId}_usage_progress_style")

    private fun sessionUsageResetStyleKey(platformId: String) =
        stringPreferencesKey("qw_${platformId}_usage_reset_style")

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
        Target(MagpieWidget(), MagpieWidget::class.java, PlatformIds.MAGPIE),
        Target(MagpieCompactWidget(), MagpieCompactWidget::class.java, PlatformIds.MAGPIE),
        Target(MagpieOverviewWidget(), MagpieOverviewWidget::class.java, PlatformIds.MAGPIE),
        Target(KimiCodeWidget(), KimiCodeWidget::class.java, PlatformIds.KIMI_CODE),
        Target(
            KimiCodeCompactWidget(),
            KimiCodeCompactWidget::class.java,
            PlatformIds.KIMI_CODE,
        ),
        Target(
            KimiCodeOverviewWidget(),
            KimiCodeOverviewWidget::class.java,
            PlatformIds.KIMI_CODE,
        ),
        Target(ZhipuWidget(), ZhipuWidget::class.java, PlatformIds.ZHIPU),
        Target(ZhipuCompactWidget(), ZhipuCompactWidget::class.java, PlatformIds.ZHIPU),
        Target(ZhipuOverviewWidget(), ZhipuOverviewWidget::class.java, PlatformIds.ZHIPU),
        Target(WorkBuddyWidget(), WorkBuddyWidget::class.java, PlatformIds.WORKBUDDY),
        Target(
            WorkBuddyCompactWidget(),
            WorkBuddyCompactWidget::class.java,
            PlatformIds.WORKBUDDY,
        ),
        Target(
            WorkBuddyOverviewWidget(),
            WorkBuddyOverviewWidget::class.java,
            PlatformIds.WORKBUDDY,
        ),
        Target(WorkBuddyAiWidget(), WorkBuddyAiWidget::class.java, PlatformIds.WORKBUDDY_AI),
        Target(
            WorkBuddyAiCompactWidget(),
            WorkBuddyAiCompactWidget::class.java,
            PlatformIds.WORKBUDDY_AI,
        ),
        Target(
            WorkBuddyAiOverviewWidget(),
            WorkBuddyAiOverviewWidget::class.java,
            PlatformIds.WORKBUDDY_AI,
        ),
        Target(ClineWidget(), ClineWidget::class.java, PlatformIds.CLINE),
        Target(ClineCompactWidget(), ClineCompactWidget::class.java, PlatformIds.CLINE),
        Target(ClineOverviewWidget(), ClineOverviewWidget::class.java, PlatformIds.CLINE),
        Target(CommandCodeWidget(), CommandCodeWidget::class.java, PlatformIds.COMMAND_CODE),
        Target(
            CommandCodeCompactWidget(),
            CommandCodeCompactWidget::class.java,
            PlatformIds.COMMAND_CODE,
        ),
        Target(
            CommandCodeOverviewWidget(),
            CommandCodeOverviewWidget::class.java,
            PlatformIds.COMMAND_CODE,
        ),
        Target(AntigravityWidget(), AntigravityWidget::class.java, PlatformIds.ANTIGRAVITY),
        Target(
            AntigravityCompactWidget(),
            AntigravityCompactWidget::class.java,
            PlatformIds.ANTIGRAVITY,
        ),
        Target(
            AntigravityOverviewWidget(),
            AntigravityOverviewWidget::class.java,
            PlatformIds.ANTIGRAVITY,
        ),
    )

    internal suspend fun syncAndUpdate(
        context: Context,
        reason: String,
        platformId: String? = null,
    ) {
        val app = context.applicationContext as QuotaWidgetApp
        val manager = GlanceAppWidgetManager(context)
        val placed = placedTargets(manager, platformId)
        if (placed.isEmpty()) {
            Log.w(
                TAG,
                "syncAndUpdate skipped; no widgets placed platform=${platformId ?: "any"} " +
                    "reason=$reason",
            )
            return
        }
        val platformIds = placed.mapTo(mutableSetOf()) { (target, _) -> target.platformId }
        val snapshot = app.settingsRepository.getWidgetRenderSnapshot(platformIds)
        Log.i(TAG, "syncAndUpdate reason=$reason platforms=$platformIds")
        var updated = 0
        for ((target, ids) in placed) {
            val render = snapshot.platforms.getValue(target.platformId)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        write(
                            platformId = target.platformId,
                            phase = render.phase,
                            display = render.display,
                            appSettings = snapshot.appSettings,
                            card = render.card,
                        )
                    }
                }
                target.widget.update(context, id)
                updated++
                Log.i(
                    TAG,
                    "syncAndUpdate applied id=$id type=${target.clazz.simpleName} " +
                        "platform=${target.platformId} phase=${render.phase} reason=$reason",
                )
            }
        }
        if (updated == 0) {
            Log.w(TAG, "syncAndUpdate no glance ids")
        }
    }

    /** Targets that currently have widget instances on a home screen, so nothing else is read. */
    private suspend fun placedTargets(
        manager: GlanceAppWidgetManager,
        platformId: String?,
    ): List<Pair<Target, List<GlanceId>>> =
        targets()
            .filter { platformId == null || it.platformId == platformId }
            .mapNotNull { target ->
                manager.getGlanceIds(target.clazz)
                    .takeIf { it.isNotEmpty() }
                    ?.let { ids -> target to ids }
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
        val snapshot = repo.getWidgetRenderSnapshot(setOf(platformId))
        val render = snapshot.platforms.getValue(platformId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                write(
                    platformId = platformId,
                    phase = render.phase,
                    display = render.display,
                    appSettings = snapshot.appSettings,
                    card = render.card,
                )
            }
        }
        val verify = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        Log.i(
            TAG,
            "syncFromRepository id=$id platform=$platformId " +
                "repo=${render.display::class.simpleName} " +
                "glanceStatus=${verify[statusKey]} glanceFormatted=${verify[formattedKey]}",
        )
        return render.display
    }

    fun Preferences.toRefreshPhase(): RefreshIconPhase =
        RefreshIconPhase.fromStorage(this[refreshPhaseKey])

    fun Preferences.toAppThemeSettings(): AppSettings = AppSettings(
        darkThemeMode = DarkThemeMode.fromStorage(this[darkThemeModeKey]),
        themeColorMode = ThemeColorMode.fromStorage(this[themeColorModeKey]),
        customSeedColorArgb = this[seedColorKey] ?: DEFAULT_CUSTOM_SEED_COLOR_ARGB,
        refreshIntervalMinutes = DEFAULT_REFRESH_INTERVAL_MINUTES,
    )

    fun Preferences.toAppLanguage(): AppLanguage =
        this[appLanguageKey]?.let { AppLanguage.fromStorage(it) } ?: AppLocale.current()

    fun Preferences.toOpenCodeUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[openCodeWindowKindKey])

    fun Preferences.toOpenCodeUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[openCodeUsageDisplayModeKey])

    fun Preferences.toOpenCodeUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[openCodeUsageProgressStyleKey])

    fun Preferences.toOpenCodeUsageResetStyle(): UsageResetStyle =
        UsageResetStyle.fromStorage(this[openCodeUsageResetStyleKey])

    fun Preferences.toCodexUsageWindowKind(): UsageWindowKind =
        this[codexWindowKindKey]
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: UsageWindowKind.WEEKLY

    fun Preferences.toCodexUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[codexUsageDisplayModeKey])

    fun Preferences.toCodexUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[codexUsageProgressStyleKey])

    fun Preferences.toCodexUsageResetStyle(): UsageResetStyle =
        UsageResetStyle.fromStorage(this[codexUsageResetStyleKey])

    fun Preferences.toNewApiUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[newApiUsageDisplayModeKey])

    fun Preferences.toNewApiUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[newApiUsageProgressStyleKey])

    fun Preferences.toMagpieUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[magpieWindowKindKey])

    fun Preferences.toMagpieUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[magpieUsageDisplayModeKey])

    fun Preferences.toMagpieUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[magpieUsageProgressStyleKey])

    fun Preferences.toMagpieUsageResetStyle(): UsageResetStyle =
        UsageResetStyle.fromStorage(this[magpieUsageResetStyleKey])

    fun Preferences.toKimiCodeUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[kimiCodeWindowKindKey])

    fun Preferences.toKimiCodeUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[kimiCodeUsageDisplayModeKey])

    fun Preferences.toKimiCodeUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[kimiCodeUsageProgressStyleKey])

    fun Preferences.toKimiCodeUsageResetStyle(): UsageResetStyle =
        UsageResetStyle.fromStorage(this[kimiCodeUsageResetStyleKey])

    fun Preferences.toZhipuUsageWindowKind(): UsageWindowKind =
        UsageWindowKind.fromStorage(this[zhipuWindowKindKey])

    fun Preferences.toZhipuUsageDisplayMode(): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[zhipuUsageDisplayModeKey])

    fun Preferences.toZhipuUsageProgressStyle(): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[zhipuUsageProgressStyleKey])

    fun Preferences.toZhipuUsageResetStyle(): UsageResetStyle =
        UsageResetStyle.fromStorage(this[zhipuUsageResetStyleKey])

    fun Preferences.toSessionUsageWindowKind(platformId: String): UsageWindowKind =
        this[sessionWindowKindKey(platformId)]
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: SessionPlatformSettings.defaultWindowKind(platformId)

    fun Preferences.toSessionUsageDisplayMode(platformId: String): UsageDisplayMode =
        UsageDisplayMode.fromStorage(this[sessionUsageDisplayModeKey(platformId)])

    fun Preferences.toSessionUsageProgressStyle(platformId: String): UsageProgressStyle =
        UsageProgressStyle.fromStorage(this[sessionUsageProgressStyleKey(platformId)])

    fun Preferences.toSessionUsageResetStyle(platformId: String): UsageResetStyle =
        UsageResetStyle.fromStorage(this[sessionUsageResetStyleKey(platformId)])

    fun Preferences.toDisplayState(context: Context): WidgetDisplayState {
        return when (this[statusKey]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(
                this[errorKey] ?: context.getString(R.string.widget_fetch_failed),
            )
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
        platformId: String,
        phase: RefreshIconPhase,
        display: WidgetDisplayState,
        appSettings: AppSettings,
        card: WidgetCardSettings?,
    ) {
        this[refreshPhaseKey] = phase.name
        this[darkThemeModeKey] = appSettings.darkThemeMode.name
        this[themeColorModeKey] = appSettings.themeColorMode.name
        this[seedColorKey] = appSettings.customSeedColorArgb
        this[appLanguageKey] = appSettings.appLanguage.name
        if (card != null) {
            writeCardSettings(platformId, card)
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

    private fun MutablePreferences.writeCardSettings(
        platformId: String,
        card: WidgetCardSettings,
    ) {
        when (platformId) {
            PlatformIds.OPENCODE_GO -> {
                card.windowKind?.let { this[openCodeWindowKindKey] = it.name }
                this[openCodeUsageDisplayModeKey] = card.usageDisplayMode.name
                this[openCodeUsageProgressStyleKey] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[openCodeUsageResetStyleKey] = it.name }
            }
            PlatformIds.CODEX -> {
                card.windowKind?.let { this[codexWindowKindKey] = it.name }
                this[codexUsageDisplayModeKey] = card.usageDisplayMode.name
                this[codexUsageProgressStyleKey] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[codexUsageResetStyleKey] = it.name }
            }
            PlatformIds.NEW_API -> {
                this[newApiUsageDisplayModeKey] = card.usageDisplayMode.name
                this[newApiUsageProgressStyleKey] = card.usageProgressStyle.name
            }
            PlatformIds.MAGPIE -> {
                card.windowKind?.let { this[magpieWindowKindKey] = it.name }
                this[magpieUsageDisplayModeKey] = card.usageDisplayMode.name
                this[magpieUsageProgressStyleKey] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[magpieUsageResetStyleKey] = it.name }
            }
            PlatformIds.KIMI_CODE -> {
                card.windowKind?.let { this[kimiCodeWindowKindKey] = it.name }
                this[kimiCodeUsageDisplayModeKey] = card.usageDisplayMode.name
                this[kimiCodeUsageProgressStyleKey] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[kimiCodeUsageResetStyleKey] = it.name }
            }
            PlatformIds.ZHIPU -> {
                card.windowKind?.let { this[zhipuWindowKindKey] = it.name }
                this[zhipuUsageDisplayModeKey] = card.usageDisplayMode.name
                this[zhipuUsageProgressStyleKey] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[zhipuUsageResetStyleKey] = it.name }
            }
            in PlatformIds.sessionPlatforms -> {
                card.windowKind?.let { this[sessionWindowKindKey(platformId)] = it.name }
                this[sessionUsageDisplayModeKey(platformId)] = card.usageDisplayMode.name
                this[sessionUsageProgressStyleKey(platformId)] = card.usageProgressStyle.name
                card.usageResetStyle?.let { this[sessionUsageResetStyleKey(platformId)] = it.name }
            }
        }
    }
}
