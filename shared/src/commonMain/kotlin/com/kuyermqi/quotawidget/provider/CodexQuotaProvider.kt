package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.codex.CodexOAuth
import com.kuyermqi.quotawidget.codex.CodexTokenBundle
import com.kuyermqi.quotawidget.codex.CodexUsageClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.clampCodexWidgetWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface CodexTokenRefresher {
    suspend fun refresh(refreshToken: String): CodexTokenBundle
}

internal fun interface CodexUsageFetcher {
    suspend fun fetchUsage(accessToken: String, accountId: String): QuotaSnapshot
}

class CodexQuotaProvider internal constructor(
    private val tokenRefresher: CodexTokenRefresher,
    private val usageFetcher: CodexUsageFetcher,
) : QuotaProvider {
    constructor(
        oauth: CodexOAuth = CodexOAuth(),
        usageClient: CodexUsageClient = CodexUsageClient(),
    ) : this(
        tokenRefresher = CodexTokenRefresher { token -> oauth.refresh(token) },
        usageFetcher = CodexUsageFetcher { access, account ->
            usageClient.fetchUsage(access, account)
        },
    )

    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.CODEX)
            ?: error("Codex platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getCodexSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        var settings = repo.getCodexSettings()
        require(settings.isConfigured) { "Codex 未登录" }
        if (CodexOAuth.isAccessTokenExpiringSoon(settings.expiresAtEpochMs)) {
            settings = refreshAndPersist(repo, settings, force = false)
        }
        val snapshot = try {
            usageFetcher.fetchUsage(settings.accessToken, settings.accountId)
        } catch (e: SessionExpiredException) {
            settings = refreshAndPersist(repo, settings, force = true)
            usageFetcher.fetchUsage(settings.accessToken, settings.accountId)
        }
        persistClampedWindowKind(repo, settings, snapshot.windows)
        return snapshot
    }

    private suspend fun persistClampedWindowKind(
        repo: PlatformSettingsRepository,
        settings: CodexSettings,
        windows: List<QuotaWindow>,
    ) {
        val nextKind = clampCodexWidgetWindowKind(settings.widgetWindowKind, windows)
        if (nextKind != settings.widgetWindowKind) {
            repo.saveCodexSettings(settings.copy(widgetWindowKind = nextKind))
        }
    }

    private suspend fun refreshAndPersist(
        repo: PlatformSettingsRepository,
        settings: CodexSettings,
        force: Boolean,
    ): CodexSettings {
        return refreshMutex.withLock {
            val latest = repo.getCodexSettings()
            if (!latest.isConfigured) {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            val alreadyFresh = !CodexOAuth.isAccessTokenExpiringSoon(latest.expiresAtEpochMs)
            val rotatedByOther =
                latest.accessToken != settings.accessToken ||
                    latest.refreshToken != settings.refreshToken
            if (alreadyFresh && (!force || rotatedByOther)) {
                return@withLock latest
            }
            val bundle = tokenRefresher.refresh(latest.refreshToken)
            // Logout / re-login may change credentials while the network call was in flight.
            val current = repo.getCodexSettings()
            if (!current.isConfigured) {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            if (current.refreshToken != latest.refreshToken ||
                current.accessToken != latest.accessToken
            ) {
                return@withLock current
            }
            val next = current.copy(
                accessToken = bundle.accessToken,
                refreshToken = bundle.refreshToken,
                idToken = bundle.idToken.ifBlank { current.idToken },
                accountId = bundle.accountId.ifBlank { current.accountId },
                expiresAtEpochMs = bundle.expiresAtEpochMs,
                email = bundle.email.ifBlank { current.email },
                planType = bundle.planType.ifBlank { current.planType },
            )
            repo.saveCodexSettings(next)
            next
        }
    }

    private companion object {
        val refreshMutex = Mutex()
    }
}
