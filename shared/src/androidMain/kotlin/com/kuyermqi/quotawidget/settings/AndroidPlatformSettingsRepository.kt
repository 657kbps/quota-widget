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
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.kuyermqi.quotawidget.domain.ALLOWED_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.decodeQuotaWindows
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.magpie.decodeMagpieProviders
import com.kuyermqi.quotawidget.magpie.encodeMagpieProviders
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quota_widget_settings")

class AndroidPlatformSettingsRepository(
    context: Context,
) : PlatformSettingsRepository {
    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore
    private val aead = RecoveringAead(
        createAead = { createAead(appContext) },
        resetKeyset = { resetTinkKeyset(appContext) },
    )
    private val settingsWriteMutex = Mutex()

    override fun observeDeepSeekSettings(): Flow<DeepSeekSettings> =
        dataStore.data.map { prefs -> prefs.toDeepSeekSettings() }

    override suspend fun getDeepSeekSettings(): DeepSeekSettings =
        dataStore.data.first().toDeepSeekSettings()

    override suspend fun saveDeepSeekSettings(settings: DeepSeekSettings) {
        editSettings { prefs ->
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
        val previous = getOpenCodeGoSettings()
        val credentialsChanged = previous.effectiveAuthMethod != settings.effectiveAuthMethod ||
            previous.workspaceId != settings.workspaceId ||
            previous.authCookie != settings.authCookie ||
            previous.apiKey != settings.apiKey
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(Keys.OPENCODE_WORKSPACE_ID)
                prefs.remove(Keys.OPENCODE_WORKSPACE_NAME)
                prefs.remove(Keys.OPENCODE_AUTH_COOKIE_ENC)
                prefs.remove(Keys.OPENCODE_API_KEY_ENC)
                prefs.remove(Keys.OPENCODE_AUTH_METHOD)
                prefs.clearWidgetPayload(PlatformIds.OPENCODE_GO)
                prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = Status.NOT_CONFIGURED
            } else {
                if (settings.workspaceId.isBlank()) {
                    prefs.remove(Keys.OPENCODE_WORKSPACE_ID)
                } else {
                    prefs[Keys.OPENCODE_WORKSPACE_ID] = settings.workspaceId
                }
                if (settings.workspaceName.isBlank()) {
                    prefs.remove(Keys.OPENCODE_WORKSPACE_NAME)
                } else {
                    prefs[Keys.OPENCODE_WORKSPACE_NAME] = settings.workspaceName
                }
                if (settings.authCookie.isBlank()) {
                    prefs.remove(Keys.OPENCODE_AUTH_COOKIE_ENC)
                } else {
                    prefs[Keys.OPENCODE_AUTH_COOKIE_ENC] = encrypt(settings.authCookie, OPENCODE_AUTH_AD)
                }
                if (settings.apiKey.isBlank()) {
                    prefs.remove(Keys.OPENCODE_API_KEY_ENC)
                } else {
                    prefs[Keys.OPENCODE_API_KEY_ENC] = encrypt(settings.apiKey, OPENCODE_API_KEY_AD)
                }
                prefs[Keys.OPENCODE_AUTH_METHOD] = settings.effectiveAuthMethod.name
                val status = prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)]
                if (credentialsChanged ||
                    status == null ||
                    status == Status.NOT_CONFIGURED ||
                    status == Status.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = Status.LOADING
                }
            }
            prefs[Keys.OPENCODE_WIDGET_WINDOW] = settings.widgetWindowKind.name
            prefs[Keys.OPENCODE_USAGE_DISPLAY] = settings.usageDisplayMode.name
            prefs[Keys.OPENCODE_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
            prefs[Keys.OPENCODE_USAGE_RESET_STYLE] = settings.usageResetStyle.name
        }
    }

    override suspend fun clearOpenCodeGoSettings() {
        val current = getOpenCodeGoSettings()
        saveOpenCodeGoSettings(
            OpenCodeGoSettings(
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
    }

    override fun observeCodexSettings(): Flow<CodexSettings> =
        dataStore.data.map { prefs -> prefs.toCodexSettings() }

    override suspend fun getCodexSettings(): CodexSettings =
        dataStore.data.first().toCodexSettings()

    override suspend fun saveCodexSettings(settings: CodexSettings) {
        editSettings { prefs -> prefs.applyCodexSettings(settings) }
    }

    override suspend fun editCodexSettings(
        guard: (CodexSettings) -> Boolean,
        transform: (CodexSettings) -> CodexSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toCodexSettings()
            if (guard(current)) {
                prefs.applyCodexSettings(transform(current))
                applied = true
            }
        }
        return applied
    }

    private fun MutablePreferences.applyCodexSettings(settings: CodexSettings) {
        if (!settings.isConfigured) {
            remove(Keys.CODEX_ACCESS_ENC)
            remove(Keys.CODEX_REFRESH_ENC)
            remove(Keys.CODEX_ID_ENC)
            remove(Keys.CODEX_ACCOUNT_ID)
            remove(Keys.CODEX_EXPIRES_AT)
            remove(Keys.CODEX_EMAIL)
            remove(Keys.CODEX_PLAN_TYPE)
            clearWidgetPayload(PlatformIds.CODEX)
            this[widgetStatusKey(PlatformIds.CODEX)] = Status.NOT_CONFIGURED
        } else {
            this[Keys.CODEX_ACCESS_ENC] = encrypt(settings.accessToken, CODEX_ACCESS_AD)
            this[Keys.CODEX_REFRESH_ENC] = encrypt(settings.refreshToken, CODEX_REFRESH_AD)
            if (settings.idToken.isBlank()) {
                remove(Keys.CODEX_ID_ENC)
            } else {
                this[Keys.CODEX_ID_ENC] = encrypt(settings.idToken, CODEX_ID_AD)
            }
            this[Keys.CODEX_ACCOUNT_ID] = settings.accountId
            this[Keys.CODEX_EXPIRES_AT] = settings.expiresAtEpochMs
            if (settings.email.isBlank()) {
                remove(Keys.CODEX_EMAIL)
            } else {
                this[Keys.CODEX_EMAIL] = settings.email
            }
            if (settings.planType.isBlank()) {
                remove(Keys.CODEX_PLAN_TYPE)
            } else {
                this[Keys.CODEX_PLAN_TYPE] = settings.planType
            }
            val status = this[widgetStatusKey(PlatformIds.CODEX)]
            if (status == null ||
                status == Status.NOT_CONFIGURED ||
                status == Status.NEEDS_REAUTH
            ) {
                this[widgetStatusKey(PlatformIds.CODEX)] = Status.LOADING
            }
        }
        this[Keys.CODEX_WIDGET_WINDOW] = settings.widgetWindowKind.name
        this[Keys.CODEX_USAGE_DISPLAY] = settings.usageDisplayMode.name
        this[Keys.CODEX_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
        this[Keys.CODEX_USAGE_RESET_STYLE] = settings.usageResetStyle.name
    }

    override suspend fun clearCodexSettings() {
        val current = getCodexSettings()
        saveCodexSettings(
            CodexSettings(
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
    }

    override fun observeNewApiSettings(): Flow<NewApiSettings> =
        dataStore.data.map { prefs -> prefs.toNewApiSettings() }

    override suspend fun getNewApiSettings(): NewApiSettings =
        dataStore.data.first().toNewApiSettings()

    override suspend fun saveNewApiSettings(settings: NewApiSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(Keys.NEW_API_BASE_URL)
                prefs.remove(Keys.NEW_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.NEW_API)
                prefs[widgetStatusKey(PlatformIds.NEW_API)] = Status.NOT_CONFIGURED
            } else {
                prefs[Keys.NEW_API_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
                prefs[Keys.NEW_API_KEY_ENC] = encrypt(settings.apiKey, NEW_API_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.NEW_API)]
                if (status == null ||
                    status == Status.NOT_CONFIGURED ||
                    status == Status.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.NEW_API)] = Status.LOADING
                }
            }
            prefs[Keys.NEW_API_USAGE_DISPLAY] = settings.usageDisplayMode.name
            prefs[Keys.NEW_API_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
            prefs[Keys.NEW_API_QUOTA_PER_USD] = settings.quotaPerUsd.takeIf { it > 0L }
                ?: DEFAULT_NEW_API_QUOTA_PER_USD
        }
    }

    override suspend fun clearNewApiSettings() {
        val current = getNewApiSettings()
        saveNewApiSettings(
            NewApiSettings(
                quotaPerUsd = current.quotaPerUsd,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
            ),
        )
    }

    override fun observeMagpieSettings(): Flow<MagpieSettings> =
        dataStore.data.map { prefs -> prefs.toMagpieSettings() }

    override suspend fun getMagpieSettings(): MagpieSettings =
        dataStore.data.first().toMagpieSettings()

    override suspend fun saveMagpieSettings(settings: MagpieSettings) {
        editSettings { prefs -> prefs.applyMagpieSettings(settings) }
    }

    override suspend fun editMagpieSettings(
        guard: (MagpieSettings) -> Boolean,
        transform: (MagpieSettings) -> MagpieSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toMagpieSettings()
            if (guard(current)) {
                prefs.applyMagpieSettings(transform(current))
                applied = true
            }
        }
        return applied
    }

    private fun MutablePreferences.applyMagpieSettings(settings: MagpieSettings) {
        if (!settings.isConfigured) {
            remove(Keys.MAGPIE_BASE_URL)
            remove(Keys.MAGPIE_KEY_ENC)
            remove(Keys.MAGPIE_PROVIDERS)
            remove(Keys.MAGPIE_SELECTED_PROVIDER)
            clearWidgetPayload(PlatformIds.MAGPIE)
            this[widgetStatusKey(PlatformIds.MAGPIE)] = Status.NOT_CONFIGURED
        } else {
            this[Keys.MAGPIE_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
            this[Keys.MAGPIE_KEY_ENC] = encrypt(settings.apiKey, MAGPIE_KEY_AD)
            this[Keys.MAGPIE_PROVIDERS] = encodeMagpieProviders(settings.providers)
            if (settings.selectedProviderId.isBlank()) {
                remove(Keys.MAGPIE_SELECTED_PROVIDER)
            } else {
                this[Keys.MAGPIE_SELECTED_PROVIDER] = settings.selectedProviderId
            }
            val status = this[widgetStatusKey(PlatformIds.MAGPIE)]
            if (status == null ||
                status == Status.NOT_CONFIGURED ||
                status == Status.NEEDS_REAUTH
            ) {
                this[widgetStatusKey(PlatformIds.MAGPIE)] = Status.LOADING
            }
        }
        this[Keys.MAGPIE_WIDGET_WINDOW] = settings.widgetWindowKind.name
        this[Keys.MAGPIE_USAGE_DISPLAY] = settings.usageDisplayMode.name
        this[Keys.MAGPIE_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
        this[Keys.MAGPIE_USAGE_RESET_STYLE] = settings.usageResetStyle.name
    }

    override suspend fun clearMagpieSettings() {
        val current = getMagpieSettings()
        saveMagpieSettings(
            MagpieSettings(
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
    }

    override fun observeKimiCodeSettings(): Flow<KimiCodeSettings> =
        dataStore.data.map { prefs -> prefs.toKimiCodeSettings() }

    override suspend fun getKimiCodeSettings(): KimiCodeSettings =
        dataStore.data.first().toKimiCodeSettings()

    override suspend fun saveKimiCodeSettings(settings: KimiCodeSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(Keys.KIMI_CODE_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.KIMI_CODE)
                prefs[widgetStatusKey(PlatformIds.KIMI_CODE)] = Status.NOT_CONFIGURED
            } else {
                prefs[Keys.KIMI_CODE_API_KEY_ENC] = encrypt(settings.apiKey, KIMI_CODE_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.KIMI_CODE)]
                if (status == null ||
                    status == Status.NOT_CONFIGURED ||
                    status == Status.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.KIMI_CODE)] = Status.LOADING
                }
            }
            prefs[Keys.KIMI_CODE_REGION] = settings.region.name
            prefs[Keys.KIMI_CODE_WIDGET_WINDOW] = settings.widgetWindowKind.name
            prefs[Keys.KIMI_CODE_USAGE_DISPLAY] = settings.usageDisplayMode.name
            prefs[Keys.KIMI_CODE_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
            prefs[Keys.KIMI_CODE_USAGE_RESET_STYLE] = settings.usageResetStyle.name
        }
    }

    override suspend fun clearKimiCodeSettings() {
        val current = getKimiCodeSettings()
        saveKimiCodeSettings(
            KimiCodeSettings(
                region = current.region,
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
    }

    override fun observeZhipuSettings(): Flow<ZhipuSettings> =
        dataStore.data.map { prefs -> prefs.toZhipuSettings() }

    override suspend fun getZhipuSettings(): ZhipuSettings =
        dataStore.data.first().toZhipuSettings()

    override suspend fun saveZhipuSettings(settings: ZhipuSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(Keys.ZHIPU_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.ZHIPU)
                prefs[widgetStatusKey(PlatformIds.ZHIPU)] = Status.NOT_CONFIGURED
            } else {
                prefs[Keys.ZHIPU_API_KEY_ENC] = encrypt(settings.apiKey, ZHIPU_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.ZHIPU)]
                if (status == null ||
                    status == Status.NOT_CONFIGURED ||
                    status == Status.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.ZHIPU)] = Status.LOADING
                }
            }
            prefs[Keys.ZHIPU_REGION] = settings.region.name
            prefs[Keys.ZHIPU_WIDGET_WINDOW] = settings.widgetWindowKind.name
            prefs[Keys.ZHIPU_USAGE_DISPLAY] = settings.usageDisplayMode.name
            prefs[Keys.ZHIPU_USAGE_PROGRESS_STYLE] = settings.usageProgressStyle.name
            prefs[Keys.ZHIPU_USAGE_RESET_STYLE] = settings.usageResetStyle.name
        }
    }

    override suspend fun clearZhipuSettings() {
        val current = getZhipuSettings()
        saveZhipuSettings(
            ZhipuSettings(
                region = current.region,
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
    }

    override fun observeSessionSettings(platformId: String): Flow<SessionPlatformSettings> =
        dataStore.data.map { prefs -> prefs.toSessionSettings(platformId) }

    override suspend fun getSessionSettings(platformId: String): SessionPlatformSettings =
        dataStore.data.first().toSessionSettings(platformId)

    override suspend fun saveSessionSettings(platformId: String, settings: SessionPlatformSettings) {
        editSettings { prefs ->
            prefs.applySessionSettings(platformId, prefs.toSessionSettings(platformId), settings)
        }
    }

    override suspend fun editSessionSettings(
        platformId: String,
        guard: (SessionPlatformSettings) -> Boolean,
        transform: (SessionPlatformSettings) -> SessionPlatformSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toSessionSettings(platformId)
            if (guard(current)) {
                prefs.applySessionSettings(platformId, current, transform(current))
                applied = true
            }
        }
        return applied
    }

    private fun MutablePreferences.applySessionSettings(
        platformId: String,
        previous: SessionPlatformSettings,
        settings: SessionPlatformSettings,
    ) {
        val credentialsChanged = previous.accessToken != settings.accessToken ||
            previous.refreshToken != settings.refreshToken ||
            previous.apiKey != settings.apiKey ||
            previous.expiresAtEpochMs != settings.expiresAtEpochMs
        if (!settings.isConfiguredFor(platformId)) {
            remove(sessionAccessKey(platformId))
            remove(sessionRefreshKey(platformId))
            remove(sessionApiKey(platformId))
            remove(sessionExpiresAtKey(platformId))
            remove(sessionAccountIdKey(platformId))
            remove(sessionEmailKey(platformId))
            remove(sessionPlanKey(platformId))
            remove(sessionProjectIdKey(platformId))
            remove(sessionDomainKey(platformId))
            remove(sessionUidKey(platformId))
            remove(sessionEnterpriseIdKey(platformId))
            clearWidgetPayload(platformId)
            this[widgetStatusKey(platformId)] = Status.NOT_CONFIGURED
        } else {
            putEncrypted(this, sessionAccessKey(platformId), settings.accessToken, platformId, "access")
            putEncrypted(this, sessionRefreshKey(platformId), settings.refreshToken, platformId, "refresh")
            putEncrypted(this, sessionApiKey(platformId), settings.apiKey, platformId, "api_key")
            if (settings.expiresAtEpochMs > 0L) {
                this[sessionExpiresAtKey(platformId)] = settings.expiresAtEpochMs
            } else {
                remove(sessionExpiresAtKey(platformId))
            }
            putOrRemove(sessionAccountIdKey(platformId), settings.accountId.trim())
            putOrRemove(sessionEmailKey(platformId), settings.email.trim())
            putOrRemove(sessionPlanKey(platformId), settings.plan.trim())
            putOrRemove(sessionProjectIdKey(platformId), settings.projectId.trim())
            putOrRemove(sessionDomainKey(platformId), settings.domain.trim())
            putOrRemove(sessionUidKey(platformId), settings.uid.trim())
            putOrRemove(sessionEnterpriseIdKey(platformId), settings.enterpriseId.trim())
            val status = this[widgetStatusKey(platformId)]
            if (credentialsChanged ||
                status == null ||
                status == Status.NOT_CONFIGURED ||
                status == Status.NEEDS_REAUTH
            ) {
                this[widgetStatusKey(platformId)] = Status.LOADING
            }
        }
        this[sessionWidgetWindowKey(platformId)] = settings.widgetWindowKind.name
        this[sessionUsageDisplayKey(platformId)] = settings.usageDisplayMode.name
        this[sessionUsageProgressStyleKey(platformId)] = settings.usageProgressStyle.name
        this[sessionUsageResetStyleKey(platformId)] = settings.usageResetStyle.name
    }

    override suspend fun clearSessionSettings(platformId: String) {
        val current = getSessionSettings(platformId)
        saveSessionSettings(
            platformId,
            SessionPlatformSettings(
                widgetWindowKind = current.widgetWindowKind,
                usageDisplayMode = current.usageDisplayMode,
                usageProgressStyle = current.usageProgressStyle,
                usageResetStyle = current.usageResetStyle,
            ),
        )
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
        editSettings { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.SUCCESS
            prefs[widgetPlatformIdKey(platformId)] = snapshot.platformId
            prefs[widgetPlatformNameKey(platformId)] = snapshot.platformName
            prefs[widgetCurrencyKey(platformId)] = snapshot.currency.name
            prefs[widgetTotalKey(platformId)] = snapshot.totalBalance
            prefs[widgetFormattedKey(platformId)] = snapshot.primaryDisplay
            prefs[widgetUpdatedAtKey(platformId)] = snapshot.updatedAtEpochMs
            prefs[widgetWindowsKey(platformId)] = encodeQuotaWindows(snapshot.windows)
            prefs[widgetUnlimitedKey(platformId)] = snapshot.unlimitedQuota
            prefs[widgetEmptyLimitedQuotaKey(platformId)] = snapshot.emptyLimitedQuota
            prefs[widgetTokenExpiredKey(platformId)] = snapshot.tokenExpired
            prefs[widgetQuotaOverspentKey(platformId)] = snapshot.quotaOverspent
            if (snapshot.usedDisplay.isBlank()) {
                prefs.remove(widgetUsedDisplayKey(platformId))
            } else {
                prefs[widgetUsedDisplayKey(platformId)] = snapshot.usedDisplay
            }
            prefs.remove(widgetErrorKey(platformId))
        }
    }

    override suspend fun saveWidgetError(platformId: String, message: String) {
        editSettings { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.ERROR
            prefs[widgetErrorKey(platformId)] = message
        }
    }

    override suspend fun saveWidgetLoading(platformId: String) {
        editSettings { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.LOADING
            prefs.remove(widgetErrorKey(platformId))
        }
    }

    override suspend fun saveWidgetNotConfigured(platformId: String) {
        editSettings { prefs ->
            prefs.migrateLegacyWidgetIfNeeded()
            prefs[widgetStatusKey(platformId)] = Status.NOT_CONFIGURED
            prefs.clearWidgetPayload(platformId)
        }
    }

    override suspend fun saveWidgetNeedsReauth(platformId: String) {
        editSettings { prefs ->
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
        editSettings { prefs ->
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
        editSettings { prefs ->
            prefs[refreshStartedAtKey(platformId)] = epochMs
            prefs.remove(Keys.LEGACY_REFRESH_STARTED_AT)
        }
    }

    override suspend fun clearAllRefreshIconPhases() {
        editSettings { prefs ->
            for (platformId in buildList {
                add(PlatformIds.DEEPSEEK)
                add(PlatformIds.OPENCODE_GO)
                add(PlatformIds.CODEX)
                add(PlatformIds.NEW_API)
                add(PlatformIds.MAGPIE)
                add(PlatformIds.KIMI_CODE)
                add(PlatformIds.ZHIPU)
                addAll(PlatformIds.sessionPlatforms)
            }) {
                prefs[refreshPhaseKey(platformId)] = RefreshIconPhase.Idle.name
            }
            prefs.remove(Keys.LEGACY_REFRESH_ICON_PHASE)
        }
    }

    override suspend fun isPlatformTipDismissed(): Boolean =
        dataStore.data.first()[Keys.PLATFORM_TIP_DISMISSED] == true

    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        editSettings { prefs ->
            prefs[Keys.PLATFORM_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun isOemBackgroundTipDismissed(): Boolean =
        dataStore.data.first()[Keys.OEM_BACKGROUND_TIP_DISMISSED] == true

    override suspend fun setOemBackgroundTipDismissed(dismissed: Boolean) {
        editSettings { prefs ->
            prefs[Keys.OEM_BACKGROUND_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun getUpdateIgnoredVersion(): String? =
        dataStore.data.first()[Keys.UPDATE_IGNORED_VERSION]

    override suspend fun setUpdateIgnoredVersion(version: String?) {
        editSettings { prefs ->
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
        editSettings { prefs ->
            prefs[Keys.DARK_THEME_MODE] = settings.darkThemeMode.name
            prefs[Keys.THEME_COLOR_MODE] = settings.themeColorMode.name
            prefs[Keys.CUSTOM_SEED_COLOR_ARGB] = settings.customSeedColorArgb
            prefs[Keys.REFRESH_INTERVAL_MINUTES] = settings.refreshIntervalMinutes
            prefs[Keys.CHECK_FOR_UPDATES_ON_LAUNCH] = settings.checkForUpdatesOnLaunch
            prefs[Keys.APP_LANGUAGE] = settings.appLanguage.name
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
            appLanguage = AppLanguage.fromStorage(this[Keys.APP_LANGUAGE]),
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
        val encryptedApiKey = this[Keys.OPENCODE_API_KEY_ENC]
        val apiKey = encryptedApiKey
            ?.let { runCatching { decrypt(it, OPENCODE_API_KEY_AD) }.getOrDefault("") }
            .orEmpty()
        val authMethod = this[Keys.OPENCODE_AUTH_METHOD]?.let { stored ->
            runCatching { OpenCodeAuthMethod.valueOf(stored) }.getOrNull()
        }
        return OpenCodeGoSettings(
            workspaceId = this[Keys.OPENCODE_WORKSPACE_ID].orEmpty(),
            workspaceName = this[Keys.OPENCODE_WORKSPACE_NAME].orEmpty(),
            authCookie = authCookie,
            apiKey = apiKey,
            authMethod = authMethod,
            widgetWindowKind = UsageWindowKind.fromStorage(this[Keys.OPENCODE_WIDGET_WINDOW]),
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.OPENCODE_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.OPENCODE_USAGE_PROGRESS_STYLE],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[Keys.OPENCODE_USAGE_RESET_STYLE],
            ),
        )
    }

    private fun Preferences.toCodexSettings(): CodexSettings {
        fun decryptOrEmpty(key: Preferences.Key<String>, ad: ByteArray): String =
            this[key]?.let { runCatching { decrypt(it, ad) }.getOrDefault("") }.orEmpty()
        return CodexSettings(
            accessToken = decryptOrEmpty(Keys.CODEX_ACCESS_ENC, CODEX_ACCESS_AD),
            refreshToken = decryptOrEmpty(Keys.CODEX_REFRESH_ENC, CODEX_REFRESH_AD),
            idToken = decryptOrEmpty(Keys.CODEX_ID_ENC, CODEX_ID_AD),
            accountId = this[Keys.CODEX_ACCOUNT_ID].orEmpty(),
            expiresAtEpochMs = this[Keys.CODEX_EXPIRES_AT] ?: 0L,
            email = this[Keys.CODEX_EMAIL].orEmpty(),
            planType = this[Keys.CODEX_PLAN_TYPE].orEmpty(),
            widgetWindowKind = this[Keys.CODEX_WIDGET_WINDOW]
                ?.let { UsageWindowKind.fromStorage(it) }
                ?: UsageWindowKind.WEEKLY,
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.CODEX_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.CODEX_USAGE_PROGRESS_STYLE],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[Keys.CODEX_USAGE_RESET_STYLE],
            ),
        )
    }

    private fun Preferences.toNewApiSettings(): NewApiSettings {
        val encrypted = this[Keys.NEW_API_KEY_ENC]
        val apiKey = encrypted
            ?.let { runCatching { decrypt(it, NEW_API_KEY_AD) }.getOrDefault("") }
            .orEmpty()
        val quotaPerUsd = this[Keys.NEW_API_QUOTA_PER_USD]
            ?.takeIf { it > 0L }
            ?: DEFAULT_NEW_API_QUOTA_PER_USD
        return NewApiSettings(
            baseUrl = this[Keys.NEW_API_BASE_URL].orEmpty(),
            apiKey = apiKey,
            quotaPerUsd = quotaPerUsd,
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.NEW_API_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.NEW_API_USAGE_PROGRESS_STYLE],
            ),
        )
    }

    private fun Preferences.toMagpieSettings(): MagpieSettings {
        val encrypted = this[Keys.MAGPIE_KEY_ENC]
        val apiKey = encrypted
            ?.let { runCatching { decrypt(it, MAGPIE_KEY_AD) }.getOrDefault("") }
            .orEmpty()
        return MagpieSettings(
            baseUrl = this[Keys.MAGPIE_BASE_URL].orEmpty(),
            apiKey = apiKey,
            selectedProviderId = this[Keys.MAGPIE_SELECTED_PROVIDER].orEmpty(),
            providers = decodeMagpieProviders(this[Keys.MAGPIE_PROVIDERS]),
            widgetWindowKind = UsageWindowKind.fromStorage(this[Keys.MAGPIE_WIDGET_WINDOW]),
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.MAGPIE_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.MAGPIE_USAGE_PROGRESS_STYLE],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[Keys.MAGPIE_USAGE_RESET_STYLE],
            ),
        )
    }

    private fun Preferences.toKimiCodeSettings(): KimiCodeSettings {
        val apiKey = this[Keys.KIMI_CODE_API_KEY_ENC]
            ?.let { runCatching { decrypt(it, KIMI_CODE_KEY_AD) }.getOrDefault("") }
            .orEmpty()
        return KimiCodeSettings(
            apiKey = apiKey,
            region = ServiceRegion.fromStorage(this[Keys.KIMI_CODE_REGION]),
            widgetWindowKind = UsageWindowKind.fromStorage(this[Keys.KIMI_CODE_WIDGET_WINDOW]),
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.KIMI_CODE_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.KIMI_CODE_USAGE_PROGRESS_STYLE],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[Keys.KIMI_CODE_USAGE_RESET_STYLE],
            ),
        )
    }

    private fun Preferences.toZhipuSettings(): ZhipuSettings {
        val apiKey = this[Keys.ZHIPU_API_KEY_ENC]
            ?.let { runCatching { decrypt(it, ZHIPU_KEY_AD) }.getOrDefault("") }
            .orEmpty()
        return ZhipuSettings(
            apiKey = apiKey,
            region = ServiceRegion.fromStorage(this[Keys.ZHIPU_REGION]),
            widgetWindowKind = UsageWindowKind.fromStorage(this[Keys.ZHIPU_WIDGET_WINDOW]),
            usageDisplayMode = UsageDisplayMode.fromStorage(this[Keys.ZHIPU_USAGE_DISPLAY]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[Keys.ZHIPU_USAGE_PROGRESS_STYLE],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[Keys.ZHIPU_USAGE_RESET_STYLE],
            ),
        )
    }

    private fun Preferences.toSessionSettings(platformId: String): SessionPlatformSettings {
        fun decryptOrEmpty(key: Preferences.Key<String>, field: String): String =
            this[key]?.let {
                runCatching { decrypt(it, sessionAd(platformId, field)) }.getOrDefault("")
            }.orEmpty()
        return SessionPlatformSettings(
            accessToken = decryptOrEmpty(sessionAccessKey(platformId), "access"),
            refreshToken = decryptOrEmpty(sessionRefreshKey(platformId), "refresh"),
            apiKey = decryptOrEmpty(sessionApiKey(platformId), "api_key"),
            expiresAtEpochMs = this[sessionExpiresAtKey(platformId)] ?: 0L,
            accountId = this[sessionAccountIdKey(platformId)].orEmpty(),
            email = this[sessionEmailKey(platformId)].orEmpty(),
            plan = this[sessionPlanKey(platformId)].orEmpty(),
            projectId = this[sessionProjectIdKey(platformId)].orEmpty(),
            domain = this[sessionDomainKey(platformId)].orEmpty(),
            uid = this[sessionUidKey(platformId)].orEmpty(),
            enterpriseId = this[sessionEnterpriseIdKey(platformId)].orEmpty(),
            widgetWindowKind = this[sessionWidgetWindowKey(platformId)]
                ?.let { UsageWindowKind.fromStorage(it) }
                ?: SessionPlatformSettings.defaultWindowKind(platformId),
            usageDisplayMode = UsageDisplayMode.fromStorage(this[sessionUsageDisplayKey(platformId)]),
            usageProgressStyle = UsageProgressStyle.fromStorage(
                this[sessionUsageProgressStyleKey(platformId)],
            ),
            usageResetStyle = UsageResetStyle.fromStorage(
                this[sessionUsageResetStyleKey(platformId)],
            ),
        )
    }

    private fun Preferences.isConfigured(platformId: String): Boolean =
        when (platformId) {
            PlatformIds.DEEPSEEK -> toDeepSeekSettings().apiKey.isNotBlank()
            PlatformIds.OPENCODE_GO -> toOpenCodeGoSettings().isConfigured
            PlatformIds.CODEX -> toCodexSettings().isConfigured
            PlatformIds.NEW_API -> toNewApiSettings().isConfigured
            PlatformIds.MAGPIE -> toMagpieSettings().isConfigured
            PlatformIds.KIMI_CODE -> toKimiCodeSettings().isConfigured
            PlatformIds.ZHIPU -> toZhipuSettings().isConfigured
            in PlatformIds.sessionPlatforms -> toSessionSettings(platformId).isConfiguredFor(platformId)
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
                        unlimitedQuota = this[widgetUnlimitedKey(platformId)] == true,
                        usedDisplay = this[widgetUsedDisplayKey(platformId)].orEmpty(),
                        emptyLimitedQuota = this[widgetEmptyLimitedQuotaKey(platformId)] == true,
                        tokenExpired = this[widgetTokenExpiredKey(platformId)] == true,
                        quotaOverspent = this[widgetQuotaOverspentKey(platformId)] == true,
                    ),
                )
            }
            else -> WidgetDisplayState.Loading
        }
    }

    private fun defaultWindows(platformId: String): List<QuotaWindow> =
        when (platformId) {
            PlatformIds.DEEPSEEK -> listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE))
            PlatformIds.NEW_API -> listOf(
                QuotaWindow(kind = QuotaWindowKind.BALANCE),
                QuotaWindow(kind = QuotaWindowKind.TOKEN),
            )
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
        remove(widgetUnlimitedKey(platformId))
        remove(widgetUsedDisplayKey(platformId))
        remove(widgetEmptyLimitedQuotaKey(platformId))
        remove(widgetTokenExpiredKey(platformId))
        remove(widgetQuotaOverspentKey(platformId))
    }

    private fun putEncrypted(
        prefs: MutablePreferences,
        key: Preferences.Key<String>,
        value: String,
        platformId: String,
        field: String,
    ) {
        if (value.isBlank()) {
            prefs.remove(key)
        } else {
            prefs[key] = encrypt(value, sessionAd(platformId, field))
        }
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String) {
        if (value.isBlank()) remove(key) else this[key] = value
    }

    private fun sessionAd(platformId: String, field: String): ByteArray =
        "session_${platformId}_$field".encodeToByteArray()

    private fun encrypt(plain: String, associatedData: ByteArray): String {
        val cipher = aead.load().aead.encrypt(plain.encodeToByteArray(), associatedData)
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String, associatedData: ByteArray): String {
        val cipher = Base64.decode(encoded, Base64.NO_WRAP)
        return aead.load().aead.decrypt(cipher, associatedData).decodeToString()
    }

    /**
     * Applies [transform], recreating a lost keyset first and dropping the ciphertexts that the
     * discarded keyset can no longer decrypt, so [transform] can store fresh ciphertexts. The
     * pending cleanup survives the read path that may have triggered the recovery; it is cleared
     * only after this write persisted the removal.
     */
    private suspend fun editSettings(transform: suspend (MutablePreferences) -> Unit) {
        settingsWriteMutex.withLock {
            val cleanupCiphertexts =
                runCatching { aead.load().requiresCiphertextCleanup }.getOrDefault(false)
            dataStore.edit { prefs ->
                if (cleanupCiphertexts) prefs.removeUndecryptableStoredCiphertexts()
                transform(prefs)
            }
            if (cleanupCiphertexts) aead.acknowledgeCiphertextCleanup()
        }
    }

    private fun MutablePreferences.removeUndecryptableStoredCiphertexts() {
        asMap().keys
            .filter { isEncryptedPreferenceName(it.name) }
            .forEach { remove(it) }
    }

    private object Keys {
        val API_KEY_ENCRYPTED = stringPreferencesKey("deepseek_api_key_enc")
        val CURRENCY = stringPreferencesKey("deepseek_currency")
        val OPENCODE_WORKSPACE_ID = stringPreferencesKey("opencode_workspace_id")
        val OPENCODE_WORKSPACE_NAME = stringPreferencesKey("opencode_workspace_name")
        val OPENCODE_AUTH_COOKIE_ENC = stringPreferencesKey("opencode_auth_cookie_enc")
        val OPENCODE_API_KEY_ENC = stringPreferencesKey("opencode_api_key_enc")
        val OPENCODE_AUTH_METHOD = stringPreferencesKey("opencode_auth_method")
        val OPENCODE_WIDGET_WINDOW = stringPreferencesKey("opencode_go_widget_window")
        val OPENCODE_USAGE_DISPLAY = stringPreferencesKey("opencode_go_usage_display")
        val OPENCODE_USAGE_PROGRESS_STYLE =
            stringPreferencesKey("opencode_go_usage_progress_style")
        val OPENCODE_USAGE_RESET_STYLE =
            stringPreferencesKey("opencode_go_usage_reset_style")
        val CODEX_ACCESS_ENC = stringPreferencesKey("codex_access_token_enc")
        val CODEX_REFRESH_ENC = stringPreferencesKey("codex_refresh_token_enc")
        val CODEX_ID_ENC = stringPreferencesKey("codex_id_token_enc")
        val CODEX_ACCOUNT_ID = stringPreferencesKey("codex_account_id")
        val CODEX_EXPIRES_AT = longPreferencesKey("codex_expires_at")
        val CODEX_EMAIL = stringPreferencesKey("codex_email")
        val CODEX_PLAN_TYPE = stringPreferencesKey("codex_plan_type")
        val CODEX_WIDGET_WINDOW = stringPreferencesKey("codex_widget_window")
        val CODEX_USAGE_DISPLAY = stringPreferencesKey("codex_usage_display")
        val CODEX_USAGE_PROGRESS_STYLE = stringPreferencesKey("codex_usage_progress_style")
        val CODEX_USAGE_RESET_STYLE = stringPreferencesKey("codex_usage_reset_style")
        val NEW_API_BASE_URL = stringPreferencesKey("new_api_base_url")
        val NEW_API_KEY_ENC = stringPreferencesKey("new_api_key_enc")
        val NEW_API_QUOTA_PER_USD = longPreferencesKey("new_api_quota_per_usd")
        val NEW_API_USAGE_DISPLAY = stringPreferencesKey("new_api_usage_display")
        val NEW_API_USAGE_PROGRESS_STYLE = stringPreferencesKey("new_api_usage_progress_style")
        val MAGPIE_BASE_URL = stringPreferencesKey("magpie_base_url")
        val MAGPIE_KEY_ENC = stringPreferencesKey("magpie_key_enc")
        val MAGPIE_PROVIDERS = stringPreferencesKey("magpie_providers")
        val MAGPIE_SELECTED_PROVIDER = stringPreferencesKey("magpie_selected_provider")
        val MAGPIE_WIDGET_WINDOW = stringPreferencesKey("magpie_widget_window")
        val MAGPIE_USAGE_DISPLAY = stringPreferencesKey("magpie_usage_display")
        val MAGPIE_USAGE_PROGRESS_STYLE = stringPreferencesKey("magpie_usage_progress_style")
        val MAGPIE_USAGE_RESET_STYLE = stringPreferencesKey("magpie_usage_reset_style")
        val KIMI_CODE_API_KEY_ENC = stringPreferencesKey("kimi_code_api_key_enc")
        val KIMI_CODE_REGION = stringPreferencesKey("kimi_code_region")
        val KIMI_CODE_WIDGET_WINDOW = stringPreferencesKey("kimi_code_widget_window")
        val KIMI_CODE_USAGE_DISPLAY = stringPreferencesKey("kimi_code_usage_display")
        val KIMI_CODE_USAGE_PROGRESS_STYLE =
            stringPreferencesKey("kimi_code_usage_progress_style")
        val KIMI_CODE_USAGE_RESET_STYLE =
            stringPreferencesKey("kimi_code_usage_reset_style")
        val ZHIPU_API_KEY_ENC = stringPreferencesKey("zhipu_api_key_enc")
        val ZHIPU_REGION = stringPreferencesKey("zhipu_region")
        val ZHIPU_WIDGET_WINDOW = stringPreferencesKey("zhipu_widget_window")
        val ZHIPU_USAGE_DISPLAY = stringPreferencesKey("zhipu_usage_display")
        val ZHIPU_USAGE_PROGRESS_STYLE = stringPreferencesKey("zhipu_usage_progress_style")
        val ZHIPU_USAGE_RESET_STYLE = stringPreferencesKey("zhipu_usage_reset_style")
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
        val APP_LANGUAGE = stringPreferencesKey("app_language")
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
        private val OPENCODE_API_KEY_AD = "opencode_api_key".encodeToByteArray()
        private val CODEX_ACCESS_AD = "codex_access_token".encodeToByteArray()
        private val CODEX_REFRESH_AD = "codex_refresh_token".encodeToByteArray()
        private val CODEX_ID_AD = "codex_id_token".encodeToByteArray()
        private val NEW_API_KEY_AD = "new_api_key".encodeToByteArray()
        private val MAGPIE_KEY_AD = "magpie_key".encodeToByteArray()
        private val KIMI_CODE_KEY_AD = "kimi_code_api_key".encodeToByteArray()
        private val ZHIPU_KEY_AD = "zhipu_api_key".encodeToByteArray()

        private fun sessionAccessKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_access_enc")

        private fun sessionRefreshKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_refresh_enc")

        private fun sessionApiKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_api_key_enc")

        private fun sessionExpiresAtKey(platformId: String) =
            longPreferencesKey("session_${platformId}_expires_at")

        private fun sessionAccountIdKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_account_id")

        private fun sessionEmailKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_email")

        private fun sessionPlanKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_plan")

        private fun sessionProjectIdKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_project_id")

        private fun sessionDomainKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_domain")

        private fun sessionUidKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_uid")

        private fun sessionEnterpriseIdKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_enterprise_id")

        private fun sessionWidgetWindowKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_widget_window")

        private fun sessionUsageDisplayKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_usage_display")

        private fun sessionUsageProgressStyleKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_usage_progress_style")

        private fun sessionUsageResetStyleKey(platformId: String) =
            stringPreferencesKey("session_${platformId}_usage_reset_style")

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

        private fun widgetUnlimitedKey(platformId: String) =
            booleanPreferencesKey("widget_${platformId}_unlimited")

        private fun widgetUsedDisplayKey(platformId: String) =
            stringPreferencesKey("widget_${platformId}_used_display")

        private fun widgetEmptyLimitedQuotaKey(platformId: String) =
            booleanPreferencesKey("widget_${platformId}_empty_limited_quota")

        private fun widgetTokenExpiredKey(platformId: String) =
            booleanPreferencesKey("widget_${platformId}_token_expired")

        private fun widgetQuotaOverspentKey(platformId: String) =
            booleanPreferencesKey("widget_${platformId}_quota_overspent")

        private fun refreshPhaseKey(platformId: String) =
            stringPreferencesKey("refresh_${platformId}_icon_phase")

        private fun refreshStartedAtKey(platformId: String) =
            longPreferencesKey("refresh_${platformId}_started_at")

        private const val TINK_KEYSET_NAME = "quota_widget_tink_keyset"
        private const val TINK_KEYSET_PREFERENCES = "quota_widget_tink_prefs"
        private const val TINK_MASTER_KEY_ID = "quota_widget_master_key"

        private fun createAead(context: Context): Aead {
            AeadConfig.register()
            return AndroidKeysetManager.Builder()
                .withSharedPref(context, TINK_KEYSET_NAME, TINK_KEYSET_PREFERENCES)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://$TINK_MASTER_KEY_ID")
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }

        private fun resetTinkKeyset(context: Context) {
            context.getSharedPreferences(TINK_KEYSET_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            runCatching { AndroidKeystore.deleteKey(TINK_MASTER_KEY_ID) }
        }
    }
}
