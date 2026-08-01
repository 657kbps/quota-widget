package com.kuyermqi.quotawidget.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.kuyermqi.quotawidget.domain.BalanceSnapshot
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
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
                prefs[Keys.WIDGET_STATUS] = Status.NOT_CONFIGURED
                prefs.remove(Keys.WIDGET_ERROR)
                prefs.remove(Keys.WIDGET_FORMATTED)
            } else {
                prefs[Keys.API_KEY_ENCRYPTED] = encrypt(settings.apiKey)
                // Key is present: never leave the widget stuck on "未配置".
                val status = prefs[Keys.WIDGET_STATUS]
                if (status == null || status == Status.NOT_CONFIGURED) {
                    prefs[Keys.WIDGET_STATUS] = Status.LOADING
                }
            }
            prefs[Keys.CURRENCY] = settings.currency.name
        }
    }

    override fun observeWidgetState(): Flow<WidgetDisplayState> =
        dataStore.data.map { prefs -> prefs.toWidgetState() }

    override suspend fun getWidgetState(): WidgetDisplayState =
        dataStore.data.first().toWidgetState()

    override suspend fun saveWidgetSuccess(snapshot: BalanceSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_STATUS] = Status.SUCCESS
            prefs[Keys.WIDGET_PLATFORM_ID] = snapshot.platformId
            prefs[Keys.WIDGET_PLATFORM_NAME] = snapshot.platformName
            prefs[Keys.WIDGET_CURRENCY] = snapshot.currency.name
            prefs[Keys.WIDGET_TOTAL] = snapshot.totalBalance
            prefs[Keys.WIDGET_FORMATTED] = snapshot.formattedBalance
            prefs[Keys.WIDGET_UPDATED_AT] = snapshot.updatedAtEpochMs
            prefs.remove(Keys.WIDGET_ERROR)
        }
    }

    override suspend fun saveWidgetError(message: String) {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_STATUS] = Status.ERROR
            prefs[Keys.WIDGET_ERROR] = message
        }
    }

    override suspend fun saveWidgetLoading() {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_STATUS] = Status.LOADING
            prefs.remove(Keys.WIDGET_ERROR)
        }
    }

    override suspend fun saveWidgetNotConfigured() {
        dataStore.edit { prefs ->
            prefs[Keys.WIDGET_STATUS] = Status.NOT_CONFIGURED
            prefs.remove(Keys.WIDGET_ERROR)
            prefs.remove(Keys.WIDGET_FORMATTED)
        }
    }

    override suspend fun getRefreshIconPhase(): RefreshIconPhase =
        RefreshIconPhase.fromStorage(dataStore.data.first()[Keys.REFRESH_ICON_PHASE])

    override suspend fun setRefreshIconPhase(phase: RefreshIconPhase) {
        dataStore.edit { prefs ->
            prefs[Keys.REFRESH_ICON_PHASE] = phase.name
        }
    }

    override suspend fun getRefreshStartedAtEpochMs(): Long =
        dataStore.data.first()[Keys.REFRESH_STARTED_AT] ?: 0L

    override suspend fun setRefreshStartedAtEpochMs(epochMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.REFRESH_STARTED_AT] = epochMs
        }
    }

    override suspend fun isPlatformTipDismissed(): Boolean =
        dataStore.data.first()[Keys.PLATFORM_TIP_DISMISSED] == true

    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.PLATFORM_TIP_DISMISSED] = dismissed
        }
    }

    private fun Preferences.toDeepSeekSettings(): DeepSeekSettings {
        val encrypted = this[Keys.API_KEY_ENCRYPTED]
        val apiKey = encrypted?.let { runCatching { decrypt(it) }.getOrDefault("") }.orEmpty()
        return DeepSeekSettings(
            apiKey = apiKey,
            currency = CurrencyPreference.fromStorage(this[Keys.CURRENCY]),
        )
    }

    private fun Preferences.toWidgetState(): WidgetDisplayState {
        val hasApiKey = !toDeepSeekSettings().apiKey.isBlank()
        if (!hasApiKey) {
            return WidgetDisplayState.NotConfigured
        }
        return when (this[Keys.WIDGET_STATUS]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.ERROR -> WidgetDisplayState.Error(this[Keys.WIDGET_ERROR] ?: "刷新失败")
            Status.SUCCESS -> {
                val currency = CurrencyPreference.fromStorage(this[Keys.WIDGET_CURRENCY])
                val total = this[Keys.WIDGET_TOTAL] ?: "0"
                WidgetDisplayState.Success(
                    BalanceSnapshot(
                        platformId = this[Keys.WIDGET_PLATFORM_ID] ?: PlatformIds.DEEPSEEK,
                        platformName = this[Keys.WIDGET_PLATFORM_NAME] ?: "DeepSeek",
                        currency = currency,
                        totalBalance = total,
                        formattedBalance = this[Keys.WIDGET_FORMATTED]
                            ?: formatBalance(currency, total),
                        updatedAtEpochMs = this[Keys.WIDGET_UPDATED_AT] ?: 0L,
                    ),
                )
            }
            // API key exists but balance not fetched yet (or stale not_configured).
            else -> WidgetDisplayState.Loading
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = aead.encrypt(plain.encodeToByteArray(), ASSOCIATED_DATA)
        return Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val cipher = Base64.decode(encoded, Base64.NO_WRAP)
        return aead.decrypt(cipher, ASSOCIATED_DATA).decodeToString()
    }

    private object Keys {
        val API_KEY_ENCRYPTED = stringPreferencesKey("deepseek_api_key_enc")
        val CURRENCY = stringPreferencesKey("deepseek_currency")
        val WIDGET_STATUS = stringPreferencesKey("widget_status")
        val WIDGET_ERROR = stringPreferencesKey("widget_error")
        val WIDGET_PLATFORM_ID = stringPreferencesKey("widget_platform_id")
        val WIDGET_PLATFORM_NAME = stringPreferencesKey("widget_platform_name")
        val WIDGET_CURRENCY = stringPreferencesKey("widget_currency")
        val WIDGET_TOTAL = stringPreferencesKey("widget_total")
        val WIDGET_FORMATTED = stringPreferencesKey("widget_formatted")
        val WIDGET_UPDATED_AT = longPreferencesKey("widget_updated_at")
        val REFRESH_ICON_PHASE = stringPreferencesKey("refresh_icon_phase")
        val REFRESH_STARTED_AT = longPreferencesKey("refresh_started_at")
        val PLATFORM_TIP_DISMISSED = booleanPreferencesKey("platform_tip_dismissed")
    }

    private object Status {
        const val NOT_CONFIGURED = "not_configured"
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
    }

    companion object {
        private val ASSOCIATED_DATA = "deepseek_api_key".encodeToByteArray()

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
