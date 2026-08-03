package com.kuyermqi.quotawidget.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.kuyermqi.quotawidget.domain.ALLOWED_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.OpenCodeWidgetWindowKind
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quota_widget_settings")

class AndroidPlatformSettingsRepository(
    context: Context,
) : PlatformSettingsRepository {
    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore
    private val aead: Aead by lazy { createAead(appContext) }

    override fun observeDeepSeekSettings(): Flow<DeepSeekSettings> =
        dataStore.data.map { prefs -> prefs.toDeepSeekSettings() }

    override suspend fun getDeepSeekSettings(): DeepSeekSettings =
        dataStore.data.first().toDeepSeekSettings()

    override suspend fun saveDeepSeekSettings(settings: DeepSeekSettings) {
        dataStore.edit { prefs ->
            if (settings.apiKey.isBlank()) {
                prefs.remove(Keys.API_KEY_ENCRYPTED)
                prefs.clearWidgetPayload(PlatformIds.DEEPSEEK)
                prefs[widgetStatusKey(PlatformIds.DEEPSEEK)] = Status.NOT_CONFIGURED
            } else {
                prefs[Keys.API_KEY_ENCRYPTED] = encrypt(settings.apiKey, API_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.DEEPSEEK)]
                if (status == null || status == Status.NOT_CONFIGURED) {
                    prefs[widgetStatusKey(PlatformIds.DEEPSEEK)] = Status.LOADING
                }
            }
            prefs[Keys.CURRENCY] = settings.currency.name
        }
    }

    override fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings> =
        dataStore.data.map { prefs -> prefs.toOpenCodeGoSettings() }

    override suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings =
        dataStore.data.first().toOpenCodeGoSettings()

    override suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings) {
        dataStore.edit { prefs ->
            if (settings.workspaceId.isBlank() || settings.authCookie.isBlank()) {
                prefs.remove(Keys.OPENCODE_WORKSPACE_ID)
                prefs.remove(Keys.OPENCODE_WORKSPACE_NAME)
                prefs.remove(Keys.OPENCODE_AUTH_COOKIE_ENC)
                prefs.clearWidgetPayload(PlatformIds.OPENCODE_GO)
                prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = Status.NOT_CONFIGURED
            } else {
                prefs[Keys.OPENCODE_WORKSPACE_ID] = settings.workspaceId
                if (settings.workspaceName.isBlank()) {
                    prefs.remove(Keys.OPENCODE_WORKSPACE_NAME)
                } else {
                    prefs[Keys.OPENCODE_WORKSPACE_NAME] = settings.workspaceName
                }
                prefs[Keys.OPENCODE_AUTH_COOKIE_ENC] = encrypt(settings.authCookie, OPENCODE_AUTH_AD)
                val status = prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)]
                if (status == null ||
                    status == Status.NOT_CONFIGURED ||
                    status == Status.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = Status.LOADING
                }
            }
            prefs[Keys.OPENCODE_WIDGET_WINDOW] = settings.widgetWindowKind.name
        }
    }

    override suspend fun clearOpenCodeGoSettings() {
        val window = getOpenCodeGoSettings().widgetWindowKind
        saveOpenCodeGoSettings(OpenCodeGoSettings(widgetWindowKind = window))
    }

    override fun observeWidgetState(platformId: String): Flow<WidgetDisplayState> =
        dataStore.data.map { prefs -> prefs.toWidgetState(platformId) }

    override suspend fun getWidgetState(platformId: String): WidgetDisplayState {
        ensureLegacyMigrated()
        return dataStore.data.first().toWidgetState(platformId)
    }

    private suspend fun ensureLegacyMigrated() {
        dataStore.edit { it.migrateLegacyWidgetIfNeeded() }
    }

    override suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot) {
        dataStore.edit { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.SUCCESS
            prefs[widgetPlatformIdKey(platformId)] = snapshot.platformId
            prefs[widgetPlatformNameKey(platformId)] = snapshot.platformName
            prefs[widgetCurrencyKey(platformId)] = snapshot.currency.name
            prefs[widgetTotalKey(platformId)] = snapshot.totalBalance
            prefs[widgetFormattedKey(platformId)] = snapshot.primaryDisplay
            prefs[widgetUpdatedAtKey(platformId)] = snapshot.updatedAtEpochMs
            prefs[widgetWindowsKey(platformId)] = encodeQuotaWindows(snapshot.windows)
            prefs.remove(widgetErrorKey(platformId))
        }
    }

    override suspend fun saveWidgetError(platformId: String, message: String) {
        dataStore.edit { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.ERROR
            prefs[widgetErrorKey(platformId)] = message
        }
    }

    override suspend fun saveWidgetLoading(platformId: String) {
        dataStore.edit { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.LOADING
            prefs.remove(widgetErrorKey(platformId))
        }
    }

    override suspend fun saveWidgetNotConfigured(platformId: String) {
        dataStore.edit { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.NOT_CONFIGURED
            prefs.clearWidgetPayload(platformId)
        }
    }

    override suspend fun saveWidgetNeedsReauth(platformId: String) {
        dataStore.edit { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.NEEDS_REAUTH
            prefs.remove(widgetErrorKey(platformId))
        }
    }

    override suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase {
        val prefs = dataStore.data.first()
        return RefreshIconPhase.fromStorage(
            prefs[refreshPhaseKey(platformId)] ?: prefs[Keys.LEGACY_REFRESH_ICON_PHASE],
        )
    }

    override suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase) {
        dataStore.edit { prefs ->
            prefs[refreshPhaseKey(platformId)] = phase.name
            prefs.remove(Keys.LEGACY_REFRESH_ICON_PHASE)
        }
    }

    override suspend fun getRefreshStartedAtEpochMs(platformId: String): Long {
        val prefs = dataStore.data.first()
        return prefs[refreshStartedAtKey(platformId)]
            ?: prefs[Keys.LEGACY_REFRESH_STARTED_AT]
            ?: 0L
    }

    override suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long) {
        dataStore.edit { prefs ->
            prefs[refreshStartedAtKey(platformId)] = epochMs
            prefs.remove(Keys.LEGACY_REFRESH_STARTED_AT)
        }
    }

    override suspend fun clearAllRefreshIconPhases() {
        dataStore.edit { prefs ->
            for (platformId in listOf(PlatformIds.DEEPSEEK, PlatformIds.OPENCODE_GO)) {
                prefs[refreshPhaseKey(platformId)] = RefreshIconPhase.Idle.name
            }
            prefs.remove(Keys.LEGACY_REFRESH_ICON_PHASE)
        }
    }

    override suspend fun isPlatformTipDismissed(): Boolean =
        dataStore.data.first()[Keys.PLATFORM_TIP_DISMISSED] == true

    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PLATFORM_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun isOemBackgroundTipDismissed(): Boolean =
        dataStore.data.first()[Keys.OEM_BACKGROUND_TIP_DISMISSED] == true

    override suspend fun setOemBackgroundTipDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.OEM_BACKGROUND_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun getUpdateIgnoredVersion(): String? =
        dataStore.data.first()[Keys.UPDATE_IGNORED_VERSION]

    override suspend fun setUpdateIgnoredVersion(version: String?) {
        dataStore.edit { prefs ->
            if (version.isNullOrBlank()) {
                prefs.remove(Keys.UPDATE_IGNORED_VERSION)
            } else {
                prefs[Keys.UPDATE_IGNORED_VERSION] = version
            }
        }
    }

    override fun observeAppSettings(): Flow<AppSettings> =
        dataStore.data.map { prefs -> prefs.toAppSettings() }

    override suspend fun getAppSettings(): AppSettings =
        dataStore.data.first().toAppSettings()

    override suspend fun saveAppSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_MODE] = settings.darkThemeMode.name
            prefs[Keys.THEME_COLOR_MODE] = settings.themeColorMode.name
            prefs[Keys.CUSTOM_SEED_COLOR_ARGB] = settings.customSeedColorArgb
            prefs[Keys.REFRESH_INTERVAL_MINUTES] = settings.refreshIntervalMinutes
            prefs[Keys.CHECK_FOR_UPDATES_ON_LAUNCH] = settings.checkForUpdatesOnLaunch
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val interval = this[Keys.REFRESH_INTERVAL_MINUTES] ?: DEFAULT_REFRESH_INTERVAL_MINUTES
        return AppSettings(
            darkThemeMode = DarkThemeMode.fromStorage(this[Keys.DARK_THEME_MODE]),
            themeColorMode = ThemeColorMode.fromStorage(this[Keys.THEME_COLOR_MODE]),
            customSeedColorArgb = this[Keys.CUSTOM_SEED_COLOR_ARGB] ?: DEFAULT_CUSTOM_SEED_COLOR_ARGB,
            refreshIntervalMinutes = interval.takeIf { it in ALLOWED_REFRESH_INTERVAL_MINUTES }
                ?: DEFAULT_REFRESH_INTERVAL_MINUTES,
            checkForUpdatesOnLaunch = this[Keys.CHECK_FOR_UPDATES_ON_LAUNCH] ?: true,
        )
    }

    private fun Preferences.toDeepSeekSettings(): DeepSeekSettings {
        val encrypted = this[Keys.API_KEY_ENCRYPTED]
        val apiKey = encrypted?.let { runCatching { decrypt(it, API_KEY_AD) }.getOrDefault("") }.orEmpty()
        return DeepSeekSettings(
            apiKey = apiKey,
            currency = CurrencyPreference.fromStorage(this[Keys.CURRENCY]),
        )
    }

    private fun Preferences.toOpenCodeGoSettings(): OpenCodeGoSettings {
        val encrypted = this[Keys.OPENCODE_AUTH_COOKIE_ENC]
        val authCookie = encrypted
            ?.let { runCatching { decrypt(it, OPENCODE_AUTH_AD) }.getOrDefault("") }
            .orEmpty()
        return OpenCodeGoSettings(
            workspaceId = this[Keys.OPENCODE_WORKSPACE_ID].orEmpty(),
            workspaceName = this[Keys.OPENCODE_WORKSPACE_NAME].orEmpty(),
            authCookie = authCookie,
            widgetWindowKind = OpenCodeWidgetWindowKind.fromStorage(this[Keys.OPENCODE_WIDGET_WINDOW]),
        )
    }

    private fun Preferences.isConfigured(platformId: String): Boolean =
        when (platformId) {
            PlatformIds.DEEPSEEK -> toDeepSeekSettings().apiKey.isNotBlank()
            PlatformIds.OPENCODE_GO -> toOpenCodeGoSettings().isConfigured
            else -> false
        }

    private fun Preferences.toWidgetState(platformId: String): WidgetDisplayState {
        if (!isConfigured(platformId)) {
            return WidgetDisplayState.NotConfigured
        }
        val status = this[widgetStatusKey(platformId)]
            ?: if (platformId == PlatformIds.DEEPSEEK) this[Keys.LEGACY_WIDGET_STATUS] else null
        return when (status) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
            Status.ERROR -> WidgetDisplayState.Error(
                this[widgetErrorKey(platformId)]
                    ?: this[Keys.LEGACY_WIDGET_ERROR]
                    ?: "刷新失败",
            )
            Status.SUCCESS -> {
                val storedPlatformId = this[widgetPlatformIdKey(platformId)]
                    ?: this[Keys.LEGACY_WIDGET_PLATFORM_ID]
                    ?: platformId
                val currency = CurrencyPreference.fromStorage(
                    this[widgetCurrencyKey(platformId)] ?: this[Keys.LEGACY_WIDGET_CURRENCY],
                )
                val total = this[widgetTotalKey(platformId)]
                    ?: this[Keys.LEGACY_WIDGET_TOTAL]
                    ?: "0"
                val primary = this[widgetFormattedKey(platformId)]
                    ?: this[Keys.LEGACY_WIDGET_FORMATTED]
                    ?: if (platformId == PlatformIds.DEEPSEEK) {
                        formatBalance(currency, total)
                    } else {
                        ""
                    }
                val windows = decodeQuotaWindows(
                    this[widgetWindowsKey(platformId)] ?: this[Keys.LEGACY_WIDGET_WINDOWS],
                ) ?: defaultWindows(platformId)
                WidgetDisplayState.Success(
                    QuotaSnapshot(
                        platformId = storedPlatformId,
                        platformName = this[widgetPlatformNameKey(platformId)]
                            ?: this[Keys.LEGACY_WIDGET_PLATFORM_NAME]
                            ?: PlatformRegistry.displayName(platformId),
                        windows = windows,
                        primaryDisplay = primary,
                        updatedAtEpochMs = this[widgetUpdatedAtKey(platformId)]
                            ?: this[Keys.LEGACY_WIDGET_UPDATED_AT]
                            ?: 0L,
                        currency = currency,
                        totalBalance = total,
                    ),
                )
            }
            else -> WidgetDisplayState.Loading
        }
    }

    private fun defaultWindows(platformId: String): List<QuotaWindow> =
        when (platformId) {
            PlatformIds.DEEPSEEK -> listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE))
            else -> emptyList()
        }

    /**
     * One-time migration: copy legacy global `widget_*` keys into DeepSeek-prefixed keys.
     */
    private fun MutablePreferences.migrateLegacyWidgetIfNeeded() {
        if (this[widgetStatusKey(PlatformIds.DEEPSEEK)] != null) return
        val legacyStatus = this[Keys.LEGACY_WIDGET_STATUS] ?: return
        this[widgetStatusKey(PlatformIds.DEEPSEEK)] = legacyStatus
        this[Keys.LEGACY_WIDGET_ERROR]?.let { this[widgetErrorKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_PLATFORM_ID]?.let { this[widgetPlatformIdKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_PLATFORM_NAME]?.let { this[widgetPlatformNameKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_CURRENCY]?.let { this[widgetCurrencyKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_TOTAL]?.let { this[widgetTotalKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_FORMATTED]?.let { this[widgetFormattedKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_WINDOWS]?.let { this[widgetWindowsKey(PlatformIds.DEEPSEEK)] = it }
        this[Keys.LEGACY_WIDGET_UPDATED_AT]?.let { this[widgetUpdatedAtKey(PlatformIds.DEEPSEEK)] = it }
        remove(Keys.LEGACY_WIDGET_STATUS)
        remove(Keys.LEGACY_WIDGET_ERROR)
        remove(Keys.LEGACY_WIDGET_PLATFORM_ID)
        remove(Keys.LEGACY_WIDGET_PLATFORM_NAME)
        remove(Keys.LEGACY_WIDGET_CURRENCY)
        remove(Keys.LEGACY_WIDGET_TOTAL)
        remove(Keys.LEGACY_WIDGET_FORMATTED)
        remove(Keys.LEGACY_WIDGET_WINDOWS)
        remove(Keys.LEGACY_WIDGET_UPDATED_AT)
        remove(Keys.ACTIVE_PLATFORM_ID)
    }

    private fun MutablePreferences.clearWidgetPayload(platformId: String) {
        remove(widgetErrorKey(platformId))
        remove(widgetFormattedKey(platformId))
        remove(widgetWindowsKey(platformId))
        remove(widgetPlatformIdKey(platformId))
        remove(widgetPlatformNameKey(platformId))
        remove(widgetCurrencyKey(platformId))
        remove(widgetTotalKey(platformId))
        remove(widgetUpdatedAtKey(platformId))
    }

    private fun encrypt(plain: String, associatedData: ByteArray): String {
        val cipher = aead.encrypt(plain.encodeToByteArray(), associatedData)
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String, associatedData: ByteArray): String {
        val cipher = Base64.decode(encoded, Base64.NO_WRAP)
        return aead.decrypt(cipher, associatedData).decodeToString()
    }

    private object Keys {
        val API_KEY_ENCRYPTED = stringPreferencesKey("deepseek_api_key_enc")
        val CURRENCY = stringPreferencesKey("deepseek_currency")
        val OPENCODE_WORKSPACE_ID = stringPreferencesKey("opencode_workspace_id")
        val OPENCODE_WORKSPACE_NAME = stringPreferencesKey("opencode_workspace_name")
        val OPENCODE_AUTH_COOKIE_ENC = stringPreferencesKey("opencode_auth_cookie_enc")
        val OPENCODE_WIDGET_WINDOW = stringPreferencesKey("opencode_go_widget_window")
        val ACTIVE_PLATFORM_ID = stringPreferencesKey("active_platform_id")
        val LEGACY_WIDGET_STATUS = stringPreferencesKey("widget_status")
        val LEGACY_WIDGET_ERROR = stringPreferencesKey("widget_error")
        val LEGACY_WIDGET_PLATFORM_ID = stringPreferencesKey("widget_platform_id")
        val LEGACY_WIDGET_PLATFORM_NAME = stringPreferencesKey("widget_platform_name")
        val LEGACY_WIDGET_CURRENCY = stringPreferencesKey("widget_currency")
        val LEGACY_WIDGET_TOTAL = stringPreferencesKey("widget_total")
        val LEGACY_WIDGET_FORMATTED = stringPreferencesKey("widget_formatted")
        val LEGACY_WIDGET_WINDOWS = stringPreferencesKey("widget_windows")
        val LEGACY_WIDGET_UPDATED_AT = longPreferencesKey("widget_updated_at")
        val LEGACY_REFRESH_ICON_PHASE = stringPreferencesKey("refresh_icon_phase")
        val LEGACY_REFRESH_STARTED_AT = longPreferencesKey("refresh_started_at")
        val PLATFORM_TIP_DISMISSED = booleanPreferencesKey("platform_tip_dismissed")
        val OEM_BACKGROUND_TIP_DISMISSED = booleanPreferencesKey("oem_background_tip_dismissed")
        val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
        val THEME_COLOR_MODE = stringPreferencesKey("theme_color_mode")
        val CUSTOM_SEED_COLOR_ARGB = intPreferencesKey("custom_seed_color_argb")
        val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        val CHECK_FOR_UPDATES_ON_LAUNCH = booleanPreferencesKey("check_for_updates_on_launch")
        val UPDATE_IGNORED_VERSION = stringPreferencesKey("update_ignored_version")
    }

    private object Status {
        const val NOT_CONFIGURED = "not_configured"
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val NEEDS_REAUTH = "needs_reauth"
    }

    companion object {
        private val API_KEY_AD = "deepseek_api_key".encodeToByteArray()
        private val OPENCODE_AUTH_AD = "opencode_auth_cookie".encodeToByteArray()

        private fun widgetStatusKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_status")

        private fun widgetErrorKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_error")

        private fun widgetPlatformIdKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_platform_id")

        private fun widgetPlatformNameKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_platform_name")

        private fun widgetCurrencyKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_currency")

        private fun widgetTotalKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_total")

        private fun widgetFormattedKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_formatted")

        private fun widgetWindowsKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_windows")

        private fun widgetUpdatedAtKey(platformId: String) =
            longPreferencesKey("widget_${platformId}_updated_at")

        private fun refreshPhaseKey(platformId: String) =
            stringPreferencesKey("refresh_${platformId}_icon_phase")

        private fun refreshStartedAtKey(platformId: String) =
            longPreferencesKey("refresh_${platformId}_started_at")

        private fun createAead(context: Context): Aead {
            AeadConfig.register()
            return AndroidKeysetManager.Builder()
                .withSharedPref(context, "quota_widget_tink_keyset", "quota_widget_tink_prefs")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://quota_widget_master_key")
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }
    }
}
