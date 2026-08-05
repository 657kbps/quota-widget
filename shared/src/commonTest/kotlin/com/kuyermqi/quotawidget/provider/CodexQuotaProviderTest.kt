package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.codex.CodexRegionUnavailableException
import com.kuyermqi.quotawidget.codex.CodexTokenBundle
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.FakePlatformSettingsRepository
import com.kuyermqi.quotawidget.util.currentTimeMillis
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexQuotaProviderTest {
    @Test
    fun refreshNetworkError_isNotSessionExpired_transientViaInteractor() = runTest {
        val previous = sampleSnapshot()
        val repo = FakePlatformSettingsRepository(
            codex = expiredCodexSettings(),
            widgetStates = mapOf(PlatformIds.CODEX to WidgetDisplayState.Success(previous)),
        )
        var refreshCalls = 0
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                refreshCalls++
                throw IllegalStateException("OAuth token refresh failed: 503")
            },
            usageFetcher = CodexUsageFetcher { _, _ -> error("usage should not run") },
        )
        val interactor = BalanceRefreshInteractor(repo, listOf(provider))

        val result = interactor.refresh(PlatformIds.CODEX)

        assertEquals(1, refreshCalls)
        assertIs<BalanceRefreshResult.TransientFailure>(result)
        assertIs<WidgetDisplayState.Success>(result.retained)
        assertIs<WidgetDisplayState.Success>(repo.getWidgetState(PlatformIds.CODEX))
    }

    @Test
    fun refreshSessionExpired_writesNeedsReauth() = runTest {
        val repo = FakePlatformSettingsRepository(
            codex = expiredCodexSettings(),
        )
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            },
            usageFetcher = CodexUsageFetcher { _, _ -> error("usage should not run") },
        )
        val interactor = BalanceRefreshInteractor(repo, listOf(provider))

        val result = interactor.refresh(PlatformIds.CODEX)

        assertIs<BalanceRefreshResult.Completed>(result)
        assertEquals(WidgetDisplayState.NeedsReauth, result.state)
        assertEquals(WidgetDisplayState.NeedsReauth, repo.getWidgetState(PlatformIds.CODEX))
    }

    @Test
    fun regionRestricted_usage_isTransient_keepsSuccessAndCredentials() = runTest {
        val previous = sampleSnapshot()
        val settings = CodexSettings(
            accessToken = "access-fresh",
            refreshToken = "refresh-keep",
            accountId = "acc-1",
            expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
        )
        val repo = FakePlatformSettingsRepository(
            codex = settings,
            widgetStates = mapOf(PlatformIds.CODEX to WidgetDisplayState.Success(previous)),
        )
        var refreshCalls = 0
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                refreshCalls++
                error("oauth should not run for region block")
            },
            usageFetcher = CodexUsageFetcher { _, _ ->
                throw CodexRegionUnavailableException()
            },
        )
        val interactor = BalanceRefreshInteractor(repo, listOf(provider))

        val result = interactor.refresh(PlatformIds.CODEX)

        assertEquals(0, refreshCalls)
        val failure = assertIs<BalanceRefreshResult.TransientFailure>(result)
        assertEquals(false, failure.retryable)
        assertIs<WidgetDisplayState.Success>(failure.retained)
        assertIs<WidgetDisplayState.Success>(repo.getWidgetState(PlatformIds.CODEX))
        assertEquals("access-fresh", repo.getCodexSettings().accessToken)
        assertEquals("refresh-keep", repo.getCodexSettings().refreshToken)
        assertTrue(repo.getCodexSettings().isConfigured)
    }

    @Test
    fun regionRestricted_oauthRefresh_isTransient_keepsCredentials() = runTest {
        val previous = sampleSnapshot()
        val repo = FakePlatformSettingsRepository(
            codex = expiredCodexSettings(),
            widgetStates = mapOf(PlatformIds.CODEX to WidgetDisplayState.Success(previous)),
        )
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                throw CodexRegionUnavailableException()
            },
            usageFetcher = CodexUsageFetcher { _, _ -> error("usage should not run") },
        )
        val interactor = BalanceRefreshInteractor(repo, listOf(provider))

        val result = interactor.refresh(PlatformIds.CODEX)

        val failure = assertIs<BalanceRefreshResult.TransientFailure>(result)
        assertEquals(false, failure.retryable)
        assertIs<WidgetDisplayState.Success>(failure.retained)
        assertTrue(repo.getCodexSettings().isConfigured)
        assertEquals("access-old", repo.getCodexSettings().accessToken)
        assertEquals("refresh-old", repo.getCodexSettings().refreshToken)
        assertEquals(
            WidgetDisplayState.Success(previous),
            repo.getWidgetState(PlatformIds.CODEX),
        )
    }

    @Test
    fun refreshSkipped_whenTokenAlreadyFresh() = runTest {
        val repo = FakePlatformSettingsRepository(codex = expiredCodexSettings())
        var refreshCalls = 0
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher { token ->
                refreshCalls++
                assertEquals("refresh-old", token)
                freshBundle()
            },
            usageFetcher = CodexUsageFetcher { access, account ->
                assertEquals("access-new", access)
                assertEquals("acc-1", account)
                sampleSnapshot()
            },
        )

        provider.fetch(repo)
        assertEquals(1, refreshCalls)
        assertEquals("access-new", repo.getCodexSettings().accessToken)

        provider.fetch(repo)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun forceRefresh_afterUsageSessionExpired_evenIfLocalExpiryFresh() = runTest {
        val repo = FakePlatformSettingsRepository(
            codex = CodexSettings(
                accessToken = "access-stale",
                refreshToken = "refresh-old",
                accountId = "acc-1",
                expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
            ),
        )
        var refreshCalls = 0
        var usageCalls = 0
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                refreshCalls++
                freshBundle()
            },
            usageFetcher = CodexUsageFetcher { access, _ ->
                usageCalls++
                if (access == "access-stale") {
                    throw SessionExpiredException("Codex 登录已失效，请重新登录")
                }
                sampleSnapshot()
            },
        )

        val snapshot = provider.fetch(repo)

        assertEquals(1, refreshCalls)
        assertEquals(2, usageCalls)
        assertEquals("42%", snapshot.primaryDisplay)
        assertEquals("access-new", repo.getCodexSettings().accessToken)
    }

    @Test
    fun fetch_propagatesRefreshIllegalState() = runTest {
        val repo = FakePlatformSettingsRepository(codex = expiredCodexSettings())
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                throw IllegalStateException("OAuth token refresh failed: 500")
            },
            usageFetcher = CodexUsageFetcher { _, _ -> error("usage should not run") },
        )

        val error = assertFailsWith<IllegalStateException> {
            provider.fetch(repo)
        }
        assertTrue(error.message.orEmpty().contains("OAuth token refresh failed"))
    }

    @Test
    fun refresh_doesNotPersist_whenLoggedOutDuringRefresh() = runTest {
        val repo = FakePlatformSettingsRepository(codex = expiredCodexSettings())
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                repo.clearCodexSettings()
                freshBundle()
            },
            usageFetcher = CodexUsageFetcher { _, _ -> error("usage should not run") },
        )

        assertFailsWith<SessionExpiredException> {
            provider.fetch(repo)
        }
        assertTrue(!repo.getCodexSettings().isConfigured)
        assertEquals("", repo.getCodexSettings().accessToken)
        assertEquals("", repo.getCodexSettings().refreshToken)
    }

    @Test
    fun refresh_doesNotOverwrite_whenReLoginDuringRefresh() = runTest {
        val repo = FakePlatformSettingsRepository(codex = expiredCodexSettings())
        val reLogin = CodexSettings(
            accessToken = "access-relogin",
            refreshToken = "refresh-relogin",
            accountId = "acc-2",
            expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
            email = "new@example.com",
        )
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                repo.saveCodexSettings(reLogin)
                freshBundle()
            },
            usageFetcher = CodexUsageFetcher { access, account ->
                assertEquals("access-relogin", access)
                assertEquals("acc-2", account)
                sampleSnapshot()
            },
        )

        provider.fetch(repo)

        val saved = repo.getCodexSettings()
        assertEquals("access-relogin", saved.accessToken)
        assertEquals("refresh-relogin", saved.refreshToken)
        assertEquals("acc-2", saved.accountId)
    }

    @Test
    fun forceRefresh_skipsWhenAnotherCallerAlreadyRotated() = runTest {
        val repo = FakePlatformSettingsRepository(
            codex = CodexSettings(
                accessToken = "access-stale",
                refreshToken = "refresh-old",
                accountId = "acc-1",
                expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
            ),
        )
        var refreshCalls = 0
        val provider = CodexQuotaProvider(
            tokenRefresher = CodexTokenRefresher {
                refreshCalls++
                error("refresh should be skipped after concurrent rotation")
            },
            usageFetcher = CodexUsageFetcher { access, _ ->
                if (access == "access-stale") {
                    // Another holder already persisted a fresh pair.
                    repo.saveCodexSettings(
                        CodexSettings(
                            accessToken = "access-new",
                            refreshToken = "refresh-new",
                            accountId = "acc-1",
                            expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
                        ),
                    )
                    throw SessionExpiredException("Codex 登录已失效，请重新登录")
                }
                assertEquals("access-new", access)
                sampleSnapshot()
            },
        )

        val snapshot = provider.fetch(repo)

        assertEquals(0, refreshCalls)
        assertEquals("42%", snapshot.primaryDisplay)
        assertEquals("access-new", repo.getCodexSettings().accessToken)
    }

    private fun expiredCodexSettings() = CodexSettings(
        accessToken = "access-old",
        refreshToken = "refresh-old",
        accountId = "acc-1",
        expiresAtEpochMs = 1L,
    )

    private fun freshBundle() = CodexTokenBundle(
        accessToken = "access-new",
        refreshToken = "refresh-new",
        idToken = "",
        expiresAtEpochMs = currentTimeMillis() + 60 * 60 * 1000L,
        accountId = "acc-1",
        email = "",
        planType = "",
    )

    private fun sampleSnapshot() = QuotaSnapshot(
        platformId = PlatformIds.CODEX,
        platformName = PlatformRegistry.displayName(PlatformIds.CODEX),
        windows = listOf(
            QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 42.0),
        ),
        primaryDisplay = "42%",
        updatedAtEpochMs = currentTimeMillis(),
    )
}
