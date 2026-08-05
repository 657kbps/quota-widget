package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.FakePlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BalanceRefreshInteractorTest {
    @Test
    fun refresh_notConfigured_whenDeepSeekKeyMissing() = runTest {
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = ""),
        )
        val interactor = BalanceRefreshInteractor(repo, listOf(FakeDeepSeekProvider()))

        val result = interactor.refresh(PlatformIds.DEEPSEEK)

        assertIs<BalanceRefreshResult.Completed>(result)
        assertEquals(WidgetDisplayState.NotConfigured, result.state)
        assertEquals(WidgetDisplayState.NotConfigured, repo.getWidgetState(PlatformIds.DEEPSEEK))
    }

    @Test
    fun refresh_success_writesSnapshot() = runTest {
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test", currency = CurrencyPreference.CNY),
        )
        val snapshot = deepSeekSnapshot("12.50")
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(FakeDeepSeekProvider(snapshot = snapshot)),
        )

        val result = interactor.refresh(PlatformIds.DEEPSEEK)

        assertIs<BalanceRefreshResult.Completed>(result)
        val success = assertIs<WidgetDisplayState.Success>(result.state)
        assertEquals("￥12.50", success.snapshot.primaryDisplay)
        assertEquals(QuotaWindowKind.BALANCE, success.snapshot.windows.single().kind)
    }

    @Test
    fun refresh_transientFailure_keepsPreviousSuccess() = runTest {
        val previous = deepSeekSnapshot("9.99")
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test"),
            widgetStates = mapOf(PlatformIds.DEEPSEEK to WidgetDisplayState.Success(previous)),
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(FakeDeepSeekProvider(error = IllegalStateException("network down"))),
        )

        val result = interactor.refresh(PlatformIds.DEEPSEEK)

        val failure = assertIs<BalanceRefreshResult.TransientFailure>(result)
        assertEquals(true, failure.retryable)
        val retained = assertIs<WidgetDisplayState.Success>(failure.retained)
        assertEquals("￥9.99", retained.snapshot.primaryDisplay)
        assertEquals(
            WidgetDisplayState.Success(previous),
            repo.getWidgetState(PlatformIds.DEEPSEEK),
        )
    }

    @Test
    fun refresh_failureWithoutPriorSuccess_writesError() = runTest {
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test"),
            widgetStates = mapOf(PlatformIds.DEEPSEEK to WidgetDisplayState.Loading),
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(FakeDeepSeekProvider(error = IllegalStateException("boom"))),
        )

        val result = interactor.refresh(PlatformIds.DEEPSEEK)

        assertIs<BalanceRefreshResult.TransientFailure>(result)
        val error = assertIs<WidgetDisplayState.Error>(result.retained)
        assertEquals("boom", error.message)
        assertIs<WidgetDisplayState.Error>(repo.getWidgetState(PlatformIds.DEEPSEEK))
    }

    @Test
    fun refresh_cancellationPropagates_withoutChangingState() = runTest {
        val previous = deepSeekSnapshot("9.99")
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test"),
            widgetStates = mapOf(PlatformIds.DEEPSEEK to WidgetDisplayState.Success(previous)),
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(FakeDeepSeekProvider(error = CancellationException("stopped"))),
        )

        assertFailsWith<CancellationException> {
            interactor.refresh(PlatformIds.DEEPSEEK)
        }
        assertEquals(
            WidgetDisplayState.Success(previous),
            repo.getWidgetState(PlatformIds.DEEPSEEK),
        )
    }

    @Test
    fun refresh_routesToOpenCodeProvider() = runTest {
        val repo = FakePlatformSettingsRepository(
            openCode = OpenCodeGoSettings(workspaceId = "wrk_abc", authCookie = "cookie"),
        )
        val openCodeSnapshot = QuotaSnapshot(
            platformId = PlatformIds.OPENCODE_GO,
            platformName = "OpenCode Go",
            windows = listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 10.0),
            ),
            primaryDisplay = "5h 10%",
            updatedAtEpochMs = 1L,
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(
                FakeDeepSeekProvider(),
                FakeOpenCodeProvider(snapshot = openCodeSnapshot),
            ),
        )

        val result = interactor.refresh(PlatformIds.OPENCODE_GO)

        assertIs<BalanceRefreshResult.Completed>(result)
        val success = assertIs<WidgetDisplayState.Success>(result.state)
        assertEquals(PlatformIds.OPENCODE_GO, success.snapshot.platformId)
        assertEquals("5h 10%", success.snapshot.primaryDisplay)
    }

    @Test
    fun refresh_sessionExpired_writesNeedsReauth_notTransient() = runTest {
        val previous = QuotaSnapshot(
            platformId = PlatformIds.OPENCODE_GO,
            platformName = "OpenCode Go",
            windows = listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 10.0),
            ),
            primaryDisplay = "5h 10%",
            updatedAtEpochMs = 1L,
        )
        val repo = FakePlatformSettingsRepository(
            openCode = OpenCodeGoSettings(workspaceId = "wrk_abc", authCookie = "cookie"),
            widgetStates = mapOf(PlatformIds.OPENCODE_GO to WidgetDisplayState.Success(previous)),
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(FakeOpenCodeProvider(error = SessionExpiredException())),
        )

        val result = interactor.refresh(PlatformIds.OPENCODE_GO)

        assertIs<BalanceRefreshResult.Completed>(result)
        assertEquals(WidgetDisplayState.NeedsReauth, result.state)
        assertEquals(WidgetDisplayState.NeedsReauth, repo.getWidgetState(PlatformIds.OPENCODE_GO))
    }

    @Test
    fun refreshAllConfigured_refreshesBoth_withoutCrossContamination() = runTest {
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test"),
            openCode = OpenCodeGoSettings(workspaceId = "wrk_abc", authCookie = "cookie"),
        )
        val interactor = BalanceRefreshInteractor(
            repo,
            listOf(
                FakeDeepSeekProvider(snapshot = deepSeekSnapshot("1.00")),
                FakeOpenCodeProvider(
                    snapshot = QuotaSnapshot(
                        platformId = PlatformIds.OPENCODE_GO,
                        platformName = "OpenCode Go",
                        windows = listOf(
                            QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 20.0),
                        ),
                        primaryDisplay = "5h 20%",
                        updatedAtEpochMs = 1L,
                    ),
                ),
            ),
        )

        val results = interactor.refreshAllConfigured()
        assertEquals(2, results.size)
        assertIs<WidgetDisplayState.Success>(repo.getWidgetState(PlatformIds.DEEPSEEK))
        assertIs<WidgetDisplayState.Success>(repo.getWidgetState(PlatformIds.OPENCODE_GO))
        assertTrue(
            (repo.getWidgetState(PlatformIds.DEEPSEEK) as WidgetDisplayState.Success)
                .snapshot.platformId == PlatformIds.DEEPSEEK,
        )
    }

}

private fun deepSeekSnapshot(amount: String): QuotaSnapshot {
    val currency = CurrencyPreference.CNY
    return QuotaSnapshot(
        platformId = PlatformIds.DEEPSEEK,
        platformName = "DeepSeek",
        windows = listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE)),
        primaryDisplay = formatBalance(currency, amount),
        updatedAtEpochMs = 1L,
        currency = currency,
        totalBalance = amount,
    )
}

private class FakeDeepSeekProvider(
    private val snapshot: QuotaSnapshot = deepSeekSnapshot("0"),
    private val error: Exception? = null,
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.DEEPSEEK)!!

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getDeepSeekSettings().apiKey.isNotBlank()

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        error?.let { throw it }
        return snapshot
    }
}

private class FakeOpenCodeProvider(
    private val snapshot: QuotaSnapshot? = null,
    private val error: Exception? = null,
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.OPENCODE_GO)!!

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getOpenCodeGoSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        error?.let { throw it }
        return snapshot ?: error("missing snapshot")
    }
}
