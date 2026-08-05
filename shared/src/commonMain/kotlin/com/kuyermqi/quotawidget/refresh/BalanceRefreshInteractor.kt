package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.codex.CodexAuthErrors
import com.kuyermqi.quotawidget.codex.CodexRegionUnavailableException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.provider.QuotaProvider
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.CancellationException

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
        if (settingsRepository.getNewApiSettings().isConfigured) {
            results += refresh(PlatformIds.NEW_API)
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
     * Session expiry ([SessionExpiredException]) writes [WidgetDisplayState.NeedsReauth]
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询额度失败"
            val retryable = !isNonRetryableTransient(e)
            println(
                "QuotaRefresh fetchFailed platform=$platformId " +
                    "retryable=$retryable msg=$message",
            )
            e.printStackTrace()
            val previous = settingsRepository.getWidgetState(platformId)
            if (previous is WidgetDisplayState.Success) {
                BalanceRefreshResult.TransientFailure(previous, retryable = retryable)
            } else {
                settingsRepository.saveWidgetError(platformId, message)
                BalanceRefreshResult.TransientFailure(
                    WidgetDisplayState.Error(message),
                    retryable = retryable,
                )
            }
        }
    }

    private fun isNonRetryableTransient(error: Exception): Boolean {
        if (error is CodexRegionUnavailableException) return true
        val message = error.message.orEmpty()
        return message == CodexAuthErrors.REGION_UNAVAILABLE_MESSAGE ||
            message.startsWith(CodexAuthErrors.REGION_UNAVAILABLE_MESSAGE)
    }
}
