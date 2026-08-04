package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.provider.QuotaProvider
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class BalanceRefreshInteractor(
    private val settingsRepository: PlatformSettingsRepository,
    private val providers: List<QuotaProvider>,
) {
    private val providersById = providers.associateBy { it.platform.id }

    /**
     * Refreshes every configured platform. Used by WorkManager and home entry.
     */
    suspend fun refreshAllConfigured(): List<BalanceRefreshResult> {
        val results = mutableListOf<BalanceRefreshResult>()
        if (settingsRepository.getDeepSeekSettings().apiKey.isNotBlank()) {
            results += refresh(PlatformIds.DEEPSEEK)
        }
        if (settingsRepository.getOpenCodeGoSettings().isConfigured) {
            results += refresh(PlatformIds.OPENCODE_GO)
        }
        if (settingsRepository.getCodexSettings().isConfigured) {
            results += refresh(PlatformIds.CODEX)
        }
        return results
    }

    /**
     * Fetches quota for [platformId] without forcing a loading placeholder,
     * so the widget can keep showing the previous value while the icon spins.
     *
     * On transient failure, keeps an existing [WidgetDisplayState.Success] instead of
     * overwriting it with Error (avoids "获取失败" after lock-screen network blips).
     *
     * Session expiry ([SessionExpiredException] / 401/403) writes [WidgetDisplayState.NeedsReauth]
     * and does not retain a prior Success.
     */
    suspend fun refresh(platformId: String): BalanceRefreshResult {
        val provider = providersById[platformId]
        if (provider == null) {
            val message = "未知平台: $platformId"
            settingsRepository.saveWidgetError(platformId, message)
            return BalanceRefreshResult.Completed(WidgetDisplayState.Error(message))
        }

        if (!provider.isConfigured(settingsRepository)) {
            settingsRepository.saveWidgetNotConfigured(platformId)
            return BalanceRefreshResult.Completed(WidgetDisplayState.NotConfigured)
        }

        return try {
            val snapshot = provider.fetch(settingsRepository)
            settingsRepository.saveWidgetSuccess(platformId, snapshot)
            BalanceRefreshResult.Completed(WidgetDisplayState.Success(snapshot))
        } catch (e: SessionExpiredException) {
            println("QuotaRefresh sessionExpired platform=$platformId msg=${e.message}")
            settingsRepository.saveWidgetNeedsReauth(platformId)
            BalanceRefreshResult.Completed(WidgetDisplayState.NeedsReauth)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询额度失败"
            println("QuotaRefresh fetchFailed platform=$platformId msg=$message")
            e.printStackTrace()
            val previous = settingsRepository.getWidgetState(platformId)
            if (previous is WidgetDisplayState.Success) {
                BalanceRefreshResult.TransientFailure(previous)
            } else {
                settingsRepository.saveWidgetError(platformId, message)
                BalanceRefreshResult.TransientFailure(WidgetDisplayState.Error(message))
            }
        }
    }
}
