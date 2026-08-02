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
     *
     * On transient failure, keeps an existing [WidgetDisplayState.Success] instead of
     * overwriting it with Error (avoids "获取失败" after lock-screen network blips).
     */
    suspend fun refreshDeepSeek(): BalanceRefreshResult {
        val settings = settingsRepository.getDeepSeekSettings()
        if (settings.apiKey.isBlank()) {
            settingsRepository.saveWidgetNotConfigured()
            return BalanceRefreshResult.Completed(WidgetDisplayState.NotConfigured)
        }

        return try {
            val snapshot = deepSeekClient.fetchBalance(
                apiKey = settings.apiKey,
                preferredCurrency = settings.currency,
            )
            settingsRepository.saveWidgetSuccess(snapshot)
            BalanceRefreshResult.Completed(WidgetDisplayState.Success(snapshot))
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询余额失败"
            val previous = settingsRepository.getWidgetState()
            if (previous is WidgetDisplayState.Success) {
                BalanceRefreshResult.TransientFailure(previous)
            } else {
                settingsRepository.saveWidgetError(message)
                BalanceRefreshResult.TransientFailure(WidgetDisplayState.Error(message))
            }
        }
    }
}
