package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.OpenCodeWidgetWindowKind
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import kotlinx.coroutines.flow.Flow

data class DeepSeekSettings(
    val apiKey: String = "",
    val currency: CurrencyPreference = CurrencyPreference.CNY,
)

data class OpenCodeGoSettings(
    val workspaceId: String = "",
    val workspaceName: String = "",
    val authCookie: String = "",
    val widgetWindowKind: OpenCodeWidgetWindowKind = OpenCodeWidgetWindowKind.ROLLING,
) {
    val isConfigured: Boolean
        get() = workspaceId.isNotBlank() && authCookie.isNotBlank()
}

interface PlatformSettingsRepository {
    fun observeDeepSeekSettings(): Flow<DeepSeekSettings>
    suspend fun getDeepSeekSettings(): DeepSeekSettings
    suspend fun saveDeepSeekSettings(settings: DeepSeekSettings)

    fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings>
    suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings
    suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings)
    suspend fun clearOpenCodeGoSettings()

    fun observeWidgetState(platformId: String): Flow<WidgetDisplayState>
    suspend fun getWidgetState(platformId: String): WidgetDisplayState
    suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot)
    suspend fun saveWidgetError(platformId: String, message: String)
    suspend fun saveWidgetLoading(platformId: String)
    suspend fun saveWidgetNotConfigured(platformId: String)
    suspend fun saveWidgetNeedsReauth(platformId: String)

    suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase
    suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase)
    suspend fun getRefreshStartedAtEpochMs(platformId: String): Long
    suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long)
    suspend fun clearAllRefreshIconPhases()

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
