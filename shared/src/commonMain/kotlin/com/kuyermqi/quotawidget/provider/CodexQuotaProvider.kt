package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.codex.CodexDebugLog
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
import com.kuyermqi.quotawidget.util.currentTimeMillis
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
        val expiring = CodexOAuth.isAccessTokenExpiringSoon(settings.expiresAtEpochMs)
        CodexDebugLog.i(
            "fetch start expiringSoon=$expiring " +
                "expiresAt=${settings.expiresAtEpochMs} now=${currentTimeMillis()} " +
                "at=${CodexDebugLog.tokenFp(settings.accessToken)} " +
                "rt=${CodexDebugLog.tokenFp(settings.refreshToken)} " +
                "account=${settings.accountId.takeLast(6)}",
        )
        if (expiring) {
            settings = refreshAndPersist(repo, settings, force = false)
        }
        val snapshot = try {
            usageFetcher.fetchUsage(settings.accessToken, settings.accountId)
        } catch (e: SessionExpiredException) {
            CodexDebugLog.w(
                "usage sessionExpired; force refresh msg=${e.message} " +
                    "at=${CodexDebugLog.tokenFp(settings.accessToken)}",
            )
            settings = refreshAndPersist(repo, settings, force = true)
            usageFetcher.fetchUsage(settings.accessToken, settings.accountId)
        }
        CodexDebugLog.i(
            "fetch ok display=${snapshot.primaryDisplay} windows=${snapshot.windows.size}",
        )
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
                CodexDebugLog.w("refreshAndPersist abort: not configured force=$force")
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            val alreadyFresh = !CodexOAuth.isAccessTokenExpiringSoon(latest.expiresAtEpochMs)
            val rotatedByOther =
                latest.accessToken != settings.accessToken ||
                    latest.refreshToken != settings.refreshToken
            CodexDebugLog.i(
                "refreshAndPersist enter force=$force alreadyFresh=$alreadyFresh " +
                    "rotatedByOther=$rotatedByOther " +
                    "callerAt=${CodexDebugLog.tokenFp(settings.accessToken)} " +
                    "latestAt=${CodexDebugLog.tokenFp(latest.accessToken)} " +
                    "latestRt=${CodexDebugLog.tokenFp(latest.refreshToken)} " +
                    "expiresAt=${latest.expiresAtEpochMs}",
            )
            if (alreadyFresh && (!force || rotatedByOther)) {
                CodexDebugLog.i("refreshAndPersist skip refresh")
                return@withLock latest
            }
            val bundle = try {
                tokenRefresher.refresh(latest.refreshToken)
            } catch (e: Exception) {
                CodexDebugLog.w(
                    "refreshAndPersist oauth failed force=$force " +
                        "type=${e::class.simpleName} msg=${e.message}",
                )
                throw e
            }
            // Logout / re-login may change credentials while the network call was in flight.
            val current = repo.getCodexSettings()
            if (!current.isConfigured) {
                CodexDebugLog.w("refreshAndPersist abort: logged out during oauth")
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            if (current.refreshToken != latest.refreshToken ||
                current.accessToken != latest.accessToken
            ) {
                CodexDebugLog.w(
                    "refreshAndPersist discard oauth result; credentials changed " +
                        "currentAt=${CodexDebugLog.tokenFp(current.accessToken)} " +
                        "currentRt=${CodexDebugLog.tokenFp(current.refreshToken)}",
                )
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
            CodexDebugLog.i(
                "refreshAndPersist persisted at=${CodexDebugLog.tokenFp(next.accessToken)} " +
                    "rt=${CodexDebugLog.tokenFp(next.refreshToken)} " +
                    "expiresAt=${next.expiresAtEpochMs}",
            )
            next
        }
    }

    private companion object {
        val refreshMutex = Mutex()
    }
}
