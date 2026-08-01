package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class BalanceRefreshInteractor(
    private val settingsRepository: PlatformSettingsRepository,
    private val deepSeekClient: DeepSeekBalanceClient = DeepSeekBalanceClient(),
) {
    /**
     * Fetches the latest balance without forcing a loading placeholder,
     * so the widget can keep showing the previous value while the icon spins.
     */
    suspend fun refreshDeepSeek(): WidgetDisplayState {
        val settings = settingsRepository.getDeepSeekSettings()
        if (settings.apiKey.isBlank()) {
            settingsRepository.saveWidgetNotConfigured()
            return WidgetDisplayState.NotConfigured
        }

        return try {
            val snapshot = deepSeekClient.fetchBalance(
                apiKey = settings.apiKey,
                preferredCurrency = settings.currency,
            )
            settingsRepository.saveWidgetSuccess(snapshot)
            WidgetDisplayState.Success(snapshot)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询余额失败"
            settingsRepository.saveWidgetError(message)
            WidgetDisplayState.Error(message)
        }
    }
}
