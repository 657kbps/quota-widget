package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.isRetryableHttpStatus
import com.kuyermqi.quotawidget.provider.QuotaProvider
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * Only network-level failures and retryable HTTP statuses are worth another attempt.
 * Everything else (bad credentials, entitlement gaps, parse failures, geo blocks)
 * needs a human, so WorkManager must not keep burning retries on it.
 */
internal fun isRetryableFailure(error: Throwable): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = error
    while (current != null && visited.add(current)) {
        when (current) {
            is HttpStatusException -> return isRetryableHttpStatus(current.statusCode)
            is ResponseException -> return isRetryableHttpStatus(current.response.status.value)
            is IOException -> return true
        }
        current = current.cause
    }
    return false
}

class BalanceRefreshInteractor(
    private val settingsRepository: PlatformSettingsRepository,
    private val providers: List<QuotaProvider>,
) {
    private val providersById = providers.associateBy { it.platform.id }

    /**
     * One flight per platform: concurrent refreshes of the same platform share a single
     * network round trip (token rotation is single-use, so parallel fetches would make one
     * caller report a failure), while different platforms still run independently.
     */
    private val flights = mutableMapOf<String, SingleFlight<BalanceRefreshResult>>()
    private val flightsLock = Mutex()

    /**
     * Refreshes every configured platform. Used by WorkManager and home entry.
     */
    suspend fun refreshAllConfigured(): List<BalanceRefreshResult> {
        val results = mutableListOf<BalanceRefreshResult>()
        for (provider in providers) {
            if (provider.isConfigured(settingsRepository)) {
                results += refresh(provider.platform.id)
            }
        }
        return results
    }

    /**
     * Platform ids that currently have usable credentials, in provider order.
     */
    suspend fun configuredPlatformIds(): List<String> =
        providers.filter { it.isConfigured(settingsRepository) }.map { it.platform.id }

    /**
     * Fetches quota for [platformId] without forcing a loading placeholder,
     * so the widget can keep showing the previous value while the icon spins.
     *
     * Retryable failures ([isRetryableFailure]) keep an existing
     * [WidgetDisplayState.Success] instead of overwriting it with Error (avoids
     * "获取失败" after lock-screen network blips). Permanent failures write
     * [WidgetDisplayState.Error] and stop WorkManager retries.
     *
     * Session expiry ([SessionExpiredException]) writes [WidgetDisplayState.NeedsReauth]
     * and does not retain a prior Success.
     *
     * Concurrent calls for the same platform are coalesced into one fetch.
     */
    suspend fun refresh(platformId: String): BalanceRefreshResult =
        flightFor(platformId).run { refreshInternal(platformId) }

    private suspend fun flightFor(platformId: String): SingleFlight<BalanceRefreshResult> =
        flightsLock.withLock {
            flights.getOrPut(platformId) { SingleFlight() }
        }

    private suspend fun refreshInternal(platformId: String): BalanceRefreshResult {
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
        } catch (e: QuotaUnavailableException) {
            // Expected data condition (no connected account / no usage window):
            // always surface it instead of silently keeping a stale Success, and
            // don't ask WorkManager to retry something only the user can fix.
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询额度失败"
            println("QuotaRefresh unavailable platform=$platformId msg=$message")
            settingsRepository.saveWidgetError(platformId, message)
            BalanceRefreshResult.Completed(WidgetDisplayState.Error(message))
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "查询额度失败"
            val retryable = isRetryableFailure(e)
            println(
                "QuotaRefresh fetchFailed platform=$platformId " +
                    "retryable=$retryable type=${e::class.simpleName} msg=$message",
            )
            e.printStackTrace()
            if (!retryable) {
                settingsRepository.saveWidgetError(platformId, message)
                return BalanceRefreshResult.Completed(WidgetDisplayState.Error(message))
            }
            val previous = settingsRepository.getWidgetState(platformId)
            if (previous is WidgetDisplayState.Success) {
                BalanceRefreshResult.TransientFailure(previous, retryable = true)
            } else {
                settingsRepository.saveWidgetError(platformId, message)
                BalanceRefreshResult.TransientFailure(
                    WidgetDisplayState.Error(message),
                    retryable = true,
                )
            }
        }
    }
}
