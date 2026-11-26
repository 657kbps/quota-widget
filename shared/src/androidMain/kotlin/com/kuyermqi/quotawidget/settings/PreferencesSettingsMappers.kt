package com.kuyermqi.quotawidget.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.kuyermqi.quotawidget.domain.ALLOWED_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DEFAULT_CUSTOM_SEED_COLOR_ARGB
import com.kuyermqi.quotawidget.domain.DEFAULT_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.WidgetErrorKind
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.magpie.decodeMagpieProviders
import com.kuyermqi.quotawidget.magpie.encodeMagpieProviders
import com.kuyermqi.quotawidget.platform.PlatformIds

/** Cipher hooks of the repository, so key mapping stays free of the Tink keyset. */
internal class SettingsCrypto(
    val encrypt: (plain: String, associatedData: ByteArray) -> String,
    val decrypt: (encoded: String, associatedData: ByteArray) -> String,
)

internal fun Preferences.toAppSettings(): AppSettings {
    val interval = this[SettingsKeys.REFRESH_INTERVAL_MINUTES] ?: DEFAULT_REFRESH_INTERVAL_MINUTES
    return AppSettings(
        darkThemeMode = DarkThemeMode.fromStorage(this[SettingsKeys.DARK_THEME_MODE]),
        themeColorMode = ThemeColorMode.fromStorage(this[SettingsKeys.THEME_COLOR_MODE]),
        customSeedColorArgb = this[SettingsKeys.CUSTOM_SEED_COLOR_ARGB] ?: DEFAULT_CUSTOM_SEED_COLOR_ARGB,
        refreshIntervalMinutes = interval.takeIf { it in ALLOWED_REFRESH_INTERVAL_MINUTES }
            ?: DEFAULT_REFRESH_INTERVAL_MINUTES,
        checkForUpdatesOnLaunch = this[SettingsKeys.CHECK_FOR_UPDATES_ON_LAUNCH] ?: true,
        appLanguage = AppLanguage.fromStorage(this[SettingsKeys.APP_LANGUAGE]),
        showWidgetUpdatedAt = this[SettingsKeys.SHOW_WIDGET_UPDATED_AT] ?: true,
    )
}

internal fun Preferences.toDeepSeekSettings(crypto: SettingsCrypto): DeepSeekSettings {
    val encrypted = this[SettingsKeys.API_KEY_ENCRYPTED]
    val apiKey = encrypted?.let { runCatching { crypto.decrypt(it, API_KEY_AD) }.getOrDefault("") }.orEmpty()
    return DeepSeekSettings(
        apiKey = apiKey,
        currency = CurrencyPreference.fromStorage(this[SettingsKeys.CURRENCY]),
    )
}

internal fun Preferences.toOpenCodeGoSettings(crypto: SettingsCrypto): OpenCodeGoSettings {
    val encrypted = this[SettingsKeys.OPENCODE_AUTH_COOKIE_ENC]
    val authCookie = encrypted
        ?.let { runCatching { crypto.decrypt(it, OPENCODE_AUTH_AD) }.getOrDefault("") }
        .orEmpty()
    val encryptedApiKey = this[SettingsKeys.OPENCODE_API_KEY_ENC]
    val apiKey = encryptedApiKey
        ?.let { runCatching { crypto.decrypt(it, OPENCODE_API_KEY_AD) }.getOrDefault("") }
        .orEmpty()
    val authMethod = this[SettingsKeys.OPENCODE_AUTH_METHOD]?.let { stored ->
        runCatching { OpenCodeAuthMethod.valueOf(stored) }.getOrNull()
    }
    return OpenCodeGoSettings(
        workspaceId = this[SettingsKeys.OPENCODE_WORKSPACE_ID].orEmpty(),
        workspaceName = this[SettingsKeys.OPENCODE_WORKSPACE_NAME].orEmpty(),
        authCookie = authCookie,
        apiKey = apiKey,
        authMethod = authMethod,
        widgetWindowKind = UsageWindowKind.fromStorage(this[SettingsKeys.OPENCODE_WIDGET_WINDOW]),
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.OPENCODE_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.OPENCODE_USAGE_PROGRESS_STYLE],
        ),
        usageResetStyle = UsageResetStyle.fromStorage(
            this[SettingsKeys.OPENCODE_USAGE_RESET_STYLE],
        ),
    )
}

internal fun Preferences.toCodexSettings(crypto: SettingsCrypto): CodexSettings {
    fun decryptOrEmpty(key: Preferences.Key<String>, ad: ByteArray): String =
        this[key]?.let { runCatching { crypto.decrypt(it, ad) }.getOrDefault("") }.orEmpty()
    return CodexSettings(
        accessToken = decryptOrEmpty(SettingsKeys.CODEX_ACCESS_ENC, CODEX_ACCESS_AD),
        refreshToken = decryptOrEmpty(SettingsKeys.CODEX_REFRESH_ENC, CODEX_REFRESH_AD),
        idToken = decryptOrEmpty(SettingsKeys.CODEX_ID_ENC, CODEX_ID_AD),
        accountId = this[SettingsKeys.CODEX_ACCOUNT_ID].orEmpty(),
        expiresAtEpochMs = this[SettingsKeys.CODEX_EXPIRES_AT] ?: 0L,
        email = this[SettingsKeys.CODEX_EMAIL].orEmpty(),
        planType = this[SettingsKeys.CODEX_PLAN_TYPE].orEmpty(),
        widgetWindowKind = this[SettingsKeys.CODEX_WIDGET_WINDOW]
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: UsageWindowKind.WEEKLY,
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.CODEX_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.CODEX_USAGE_PROGRESS_STYLE],
        ),
        usageResetStyle = UsageResetStyle.fromStorage(
            this[SettingsKeys.CODEX_USAGE_RESET_STYLE],
        ),
    )
}

internal fun Preferences.toNewApiSettings(crypto: SettingsCrypto): NewApiSettings {
    val encrypted = this[SettingsKeys.NEW_API_KEY_ENC]
    val apiKey = encrypted
        ?.let { runCatching { crypto.decrypt(it, NEW_API_KEY_AD) }.getOrDefault("") }
        .orEmpty()
    val quotaPerUsd = this[SettingsKeys.NEW_API_QUOTA_PER_USD]
        ?.takeIf { it > 0L }
        ?: DEFAULT_NEW_API_QUOTA_PER_USD
    return NewApiSettings(
        baseUrl = this[SettingsKeys.NEW_API_BASE_URL].orEmpty(),
        apiKey = apiKey,
        quotaPerUsd = quotaPerUsd,
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.NEW_API_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.NEW_API_USAGE_PROGRESS_STYLE],
        ),
    )
}

internal fun Preferences.toMagpieSettings(crypto: SettingsCrypto): MagpieSettings {
    val encrypted = this[SettingsKeys.MAGPIE_KEY_ENC]
    val apiKey = encrypted
        ?.let { runCatching { crypto.decrypt(it, MAGPIE_KEY_AD) }.getOrDefault("") }
        .orEmpty()
    return MagpieSettings(
        baseUrl = this[SettingsKeys.MAGPIE_BASE_URL].orEmpty(),
        apiKey = apiKey,
        selectedProviderId = this[SettingsKeys.MAGPIE_SELECTED_PROVIDER].orEmpty(),
        providers = decodeMagpieProviders(this[SettingsKeys.MAGPIE_PROVIDERS]),
        widgetWindowKind = UsageWindowKind.fromStorage(this[SettingsKeys.MAGPIE_WIDGET_WINDOW]),
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.MAGPIE_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.MAGPIE_USAGE_PROGRESS_STYLE],
        ),
        usageResetStyle = UsageResetStyle.fromStorage(
            this[SettingsKeys.MAGPIE_USAGE_RESET_STYLE],
        ),
    )
}

internal fun Preferences.toKimiCodeSettings(crypto: SettingsCrypto): KimiCodeSettings {
    val apiKey = this[SettingsKeys.KIMI_CODE_API_KEY_ENC]
        ?.let { runCatching { crypto.decrypt(it, KIMI_CODE_KEY_AD) }.getOrDefault("") }
        .orEmpty()
    return KimiCodeSettings(
        apiKey = apiKey,
        region = ServiceRegion.fromStorage(this[SettingsKeys.KIMI_CODE_REGION]),
        widgetWindowKind = UsageWindowKind.fromStorage(this[SettingsKeys.KIMI_CODE_WIDGET_WINDOW]),
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.KIMI_CODE_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.KIMI_CODE_USAGE_PROGRESS_STYLE],
        ),
        usageResetStyle = UsageResetStyle.fromStorage(
            this[SettingsKeys.KIMI_CODE_USAGE_RESET_STYLE],
        ),
    )
}

internal fun Preferences.toZhipuSettings(crypto: SettingsCrypto): ZhipuSettings {
    val apiKey = this[SettingsKeys.ZHIPU_API_KEY_ENC]
        ?.let { runCatching { crypto.decrypt(it, ZHIPU_KEY_AD) }.getOrDefault("") }
        .orEmpty()
    return ZhipuSettings(
        apiKey = apiKey,
        region = ServiceRegion.fromStorage(this[SettingsKeys.ZHIPU_REGION]),
        widgetWindowKind = UsageWindowKind.fromStorage(this[SettingsKeys.ZHIPU_WIDGET_WINDOW]),
        usageDisplayMode = UsageDisplayMode.fromStorage(this[SettingsKeys.ZHIPU_USAGE_DISPLAY]),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            this[SettingsKeys.ZHIPU_USAGE_PROGRESS_STYLE],
        ),
        usageResetStyle = UsageResetStyle.fromStorage(
            this[SettingsKeys.ZHIPU_USAGE_RESET_STYLE],
        ),
    )
}

internal fun Preferences.toSessionSettings(
    platformId: String,
    crypto: SettingsCrypto,
): SessionPlatformSettings {
    fun decryptOrEmpty(key: Preferences.Key<String>, field: String): String =
        this[key]?.let {
            runCatching { crypto.decrypt(it, sessionAd(platformId, field)) }.getOrDefault("")
        }.orEmpty()
    return SessionPlatformSettings(
        accessToken = decryptOrEmpty(sessionAccessKey(platformId), "access"),
        refreshToken = decryptOrEmpty(sessionRefreshKey(platformId), "refresh"),
        apiKey = decryptOrEmpty(sessionApiKey(platformId), "api_key"),
        authMethod = SessionAuthMethod.fromStorage(this[sessionAuthMethodKey(platformId)]),
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

internal fun Preferences.isConfigured(platformId: String): Boolean =
    isPlatformConfiguredWithStoredCredentials(platformId, toStoredCredentialPresence(platformId))

/**
 * Which credential fields are stored for [platformId], read without decrypting them.
 */
internal fun Preferences.toStoredCredentialPresence(platformId: String): StoredCredentialPresence =
    when (platformId) {
        PlatformIds.DEEPSEEK -> StoredCredentialPresence(
            hasApiKey = this[SettingsKeys.API_KEY_ENCRYPTED].isStored(),
        )
        PlatformIds.OPENCODE_GO -> StoredCredentialPresence(
            hasWorkspaceId = !this[SettingsKeys.OPENCODE_WORKSPACE_ID].isNullOrBlank(),
            hasAuthCookie = this[SettingsKeys.OPENCODE_AUTH_COOKIE_ENC].isStored(),
            hasApiKey = this[SettingsKeys.OPENCODE_API_KEY_ENC].isStored(),
            openCodeAuthMethod = this[SettingsKeys.OPENCODE_AUTH_METHOD]?.let { stored ->
                runCatching { OpenCodeAuthMethod.valueOf(stored) }.getOrNull()
            },
        )
        PlatformIds.CODEX -> StoredCredentialPresence(
            hasAccessToken = this[SettingsKeys.CODEX_ACCESS_ENC].isStored(),
            hasRefreshToken = this[SettingsKeys.CODEX_REFRESH_ENC].isStored(),
            hasAccountId = !this[SettingsKeys.CODEX_ACCOUNT_ID].isNullOrBlank(),
        )
        PlatformIds.NEW_API -> StoredCredentialPresence(
            hasBaseUrl = !this[SettingsKeys.NEW_API_BASE_URL].isNullOrBlank(),
            hasApiKey = this[SettingsKeys.NEW_API_KEY_ENC].isStored(),
        )
        PlatformIds.MAGPIE -> StoredCredentialPresence(
            hasBaseUrl = !this[SettingsKeys.MAGPIE_BASE_URL].isNullOrBlank(),
            hasApiKey = this[SettingsKeys.MAGPIE_KEY_ENC].isStored(),
        )
        PlatformIds.KIMI_CODE -> StoredCredentialPresence(
            hasApiKey = this[SettingsKeys.KIMI_CODE_API_KEY_ENC].isStored(),
        )
        PlatformIds.ZHIPU -> StoredCredentialPresence(
            hasApiKey = this[SettingsKeys.ZHIPU_API_KEY_ENC].isStored(),
        )
        in PlatformIds.sessionPlatforms -> StoredCredentialPresence(
            hasAccessToken = this[sessionAccessKey(platformId)].isStored(),
            hasRefreshToken = this[sessionRefreshKey(platformId)].isStored(),
            hasApiKey = this[sessionApiKey(platformId)].isStored(),
        )
        else -> StoredCredentialPresence()
    }

internal fun String?.isStored(): Boolean = !isNullOrBlank()

internal fun Preferences.toWidgetState(platformId: String): WidgetDisplayState =
    toStoredWidgetRecord(platformId).toWidgetDisplayState()

internal fun Preferences.toStoredWidgetRecord(platformId: String): StoredWidgetRecord =
    StoredWidgetRecord(
        platformId = platformId,
        configured = isConfigured(platformId),
        status = this[widgetStatusKey(platformId)]
            ?: if (platformId == PlatformIds.DEEPSEEK) {
                this[SettingsKeys.LEGACY_WIDGET_STATUS]
            } else {
                null
            },
        error = this[widgetErrorKey(platformId)] ?: this[SettingsKeys.LEGACY_WIDGET_ERROR],
        payloadPlatformId = this[widgetPlatformIdKey(platformId)]
            ?: this[SettingsKeys.LEGACY_WIDGET_PLATFORM_ID],
        payloadPlatformName = this[widgetPlatformNameKey(platformId)]
            ?: this[SettingsKeys.LEGACY_WIDGET_PLATFORM_NAME],
        currency = this[widgetCurrencyKey(platformId)] ?: this[SettingsKeys.LEGACY_WIDGET_CURRENCY],
        total = this[widgetTotalKey(platformId)] ?: this[SettingsKeys.LEGACY_WIDGET_TOTAL],
        formatted = this[widgetFormattedKey(platformId)] ?: this[SettingsKeys.LEGACY_WIDGET_FORMATTED],
        windows = this[widgetWindowsKey(platformId)] ?: this[SettingsKeys.LEGACY_WIDGET_WINDOWS],
        updatedAtEpochMs = this[widgetUpdatedAtKey(platformId)]
            ?: this[SettingsKeys.LEGACY_WIDGET_UPDATED_AT],
        unlimitedQuota = this[widgetUnlimitedKey(platformId)] == true,
        usedDisplay = this[widgetUsedDisplayKey(platformId)].orEmpty(),
        emptyLimitedQuota = this[widgetEmptyLimitedQuotaKey(platformId)] == true,
        tokenExpired = this[widgetTokenExpiredKey(platformId)] == true,
        quotaOverspent = this[widgetQuotaOverspentKey(platformId)] == true,
    )

internal fun Preferences.toRefreshIconPhase(platformId: String): RefreshIconPhase =
    RefreshIconPhase.fromStorage(
        this[refreshPhaseKey(platformId)] ?: this[SettingsKeys.LEGACY_REFRESH_ICON_PHASE],
    )

internal fun Preferences.toWidgetCardSettings(platformId: String): WidgetCardSettings? {
    if (UsageDisplayPrefFields.supportedBy(platformId).isEmpty()) return null
    return toUsageDisplayPrefs(platformId).toWidgetCardSettings(platformId)
}

internal fun Preferences.needsLegacyWidgetMigration(): Boolean =
    this[widgetStatusKey(PlatformIds.DEEPSEEK)] == null &&
        this[SettingsKeys.LEGACY_WIDGET_STATUS] != null

internal fun MutablePreferences.migrateLegacyWidgetIfNeeded() {
    if (this[widgetStatusKey(PlatformIds.DEEPSEEK)] != null) return
    val legacyStatus = this[SettingsKeys.LEGACY_WIDGET_STATUS] ?: return
    this[widgetStatusKey(PlatformIds.DEEPSEEK)] = legacyStatus
    this[SettingsKeys.LEGACY_WIDGET_ERROR]?.let { this[widgetErrorKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_PLATFORM_ID]?.let { this[widgetPlatformIdKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_PLATFORM_NAME]?.let { this[widgetPlatformNameKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_CURRENCY]?.let { this[widgetCurrencyKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_TOTAL]?.let { this[widgetTotalKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_FORMATTED]?.let { this[widgetFormattedKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_WINDOWS]?.let { this[widgetWindowsKey(PlatformIds.DEEPSEEK)] = it }
    this[SettingsKeys.LEGACY_WIDGET_UPDATED_AT]?.let { this[widgetUpdatedAtKey(PlatformIds.DEEPSEEK)] = it }
    remove(SettingsKeys.LEGACY_WIDGET_STATUS)
    remove(SettingsKeys.LEGACY_WIDGET_ERROR)
    remove(SettingsKeys.LEGACY_WIDGET_PLATFORM_ID)
    remove(SettingsKeys.LEGACY_WIDGET_PLATFORM_NAME)
    remove(SettingsKeys.LEGACY_WIDGET_CURRENCY)
    remove(SettingsKeys.LEGACY_WIDGET_TOTAL)
    remove(SettingsKeys.LEGACY_WIDGET_FORMATTED)
    remove(SettingsKeys.LEGACY_WIDGET_WINDOWS)
    remove(SettingsKeys.LEGACY_WIDGET_UPDATED_AT)
    remove(SettingsKeys.ACTIVE_PLATFORM_ID)
}

internal fun MutablePreferences.clearWidgetPayload(platformId: String) {
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

internal fun putEncrypted(
    prefs: MutablePreferences,
    key: Preferences.Key<String>,
    value: String,
    platformId: String,
    field: String,
    crypto: SettingsCrypto,
) {
    if (value.isBlank()) {
        prefs.remove(key)
    } else {
        prefs[key] = crypto.encrypt(value, sessionAd(platformId, field))
    }
}

internal fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String) {
    if (value.isBlank()) remove(key) else this[key] = value
}

internal fun sessionAd(platformId: String, field: String): ByteArray =
    "session_${platformId}_$field".encodeToByteArray()

internal fun MutablePreferences.applyCodexSettings(
    settings: CodexSettings,
    crypto: SettingsCrypto,
) {
    if (!settings.isConfigured) {
        remove(SettingsKeys.CODEX_ACCESS_ENC)
        remove(SettingsKeys.CODEX_REFRESH_ENC)
        remove(SettingsKeys.CODEX_ID_ENC)
        remove(SettingsKeys.CODEX_ACCOUNT_ID)
        remove(SettingsKeys.CODEX_EXPIRES_AT)
        remove(SettingsKeys.CODEX_EMAIL)
        remove(SettingsKeys.CODEX_PLAN_TYPE)
        clearWidgetPayload(PlatformIds.CODEX)
        this[widgetStatusKey(PlatformIds.CODEX)] = StoredWidgetStatus.NOT_CONFIGURED
    } else {
        this[SettingsKeys.CODEX_ACCESS_ENC] = crypto.encrypt(settings.accessToken, CODEX_ACCESS_AD)
        this[SettingsKeys.CODEX_REFRESH_ENC] = crypto.encrypt(settings.refreshToken, CODEX_REFRESH_AD)
        if (settings.idToken.isBlank()) {
            remove(SettingsKeys.CODEX_ID_ENC)
        } else {
            this[SettingsKeys.CODEX_ID_ENC] = crypto.encrypt(settings.idToken, CODEX_ID_AD)
        }
        this[SettingsKeys.CODEX_ACCOUNT_ID] = settings.accountId
        this[SettingsKeys.CODEX_EXPIRES_AT] = settings.expiresAtEpochMs
        if (settings.email.isBlank()) {
            remove(SettingsKeys.CODEX_EMAIL)
        } else {
            this[SettingsKeys.CODEX_EMAIL] = settings.email
        }
        if (settings.planType.isBlank()) {
            remove(SettingsKeys.CODEX_PLAN_TYPE)
        } else {
            this[SettingsKeys.CODEX_PLAN_TYPE] = settings.planType
        }
        val status = this[widgetStatusKey(PlatformIds.CODEX)]
        if (status == null ||
            status == StoredWidgetStatus.NOT_CONFIGURED ||
            status == StoredWidgetStatus.NEEDS_REAUTH
        ) {
            this[widgetStatusKey(PlatformIds.CODEX)] = StoredWidgetStatus.LOADING
        }
    }
    applyUsageDisplayPrefs(PlatformIds.CODEX, settings.toUsageDisplayPrefs())
}

internal fun MutablePreferences.applyMagpieSettings(
    settings: MagpieSettings,
    crypto: SettingsCrypto,
) {
    if (!settings.isConfigured) {
        remove(SettingsKeys.MAGPIE_BASE_URL)
        remove(SettingsKeys.MAGPIE_KEY_ENC)
        remove(SettingsKeys.MAGPIE_PROVIDERS)
        remove(SettingsKeys.MAGPIE_SELECTED_PROVIDER)
        clearWidgetPayload(PlatformIds.MAGPIE)
        this[widgetStatusKey(PlatformIds.MAGPIE)] = StoredWidgetStatus.NOT_CONFIGURED
    } else {
        this[SettingsKeys.MAGPIE_BASE_URL] = settings.baseUrl.trim().trimEnd('/')
        this[SettingsKeys.MAGPIE_KEY_ENC] = crypto.encrypt(settings.apiKey, MAGPIE_KEY_AD)
        this[SettingsKeys.MAGPIE_PROVIDERS] = encodeMagpieProviders(settings.providers)
        if (settings.selectedProviderId.isBlank()) {
            remove(SettingsKeys.MAGPIE_SELECTED_PROVIDER)
        } else {
            this[SettingsKeys.MAGPIE_SELECTED_PROVIDER] = settings.selectedProviderId
        }
        val status = this[widgetStatusKey(PlatformIds.MAGPIE)]
        if (status == null ||
            status == StoredWidgetStatus.NOT_CONFIGURED ||
            status == StoredWidgetStatus.NEEDS_REAUTH
        ) {
            this[widgetStatusKey(PlatformIds.MAGPIE)] = StoredWidgetStatus.LOADING
        }
    }
    applyUsageDisplayPrefs(PlatformIds.MAGPIE, settings.toUsageDisplayPrefs())
}

internal fun MutablePreferences.applySessionSettings(
    platformId: String,
    previous: SessionPlatformSettings,
    settings: SessionPlatformSettings,
    crypto: SettingsCrypto,
) {
    val credentialsChanged = previous.accessToken != settings.accessToken ||
        previous.refreshToken != settings.refreshToken ||
        previous.apiKey != settings.apiKey ||
        previous.expiresAtEpochMs != settings.expiresAtEpochMs
    if (!settings.isConfiguredFor(platformId)) {
        remove(sessionAccessKey(platformId))
        remove(sessionRefreshKey(platformId))
        remove(sessionApiKey(platformId))
        remove(sessionAuthMethodKey(platformId))
        remove(sessionExpiresAtKey(platformId))
        remove(sessionAccountIdKey(platformId))
        remove(sessionEmailKey(platformId))
        remove(sessionPlanKey(platformId))
        remove(sessionProjectIdKey(platformId))
        remove(sessionDomainKey(platformId))
        remove(sessionUidKey(platformId))
        remove(sessionEnterpriseIdKey(platformId))
        clearWidgetPayload(platformId)
        this[widgetStatusKey(platformId)] = StoredWidgetStatus.NOT_CONFIGURED
    } else {
        putEncrypted(this, sessionAccessKey(platformId), settings.accessToken, platformId, "access", crypto)
        putEncrypted(this, sessionRefreshKey(platformId), settings.refreshToken, platformId, "refresh", crypto)
        putEncrypted(this, sessionApiKey(platformId), settings.apiKey, platformId, "api_key", crypto)
        putOrRemove(sessionAuthMethodKey(platformId), settings.authMethod?.name.orEmpty())
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
            status == StoredWidgetStatus.NOT_CONFIGURED ||
            status == StoredWidgetStatus.NEEDS_REAUTH
        ) {
            this[widgetStatusKey(platformId)] = StoredWidgetStatus.LOADING
        }
    }
    applyUsageDisplayPrefs(platformId, settings.toUsageDisplayPrefs())
}

internal fun MutablePreferences.applyWidgetSnapshot(platformId: String, snapshot: QuotaSnapshot) {
    migrateLegacyWidgetIfNeeded()
    this[widgetStatusKey(platformId)] = StoredWidgetStatus.SUCCESS
    this[widgetPlatformIdKey(platformId)] = snapshot.platformId
    this[widgetPlatformNameKey(platformId)] = snapshot.platformName
    this[widgetCurrencyKey(platformId)] = snapshot.currency.name
    this[widgetTotalKey(platformId)] = snapshot.totalBalance
    this[widgetFormattedKey(platformId)] = snapshot.primaryDisplay
    this[widgetUpdatedAtKey(platformId)] = snapshot.updatedAtEpochMs
    this[widgetWindowsKey(platformId)] = encodeQuotaWindows(snapshot.windows)
    this[widgetUnlimitedKey(platformId)] = snapshot.unlimitedQuota
    this[widgetEmptyLimitedQuotaKey(platformId)] = snapshot.emptyLimitedQuota
    this[widgetTokenExpiredKey(platformId)] = snapshot.tokenExpired
    this[widgetQuotaOverspentKey(platformId)] = snapshot.quotaOverspent
    if (snapshot.usedDisplay.isBlank()) {
        remove(widgetUsedDisplayKey(platformId))
    } else {
        this[widgetUsedDisplayKey(platformId)] = snapshot.usedDisplay
    }
    remove(widgetErrorKey(platformId))
}

internal fun MutablePreferences.applyWidgetError(platformId: String, kind: WidgetErrorKind) {
    migrateLegacyWidgetIfNeeded()
    this[widgetStatusKey(platformId)] = StoredWidgetStatus.ERROR
    this[widgetErrorKey(platformId)] = kind.toStorage()
}

internal fun MutablePreferences.applyWidgetLoading(platformId: String) {
    migrateLegacyWidgetIfNeeded()
    this[widgetStatusKey(platformId)] = StoredWidgetStatus.LOADING
    remove(widgetErrorKey(platformId))
}

internal fun MutablePreferences.applyWidgetNotConfigured(platformId: String) {
    migrateLegacyWidgetIfNeeded()
    this[widgetStatusKey(platformId)] = StoredWidgetStatus.NOT_CONFIGURED
    clearWidgetPayload(platformId)
}

internal fun MutablePreferences.applyWidgetNeedsReauth(platformId: String) {
    migrateLegacyWidgetIfNeeded()
    this[widgetStatusKey(platformId)] = StoredWidgetStatus.NEEDS_REAUTH
    remove(widgetErrorKey(platformId))
}
