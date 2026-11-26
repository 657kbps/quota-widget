package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.WidgetErrorKind
import com.kuyermqi.quotawidget.domain.isRetryableHttpStatus
import com.kuyermqi.quotawidget.network.InvalidBaseUrlException
import com.kuyermqi.quotawidget.provider.QuotaProvider
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

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

/**
 * Classifies [error] for persistence, walking the cause chain like [isRetryableFailure] so a
 * wrapped network or status failure keeps its meaning. Messages are dropped: the kind is the
 * only thing stored and every surface renders it in the app language.
 */
internal fun Throwable.toWidgetErrorKind(): WidgetErrorKind {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        when (current) {
            is SessionExpiredException -> return WidgetErrorKind.SessionExpired
            is QuotaUnavailableException -> return WidgetErrorKind.QuotaUnavailable
            is InvalidBaseUrlException -> return WidgetErrorKind.InvalidBaseUrl
            is HttpStatusException -> return WidgetErrorKind.HttpStatus(current.statusCode)
            is ResponseException ->
                return WidgetErrorKind.HttpStatus(current.response.status.value)
            is IOException -> return WidgetErrorKind.Network
            is SerializationException -> return WidgetErrorKind.ParseFailure
        }
        current = current.cause
    }
    return WidgetErrorKind.Unknown()
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
     * Platform ids that currently have usable credentials, in provider order. Judged from the
     * stored key material so the refresh schedule gate stays decrypt-free.
     */
    suspend fun configuredPlatformIds(): List<String> =
        settingsRepository.configuredPlatformIds(providers.map { it.platform.id })

    /**
     * Fetches quota for [platformId] without forcing a loading placeholder,
     * so the widget can keep showing the previous value while the icon spins.
     *
     * Retryable failures ([isRetryableFailure]) keep an existing
     * [WidgetDisplayState.Success] instead of overwriting it with Error (avoids a
     * "fetch failed" state after lock-screen network blips). Permanent failures write
     * [WidgetDisplayState.Error] with a [WidgetErrorKind] and stop WorkManager retries.
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
            val kind = WidgetErrorKind.Unknown()
            println("QuotaRefresh unknownPlatform id=$platformId kind=$kind")
            settingsRepository.saveWidgetError(platformId, kind)
            return BalanceRefreshResult.Completed(WidgetDisplayState.Error(kind))
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
            val kind = WidgetErrorKind.QuotaUnavailable
            println("QuotaRefresh unavailable platform=$platformId kind=$kind msg=${e.message}")
            settingsRepository.saveWidgetError(platformId, kind)
            BalanceRefreshResult.Completed(WidgetDisplayState.Error(kind))
        } catch (e: Exception) {
            val kind = e.toWidgetErrorKind()
            val retryable = isRetryableFailure(e)
            println(
                "QuotaRefresh fetchFailed platform=$platformId " +
                    "retryable=$retryable type=${e::class.simpleName} kind=$kind msg=${e.message}",
            )
            e.printStackTrace()
            if (!retryable) {
                settingsRepository.saveWidgetError(platformId, kind)
                return BalanceRefreshResult.Completed(WidgetDisplayState.Error(kind))
            }
            val previous = settingsRepository.getWidgetState(platformId)
            if (previous is WidgetDisplayState.Success) {
                BalanceRefreshResult.TransientFailure(previous, retryable = true)
            } else {
                settingsRepository.saveWidgetError(platformId, kind)
                BalanceRefreshResult.TransientFailure(
                    WidgetDisplayState.Error(kind),
                    retryable = true,
                )
            }
        }
    }
}
