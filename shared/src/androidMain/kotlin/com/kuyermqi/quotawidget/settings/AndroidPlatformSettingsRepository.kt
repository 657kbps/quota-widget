package com.kuyermqi.quotawidget.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.WidgetErrorKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.refresh.isRefreshPhaseStale
import com.kuyermqi.quotawidget.refresh.shouldResetRefreshPhase
import com.kuyermqi.quotawidget.util.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val crypto = SettingsCrypto(
        encrypt = { plain, associatedData -> encrypt(plain, associatedData) },
        decrypt = { encoded, associatedData -> decrypt(encoded, associatedData) },
    )

    override fun observeDeepSeekSettings(): Flow<DeepSeekSettings> =
        dataStore.data.map { prefs -> prefs.toDeepSeekSettings(crypto) }.distinctUntilChanged()

    override suspend fun getDeepSeekSettings(): DeepSeekSettings =
        dataStore.data.first().toDeepSeekSettings(crypto)

    override suspend fun saveDeepSeekSettings(settings: DeepSeekSettings) {
        editSettings { prefs ->
            if (settings.apiKey.isBlank()) {
                prefs.remove(SettingsKeys.API_KEY_ENCRYPTED)
                prefs.clearWidgetPayload(PlatformIds.DEEPSEEK)
                prefs[widgetStatusKey(PlatformIds.DEEPSEEK)] = StoredWidgetStatus.NOT_CONFIGURED
            } else {
                prefs[SettingsKeys.API_KEY_ENCRYPTED] = encrypt(settings.apiKey, API_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.DEEPSEEK)]
                if (status == null || status == StoredWidgetStatus.NOT_CONFIGURED) {
                    prefs[widgetStatusKey(PlatformIds.DEEPSEEK)] = StoredWidgetStatus.LOADING
                }
            }
            prefs[SettingsKeys.CURRENCY] = settings.currency.name
        }
    }

    override fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings> =
        dataStore.data.map { prefs -> prefs.toOpenCodeGoSettings(crypto) }.distinctUntilChanged()

    override suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings =
        dataStore.data.first().toOpenCodeGoSettings(crypto)

    override suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings) {
        val previous = getOpenCodeGoSettings()
        val credentialsChanged = previous.effectiveAuthMethod != settings.effectiveAuthMethod ||
            previous.workspaceId != settings.workspaceId ||
            previous.authCookie != settings.authCookie ||
            previous.apiKey != settings.apiKey
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(SettingsKeys.OPENCODE_WORKSPACE_ID)
                prefs.remove(SettingsKeys.OPENCODE_WORKSPACE_NAME)
                prefs.remove(SettingsKeys.OPENCODE_AUTH_COOKIE_ENC)
                prefs.remove(SettingsKeys.OPENCODE_API_KEY_ENC)
                prefs.remove(SettingsKeys.OPENCODE_AUTH_METHOD)
                prefs.clearWidgetPayload(PlatformIds.OPENCODE_GO)
                prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = StoredWidgetStatus.NOT_CONFIGURED
            } else {
                if (settings.workspaceId.isBlank()) {
                    prefs.remove(SettingsKeys.OPENCODE_WORKSPACE_ID)
                } else {
                    prefs[SettingsKeys.OPENCODE_WORKSPACE_ID] = settings.workspaceId
                }
                if (settings.workspaceName.isBlank()) {
                    prefs.remove(SettingsKeys.OPENCODE_WORKSPACE_NAME)
                } else {
                    prefs[SettingsKeys.OPENCODE_WORKSPACE_NAME] = settings.workspaceName
                }
                if (settings.authCookie.isBlank()) {
                    prefs.remove(SettingsKeys.OPENCODE_AUTH_COOKIE_ENC)
                } else {
                    prefs[SettingsKeys.OPENCODE_AUTH_COOKIE_ENC] = encrypt(settings.authCookie, OPENCODE_AUTH_AD)
                }
                if (settings.apiKey.isBlank()) {
                    prefs.remove(SettingsKeys.OPENCODE_API_KEY_ENC)
                } else {
                    prefs[SettingsKeys.OPENCODE_API_KEY_ENC] = encrypt(settings.apiKey, OPENCODE_API_KEY_AD)
                }
                prefs[SettingsKeys.OPENCODE_AUTH_METHOD] = settings.effectiveAuthMethod.name
                val status = prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)]
                if (credentialsChanged ||
                    status == null ||
                    status == StoredWidgetStatus.NOT_CONFIGURED ||
                    status == StoredWidgetStatus.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.OPENCODE_GO)] = StoredWidgetStatus.LOADING
                }
            }
            prefs.applyUsageDisplayPrefs(PlatformIds.OPENCODE_GO, settings.toUsageDisplayPrefs())
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
        dataStore.data.map { prefs -> prefs.toCodexSettings(crypto) }.distinctUntilChanged()

    override suspend fun getCodexSettings(): CodexSettings =
        dataStore.data.first().toCodexSettings(crypto)

    override suspend fun saveCodexSettings(settings: CodexSettings) {
        editSettings { prefs -> prefs.applyCodexSettings(settings, crypto) }
    }

    override suspend fun editCodexSettings(
        guard: (CodexSettings) -> Boolean,
        transform: (CodexSettings) -> CodexSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toCodexSettings(crypto)
            if (guard(current)) {
                prefs.applyCodexSettings(transform(current), crypto)
                applied = true
            }
        }
        return applied
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
        dataStore.data.map { prefs -> prefs.toNewApiSettings(crypto) }.distinctUntilChanged()

    override suspend fun getNewApiSettings(): NewApiSettings =
        dataStore.data.first().toNewApiSettings(crypto)

    override suspend fun saveNewApiSettings(settings: NewApiSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(SettingsKeys.NEW_API_BASE_URL)
                prefs.remove(SettingsKeys.NEW_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.NEW_API)
                prefs[widgetStatusKey(PlatformIds.NEW_API)] = StoredWidgetStatus.NOT_CONFIGURED
            } else {
                prefs[SettingsKeys.NEW_API_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
                prefs[SettingsKeys.NEW_API_KEY_ENC] = encrypt(settings.apiKey, NEW_API_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.NEW_API)]
                if (status == null ||
                    status == StoredWidgetStatus.NOT_CONFIGURED ||
                    status == StoredWidgetStatus.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.NEW_API)] = StoredWidgetStatus.LOADING
                }
            }
            prefs.applyUsageDisplayPrefs(PlatformIds.NEW_API, settings.toUsageDisplayPrefs())
            prefs[SettingsKeys.NEW_API_QUOTA_PER_USD] = settings.quotaPerUsd.takeIf { it > 0L }
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
        dataStore.data.map { prefs -> prefs.toMagpieSettings(crypto) }.distinctUntilChanged()

    override suspend fun getMagpieSettings(): MagpieSettings =
        dataStore.data.first().toMagpieSettings(crypto)

    override suspend fun saveMagpieSettings(settings: MagpieSettings) {
        editSettings { prefs -> prefs.applyMagpieSettings(settings, crypto) }
    }

    override suspend fun editMagpieSettings(
        guard: (MagpieSettings) -> Boolean,
        transform: (MagpieSettings) -> MagpieSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toMagpieSettings(crypto)
            if (guard(current)) {
                prefs.applyMagpieSettings(transform(current), crypto)
                applied = true
            }
        }
        return applied
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
        dataStore.data.map { prefs -> prefs.toKimiCodeSettings(crypto) }.distinctUntilChanged()

    override suspend fun getKimiCodeSettings(): KimiCodeSettings =
        dataStore.data.first().toKimiCodeSettings(crypto)

    override suspend fun saveKimiCodeSettings(settings: KimiCodeSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(SettingsKeys.KIMI_CODE_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.KIMI_CODE)
                prefs[widgetStatusKey(PlatformIds.KIMI_CODE)] = StoredWidgetStatus.NOT_CONFIGURED
            } else {
                prefs[SettingsKeys.KIMI_CODE_API_KEY_ENC] = encrypt(settings.apiKey, KIMI_CODE_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.KIMI_CODE)]
                if (status == null ||
                    status == StoredWidgetStatus.NOT_CONFIGURED ||
                    status == StoredWidgetStatus.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.KIMI_CODE)] = StoredWidgetStatus.LOADING
                }
            }
            prefs[SettingsKeys.KIMI_CODE_REGION] = settings.region.name
            prefs.applyUsageDisplayPrefs(PlatformIds.KIMI_CODE, settings.toUsageDisplayPrefs())
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
        dataStore.data.map { prefs -> prefs.toZhipuSettings(crypto) }.distinctUntilChanged()

    override suspend fun getZhipuSettings(): ZhipuSettings =
        dataStore.data.first().toZhipuSettings(crypto)

    override suspend fun saveZhipuSettings(settings: ZhipuSettings) {
        editSettings { prefs ->
            if (!settings.isConfigured) {
                prefs.remove(SettingsKeys.ZHIPU_API_KEY_ENC)
                prefs.clearWidgetPayload(PlatformIds.ZHIPU)
                prefs[widgetStatusKey(PlatformIds.ZHIPU)] = StoredWidgetStatus.NOT_CONFIGURED
            } else {
                prefs[SettingsKeys.ZHIPU_API_KEY_ENC] = encrypt(settings.apiKey, ZHIPU_KEY_AD)
                val status = prefs[widgetStatusKey(PlatformIds.ZHIPU)]
                if (status == null ||
                    status == StoredWidgetStatus.NOT_CONFIGURED ||
                    status == StoredWidgetStatus.NEEDS_REAUTH
                ) {
                    prefs[widgetStatusKey(PlatformIds.ZHIPU)] = StoredWidgetStatus.LOADING
                }
            }
            prefs[SettingsKeys.ZHIPU_REGION] = settings.region.name
            prefs.applyUsageDisplayPrefs(PlatformIds.ZHIPU, settings.toUsageDisplayPrefs())
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
        dataStore.data.map { prefs -> prefs.toSessionSettings(platformId, crypto) }.distinctUntilChanged()

    override suspend fun getSessionSettings(platformId: String): SessionPlatformSettings =
        dataStore.data.first().toSessionSettings(platformId, crypto)

    override suspend fun saveSessionSettings(platformId: String, settings: SessionPlatformSettings) {
        editSettings { prefs ->
            prefs.applySessionSettings(platformId, prefs.toSessionSettings(platformId, crypto), settings, crypto)
        }
    }

    override suspend fun editSessionSettings(
        platformId: String,
        guard: (SessionPlatformSettings) -> Boolean,
        transform: (SessionPlatformSettings) -> SessionPlatformSettings,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val current = prefs.toSessionSettings(platformId, crypto)
            if (guard(current)) {
                prefs.applySessionSettings(platformId, current, transform(current), crypto)
                applied = true
            }
        }
        return applied
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
        dataStore.data.map { prefs -> prefs.toWidgetState(platformId) }.distinctUntilChanged()

    override suspend fun getWidgetState(platformId: String): WidgetDisplayState {
        ensureLegacyMigrated()
        return dataStore.data.first().toWidgetState(platformId)
    }

    override suspend fun configuredPlatformIds(platformIds: List<String>): List<String> {
        val prefs = dataStore.data.first()
        return platformIds.filter { platformId -> prefs.isConfigured(platformId) }
    }

    override suspend fun getWidgetRenderSnapshot(
        platformIds: Set<String>,
    ): WidgetRenderSnapshot {
        ensureLegacyMigrated()
        val prefs = dataStore.data.first()
        return WidgetRenderSnapshot.of(
            platformIds = platformIds,
            appSettings = prefs.toAppSettings(),
        ) { platformId ->
            WidgetPlatformRenderState(
                display = prefs.toWidgetState(platformId),
                phase = prefs.toRefreshIconPhase(platformId),
                card = prefs.toWidgetCardSettings(platformId),
            )
        }
    }

    private suspend fun ensureLegacyMigrated() {
        dataStore.edit { it.migrateLegacyWidgetIfNeeded() }
    }

    override suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot) {
        editSettings { prefs -> prefs.applyWidgetSnapshot(platformId, snapshot) }
    }

    override suspend fun saveWidgetError(platformId: String, kind: WidgetErrorKind) {
        editSettings { prefs -> prefs.applyWidgetError(platformId, kind) }
    }

    override suspend fun saveWidgetLoading(platformId: String) {
        editSettings { prefs -> prefs.applyWidgetLoading(platformId) }
    }

    override suspend fun saveWidgetNotConfigured(platformId: String) {
        editSettings { prefs -> prefs.applyWidgetNotConfigured(platformId) }
    }

    override suspend fun saveWidgetNeedsReauth(platformId: String) {
        editSettings { prefs -> prefs.applyWidgetNeedsReauth(platformId) }
    }

    override suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase =
        dataStore.data.first().toRefreshIconPhase(platformId)

    override suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase) {
        editSettings { prefs ->
            prefs[refreshPhaseKey(platformId)] = phase.name
            prefs.remove(SettingsKeys.LEGACY_REFRESH_ICON_PHASE)
        }
    }

    override suspend fun setRefreshIconPhaseIfStartedAt(
        platformId: String,
        expectedStartedAtEpochMs: Long,
        phase: RefreshIconPhase,
    ): Boolean {
        var applied = false
        editSettings { prefs ->
            val storedStartedAt = prefs[refreshStartedAtKey(platformId)]
                ?: prefs[SettingsKeys.LEGACY_REFRESH_STARTED_AT]
                ?: 0L
            if (!shouldResetRefreshPhase(storedStartedAt, expectedStartedAtEpochMs)) return@editSettings
            prefs[refreshPhaseKey(platformId)] = phase.name
            prefs.remove(SettingsKeys.LEGACY_REFRESH_ICON_PHASE)
            applied = true
        }
        return applied
    }

    override suspend fun getRefreshStartedAtEpochMs(platformId: String): Long {
        val prefs = dataStore.data.first()
        return prefs[refreshStartedAtKey(platformId)]
            ?: prefs[SettingsKeys.LEGACY_REFRESH_STARTED_AT]
            ?: 0L
    }

    override suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long) {
        editSettings { prefs ->
            prefs[refreshStartedAtKey(platformId)] = epochMs
            prefs.remove(SettingsKeys.LEGACY_REFRESH_STARTED_AT)
        }
    }

    override suspend fun clearStaleRefreshIconPhases() {
        val now = currentTimeMillis()
        editSettings { prefs ->
            for (platformId in REFRESH_PHASE_PLATFORM_IDS) {
                val startedAt = prefs[refreshStartedAtKey(platformId)]
                    ?: prefs[SettingsKeys.LEGACY_REFRESH_STARTED_AT]
                    ?: 0L
                if (isRefreshPhaseStale(startedAt, now)) {
                    prefs[refreshPhaseKey(platformId)] = RefreshIconPhase.Idle.name
                }
            }
            prefs.remove(SettingsKeys.LEGACY_REFRESH_ICON_PHASE)
        }
    }

    override suspend fun isPlatformTipDismissed(): Boolean =
        dataStore.data.first()[SettingsKeys.PLATFORM_TIP_DISMISSED] == true

    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        editSettings { prefs ->
            prefs[SettingsKeys.PLATFORM_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun isOemBackgroundTipDismissed(): Boolean =
        dataStore.data.first()[SettingsKeys.OEM_BACKGROUND_TIP_DISMISSED] == true

    override suspend fun setOemBackgroundTipDismissed(dismissed: Boolean) {
        editSettings { prefs ->
            prefs[SettingsKeys.OEM_BACKGROUND_TIP_DISMISSED] = dismissed
        }
    }

    override suspend fun getUpdateIgnoredVersion(): String? =
        dataStore.data.first()[SettingsKeys.UPDATE_IGNORED_VERSION]

    override suspend fun setUpdateIgnoredVersion(version: String?) {
        editSettings { prefs ->
            if (version.isNullOrBlank()) {
                prefs.remove(SettingsKeys.UPDATE_IGNORED_VERSION)
            } else {
                prefs[SettingsKeys.UPDATE_IGNORED_VERSION] = version
            }
        }
    }

    override fun observeAppSettings(): Flow<AppSettings> =
        dataStore.data.map { prefs -> prefs.toAppSettings() }.distinctUntilChanged()

    override suspend fun getAppSettings(): AppSettings =
        dataStore.data.first().toAppSettings()

    override suspend fun saveAppSettings(settings: AppSettings) {
        editSettings { prefs ->
            prefs[SettingsKeys.DARK_THEME_MODE] = settings.darkThemeMode.name
            prefs[SettingsKeys.THEME_COLOR_MODE] = settings.themeColorMode.name
            prefs[SettingsKeys.CUSTOM_SEED_COLOR_ARGB] = settings.customSeedColorArgb
            prefs[SettingsKeys.REFRESH_INTERVAL_MINUTES] = settings.refreshIntervalMinutes
            prefs[SettingsKeys.CHECK_FOR_UPDATES_ON_LAUNCH] = settings.checkForUpdatesOnLaunch
            prefs[SettingsKeys.APP_LANGUAGE] = settings.appLanguage.name
        }
    }

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

    companion object {
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
