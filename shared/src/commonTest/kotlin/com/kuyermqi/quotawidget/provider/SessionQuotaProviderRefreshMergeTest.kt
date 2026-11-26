package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.FakePlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A refresh holds the settings snapshot it started with. Whatever the user changes while the
 * network call is in flight must survive the token write that lands afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionQuotaProviderRefreshMergeTest {
    @Test
    fun refreshDuringPreferenceChange_keepsUserPreferences() = runTest {
        val platformId = PlatformIds.WORKBUDDY
        val repo = FakePlatformSettingsRepository(
            sessionSettings = mapOf(
                platformId to configuredSession(
                    usageDisplayMode = UsageDisplayMode.USED,
                    usageProgressStyle = UsageProgressStyle.BAR,
                    widgetWindowKind = UsageWindowKind.MONTHLY,
                ),
            ),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = SessionQuotaProvider(
            platformId,
            object : SessionQuotaClient {
                override fun isConfigured(settings: SessionPlatformSettings): Boolean =
                    settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()

                override suspend fun fetch(
                    settings: SessionPlatformSettings,
                    save: suspend (SessionPlatformSettings) -> Unit,
                ): QuotaSnapshot {
                    started.complete(Unit)
                    release.await()
                    save(
                        settings.copy(
                            accessToken = "access-new",
                            refreshToken = "refresh-new",
                            expiresAtEpochMs = 99L,
                            plan = "Pro",
                        ),
                    )
                    return creditSnapshot(platformId)
                }
            },
        )

        val job = async { provider.fetch(repo) }
        started.await()
        val inFlight = repo.getSessionSettings(platformId)
        repo.saveSessionSettings(
            platformId,
            inFlight.copy(
                usageDisplayMode = UsageDisplayMode.REMAINING,
                usageProgressStyle = UsageProgressStyle.CAPSULE,
                usageResetStyle = UsageResetStyle.PILL,
                widgetWindowKind = UsageWindowKind.ROLLING,
            ),
        )
        release.complete(Unit)
        job.await()

        val stored = repo.getSessionSettings(platformId)
        assertEquals("access-new", stored.accessToken)
        assertEquals("refresh-new", stored.refreshToken)
        assertEquals(99L, stored.expiresAtEpochMs)
        assertEquals("Pro", stored.plan)
        assertEquals(UsageDisplayMode.REMAINING, stored.usageDisplayMode)
        assertEquals(UsageProgressStyle.CAPSULE, stored.usageProgressStyle)
        assertEquals(UsageResetStyle.PILL, stored.usageResetStyle)
        assertEquals(UsageWindowKind.ROLLING, stored.widgetWindowKind)
    }

    @Test
    fun logoutDuringRefresh_doesNotRestoreCredentials() = runTest {
        val platformId = PlatformIds.WORKBUDDY
        val repo = FakePlatformSettingsRepository(
            sessionSettings = mapOf(platformId to configuredSession()),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = SessionQuotaProvider(
            platformId,
            object : SessionQuotaClient {
                override fun isConfigured(settings: SessionPlatformSettings): Boolean =
                    settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()

                override suspend fun fetch(
                    settings: SessionPlatformSettings,
                    save: suspend (SessionPlatformSettings) -> Unit,
                ): QuotaSnapshot {
                    started.complete(Unit)
                    release.await()
                    save(settings.copy(accessToken = "access-rotated"))
                    return creditSnapshot(platformId)
                }
            },
        )

        val job = async { provider.fetch(repo) }
        started.await()
        repo.clearSessionSettings(platformId)
        release.complete(Unit)
        job.await()

        val stored = repo.getSessionSettings(platformId)
        assertFalse(stored.isConfiguredFor(platformId))
        assertEquals("", stored.accessToken)
        assertEquals("", stored.refreshToken)
    }

    @Test
    fun reLoginDuringRefresh_keepsNewCredentials() = runTest {
        val platformId = PlatformIds.WORKBUDDY
        val repo = FakePlatformSettingsRepository(
            sessionSettings = mapOf(platformId to configuredSession()),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = SessionQuotaProvider(
            platformId,
            object : SessionQuotaClient {
                override fun isConfigured(settings: SessionPlatformSettings): Boolean =
                    settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()

                override suspend fun fetch(
                    settings: SessionPlatformSettings,
                    save: suspend (SessionPlatformSettings) -> Unit,
                ): QuotaSnapshot {
                    started.complete(Unit)
                    release.await()
                    save(
                        settings.copy(
                            accessToken = "access-rotated",
                            refreshToken = "refresh-rotated",
                            plan = "Free",
                        ),
                    )
                    return creditSnapshot(platformId)
                }
            },
        )

        val job = async { provider.fetch(repo) }
        started.await()
        repo.saveSessionSettings(
            platformId,
            SessionPlatformSettings(
                accessToken = "access-relogin",
                refreshToken = "refresh-relogin",
                expiresAtEpochMs = 5L,
                email = "new@example.com",
            ),
        )
        release.complete(Unit)
        job.await()

        val stored = repo.getSessionSettings(platformId)
        assertEquals("access-relogin", stored.accessToken)
        assertEquals("refresh-relogin", stored.refreshToken)
        assertEquals("new@example.com", stored.email)
        assertEquals("", stored.plan)
    }

    @Test
    fun sequentialSavesWithinOneFetch_allPersist() = runTest {
        // Refresh rotation and the plan update that follows it must both land: the guard tracks
        // the credentials this fetch rotated into, not only the ones it started with.
        val platformId = PlatformIds.WORKBUDDY
        val repo = FakePlatformSettingsRepository(
            sessionSettings = mapOf(platformId to configuredSession()),
        )
        val provider = SessionQuotaProvider(
            platformId,
            object : SessionQuotaClient {
                override fun isConfigured(settings: SessionPlatformSettings): Boolean =
                    settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()

                override suspend fun fetch(
                    settings: SessionPlatformSettings,
                    save: suspend (SessionPlatformSettings) -> Unit,
                ): QuotaSnapshot {
                    val rotated = settings.copy(
                        accessToken = "access-new",
                        refreshToken = "refresh-new",
                    )
                    save(rotated)
                    save(rotated.copy(plan = "Pro"))
                    return creditSnapshot(platformId)
                }
            },
        )

        provider.fetch(repo)
        runCurrent()

        val stored = repo.getSessionSettings(platformId)
        assertEquals("access-new", stored.accessToken)
        assertEquals("refresh-new", stored.refreshToken)
        assertEquals("Pro", stored.plan)
    }
}

private fun configuredSession(
    usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
    usageProgressStyle: UsageProgressStyle = UsageProgressStyle.BAR,
    usageResetStyle: UsageResetStyle = UsageResetStyle.HIDDEN,
    widgetWindowKind: UsageWindowKind = UsageWindowKind.MONTHLY,
): SessionPlatformSettings = SessionPlatformSettings(
    accessToken = "access-old",
    refreshToken = "refresh-old",
    expiresAtEpochMs = 1L,
    plan = "Free",
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
    widgetWindowKind = widgetWindowKind,
)

private fun creditSnapshot(platformId: String): QuotaSnapshot = QuotaSnapshot(
    platformId = platformId,
    platformName = "WorkBuddy",
    windows = listOf(QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 10.0)),
    primaryDisplay = "10%",
    updatedAtEpochMs = 1L,
)
