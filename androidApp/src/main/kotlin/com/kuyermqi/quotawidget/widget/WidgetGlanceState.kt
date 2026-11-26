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
import com.kuyermqi.quotawidget.domain.WidgetErrorKind
import com.kuyermqi.quotawidget.domain.decodeQuotaWindows
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.domain.relocalizedPrimaryDisplay
import com.kuyermqi.quotawidget.domain.usage.usageLabels
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.UsageDisplayPrefField
import com.kuyermqi.quotawidget.settings.UsageDisplayPrefKeyScheme
import com.kuyermqi.quotawidget.settings.UsageDisplayPrefs
import com.kuyermqi.quotawidget.settings.WidgetCardSettings
import com.kuyermqi.quotawidget.settings.applyWidgetCardSettings
import com.kuyermqi.quotawidget.settings.toUsageDisplayPrefs

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
    val showUpdatedAtKey = booleanPreferencesKey("qw_show_updated_at")
    private val cardKeyScheme = UsageDisplayPrefKeyScheme(
        // Historical "qw_opencode" prefix: these names are persisted in widget state.
        prefixOf = { platformId ->
            if (platformId == PlatformIds.OPENCODE_GO) "qw_opencode" else "qw_$platformId"
        },
        tokens = mapOf(
            UsageDisplayPrefField.WINDOW_KIND to "window_kind",
            UsageDisplayPrefField.DISPLAY_MODE to "usage_display",
            UsageDisplayPrefField.PROGRESS_STYLE to "usage_progress_style",
            UsageDisplayPrefField.RESET_STYLE to "usage_reset_style",
        ),
    )

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

    fun Preferences.toShowWidgetUpdatedAt(): Boolean =
        this[showUpdatedAtKey] ?: true

    private fun Preferences.cardPrefs(platformId: String): UsageDisplayPrefs =
        toUsageDisplayPrefs(platformId, cardKeyScheme)

    fun Preferences.toOpenCodeUsageWindowKind(): UsageWindowKind = cardPrefs(PlatformIds.OPENCODE_GO).windowKind

    fun Preferences.toOpenCodeUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.OPENCODE_GO).usageDisplayMode

    fun Preferences.toOpenCodeUsageProgressStyle(): UsageProgressStyle =
        cardPrefs(PlatformIds.OPENCODE_GO).usageProgressStyle

    fun Preferences.toOpenCodeUsageResetStyle(): UsageResetStyle = cardPrefs(PlatformIds.OPENCODE_GO).usageResetStyle

    fun Preferences.toCodexUsageWindowKind(): UsageWindowKind = cardPrefs(PlatformIds.CODEX).windowKind

    fun Preferences.toCodexUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.CODEX).usageDisplayMode

    fun Preferences.toCodexUsageProgressStyle(): UsageProgressStyle = cardPrefs(PlatformIds.CODEX).usageProgressStyle

    fun Preferences.toCodexUsageResetStyle(): UsageResetStyle = cardPrefs(PlatformIds.CODEX).usageResetStyle

    fun Preferences.toNewApiUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.NEW_API).usageDisplayMode

    fun Preferences.toNewApiUsageProgressStyle(): UsageProgressStyle = cardPrefs(PlatformIds.NEW_API).usageProgressStyle

    fun Preferences.toMagpieUsageWindowKind(): UsageWindowKind = cardPrefs(PlatformIds.MAGPIE).windowKind

    fun Preferences.toMagpieUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.MAGPIE).usageDisplayMode

    fun Preferences.toMagpieUsageProgressStyle(): UsageProgressStyle = cardPrefs(PlatformIds.MAGPIE).usageProgressStyle

    fun Preferences.toMagpieUsageResetStyle(): UsageResetStyle = cardPrefs(PlatformIds.MAGPIE).usageResetStyle

    fun Preferences.toKimiCodeUsageWindowKind(): UsageWindowKind = cardPrefs(PlatformIds.KIMI_CODE).windowKind

    fun Preferences.toKimiCodeUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.KIMI_CODE).usageDisplayMode

    fun Preferences.toKimiCodeUsageProgressStyle(): UsageProgressStyle =
        cardPrefs(PlatformIds.KIMI_CODE).usageProgressStyle

    fun Preferences.toKimiCodeUsageResetStyle(): UsageResetStyle = cardPrefs(PlatformIds.KIMI_CODE).usageResetStyle

    fun Preferences.toZhipuUsageWindowKind(): UsageWindowKind = cardPrefs(PlatformIds.ZHIPU).windowKind

    fun Preferences.toZhipuUsageDisplayMode(): UsageDisplayMode = cardPrefs(PlatformIds.ZHIPU).usageDisplayMode

    fun Preferences.toZhipuUsageProgressStyle(): UsageProgressStyle = cardPrefs(PlatformIds.ZHIPU).usageProgressStyle

    fun Preferences.toZhipuUsageResetStyle(): UsageResetStyle = cardPrefs(PlatformIds.ZHIPU).usageResetStyle

    fun Preferences.toSessionUsageWindowKind(platformId: String): UsageWindowKind = cardPrefs(platformId).windowKind

    fun Preferences.toSessionUsageDisplayMode(platformId: String): UsageDisplayMode =
        cardPrefs(platformId).usageDisplayMode

    fun Preferences.toSessionUsageProgressStyle(platformId: String): UsageProgressStyle =
        cardPrefs(platformId).usageProgressStyle

    fun Preferences.toSessionUsageResetStyle(platformId: String): UsageResetStyle =
        cardPrefs(platformId).usageResetStyle

    /** Display mode the platform's card uses; also drives the rebuilt usage summary. */
    fun Preferences.usageDisplayModeFor(platformId: String): UsageDisplayMode = cardPrefs(platformId).usageDisplayMode

    fun Preferences.toDisplayState(): WidgetDisplayState {
        return when (this[statusKey]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(
                WidgetErrorKind.fromStorage(this[errorKey]),
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
        this[showUpdatedAtKey] = appSettings.showWidgetUpdatedAt
        if (card != null) {
            applyWidgetCardSettings(platformId, cardKeyScheme, card)
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
                this[errorKey] = display.kind.toStorage()
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

/**
 * Rebuilds [QuotaSnapshot.primaryDisplay] from the stored windows with the app language, so a
 * summary persisted under another language never reaches the screen. Balance and plan texts
 * (no usage windows) are kept as stored.
 */
internal fun WidgetDisplayState.withLocalizedSummary(
    context: Context,
    mode: UsageDisplayMode,
): WidgetDisplayState = when (this) {
    is WidgetDisplayState.Success -> WidgetDisplayState.Success(
        snapshot.copy(
            primaryDisplay = snapshot.relocalizedPrimaryDisplay(
                labels = usageLabels(context.resources),
                mode = mode,
            ),
        ),
    )
    else -> this
}
