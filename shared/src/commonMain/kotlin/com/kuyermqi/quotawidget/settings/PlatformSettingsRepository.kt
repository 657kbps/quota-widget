package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.BalanceSnapshot
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import kotlinx.coroutines.flow.Flow

data class DeepSeekSettings(
    val apiKey: String = "",
    val currency: CurrencyPreference = CurrencyPreference.CNY,
)

interface PlatformSettingsRepository {
    fun observeDeepSeekSettings(): Flow<DeepSeekSettings>
    suspend fun getDeepSeekSettings(): DeepSeekSettings
    suspend fun saveDeepSeekSettings(settings: DeepSeekSettings)

    fun observeWidgetState(): Flow<WidgetDisplayState>
    suspend fun getWidgetState(): WidgetDisplayState
    suspend fun saveWidgetSuccess(snapshot: BalanceSnapshot)
    suspend fun saveWidgetError(message: String)
    suspend fun saveWidgetLoading()
    suspend fun saveWidgetNotConfigured()

    suspend fun getRefreshIconPhase(): RefreshIconPhase
    suspend fun setRefreshIconPhase(phase: RefreshIconPhase)
    suspend fun getRefreshStartedAtEpochMs(): Long
    suspend fun setRefreshStartedAtEpochMs(epochMs: Long)

    suspend fun isPlatformTipDismissed(): Boolean
    suspend fun setPlatformTipDismissed(dismissed: Boolean)

    suspend fun isOemBackgroundTipDismissed(): Boolean
    suspend fun setOemBackgroundTipDismissed(dismissed: Boolean)

    suspend fun getUpdateIgnoredVersion(): String?
    suspend fun setUpdateIgnoredVersion(version: String?)

    fun observeAppSettings(): Flow<AppSettings>
    suspend fun getAppSettings(): AppSettings
    suspend fun saveAppSettings(settings: AppSettings)
}
